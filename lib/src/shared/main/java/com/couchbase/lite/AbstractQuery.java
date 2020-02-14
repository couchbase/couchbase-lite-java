//
// AbstractQuery.java
//
// Copyright (c) 2017 Couchbase, Inc All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package com.couchbase.lite;

import android.support.annotation.GuardedBy;
import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.json.JSONException;

import com.couchbase.lite.internal.CBLStatus;
import com.couchbase.lite.internal.core.C4Query;
import com.couchbase.lite.internal.core.C4QueryEnumerator;
import com.couchbase.lite.internal.core.C4QueryOptions;
import com.couchbase.lite.internal.fleece.AllocSlice;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.JsonUtils;
import com.couchbase.lite.internal.utils.Preconditions;


abstract class AbstractQuery implements Query {
    //---------------------------------------------
    // constants
    //---------------------------------------------
    private static final LogDomain DOMAIN = LogDomain.QUERY;

    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private final Object lock = new Object();

    @GuardedBy("lock")
    private C4Query c4query;

    @GuardedBy("lock")
    private LiveQuery liveQuery;

    private Database database;

    // NOTE:
    // https://sqlite.org/lang_select.html

    // SELECT
    private Select select;
    // FROM
    private DataSource from; // FROM table-or-subquery
    private Joins joins;     // FROM join-clause
    // WHERE
    private Expression where; // WHERE expr
    // GROUP BY
    private GroupBy groupBy; // GROUP BY expr(s)
    private Having having; // Having expr
    // ORDER BY
    private OrderBy orderBy; // ORDER BY ordering-term(s)
    // LIMIT
    private Limit limit; // LIMIT expr

    // PARAMETERS
    private Parameters parameters;

    // column names
    private Map<String, Integer> columnNames;

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * Returns a copies of the current parameters.
     */
    @Override
    public Parameters getParameters() { return parameters; }

    /**
     * Set parameters should copy the given parameters. Set a new parameter will
     * also re-execute the query if there is at least one listener listening for
     * changes.
     */
    @Override
    public void setParameters(Parameters parameters) {
        final LiveQuery newQuery;
        synchronized (lock) {
            this.parameters = (parameters == null) ? null : parameters.readonlyCopy();
            newQuery = liveQuery;
        }

        // https://github.com/couchbase/couchbase-lite-android/issues/1727
        // Shouldn't call start() method inside the lock to prevent deadlock:
        if (newQuery != null) { newQuery.start(true); }
    }

    /**
     * Executes the query. The returning a result set that enumerates result rows one at a time.
     * You can run the query any number of times, and you can even have multiple ResultSet active at
     * once.
     * <p>
     * The results come from a snapshot of the database taken at the moment the run() method
     * is called, so they will not reflect any changes made to the database afterwards.
     * </p>
     *
     * @return the ResultSet for the query result.
     * @throws CouchbaseLiteException if there is an error when running the query.
     */
    @NonNull
    @Override
    public ResultSet execute() throws CouchbaseLiteException {
        AllocSlice params = null;
        try {
            final C4QueryOptions options = new C4QueryOptions();
            if (parameters == null) { parameters = new Parameters(); }
            params = parameters.encode();
            final C4QueryEnumerator c4enum;
            synchronized (getDatabase().getLock()) {
                synchronized (lock) {
                    if (c4query == null) { c4query = prepQueryLocked(); }
                    c4enum = c4query.run(options, params);
                }
            }
            return new ResultSet(this, c4enum, columnNames);
        }
        catch (LiteCoreException e) {
            throw CBLStatus.convertException(e);
        }
        finally {
            if (params != null) { params.free(); }
        }
    }

    /**
     * Returns a string describing the implementation of the compiled query.
     * This is intended to be read by a developer for purposes of optimizing the query, especially
     * to add database indexes. It's not machine-readable and its format may change.
     * As currently implemented, the result is two or more lines separated by newline characters:
     * * The first line is the SQLite SELECT statement.
     * * The subsequent lines are the output of SQLite's "EXPLAIN QUERY PLAN" command applied to that
     * statement; for help interpreting this, see https://www.sqlite.org/eqp.html . The most
     * important thing to know is that if you see "SCAN TABLE", it means that SQLite is doing a
     * slow linear scan of the documents instead of using an index.
     *
     * @return a string describing the implementation of the compiled query.
     * @throws CouchbaseLiteException if an error occurs
     */
    @NonNull
    @Override
    public String explain() throws CouchbaseLiteException {
        synchronized (getDatabase().getLock()) {
            synchronized (lock) {
                if (c4query == null) { c4query = prepQueryLocked(); }
                return c4query.explain();
            }
        }
    }

    /**
     * Adds a query change listener. Changes will be posted on the main queue.
     *
     * @param listener The listener to post changes.
     * @return An opaque listener token object for removing the listener.
     */
    @NonNull
    @Override
    public ListenerToken addChangeListener(@NonNull QueryChangeListener listener) {
        Preconditions.assertNotNull(listener, "listener");
        return addChangeListener(null, listener);
    }

    /**
     * Adds a query change listener with the dispatch queue on which changes
     * will be posted. If the dispatch queue is not specified, the changes will be
     * posted on the main queue.
     *
     * @param executor The executor object that calls listener. If null, use default executor.
     * @param listener The listener to post changes.
     * @return An opaque listener token object for removing the listener.
     */
    @NonNull
    @Override
    public ListenerToken addChangeListener(Executor executor, @NonNull QueryChangeListener listener) {
        Preconditions.assertNotNull(listener, "listener");
        return getLiveQuery().addChangeListener(executor, listener);
    }

    /**
     * Removes a change listener wih the given listener token.
     *
     * @param token The listener token.
     */
    @Override
    public void removeChangeListener(@NonNull ListenerToken token) {
        Preconditions.assertNotNull(token, "token");
        getLiveQuery().removeChangeListener(token);
    }

    @NonNull
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[json=" + asJson() + "]";
    }

    //---------------------------------------------
    // Protected level access
    //---------------------------------------------

    @SuppressWarnings("NoFinalizer")
    @SuppressFBWarnings("RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE")
    @Override
    protected void finalize() throws Throwable {
        free(c4query);
        super.finalize();
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    Database getDatabase() {
        if (database == null) { database = (Database) from.getSource(); }
        return database;
    }

    void setSelect(Select select) { this.select = select; }

    void setFrom(DataSource from) { this.from = from; }

    void setJoins(Joins joins) { this.joins = joins; }

    void setWhere(Expression where) { this.where = where; }

    void setGroupBy(GroupBy groupBy) { this.groupBy = groupBy; }

    void setHaving(Having having) { this.having = having; }

    void setOrderBy(OrderBy orderBy) { this.orderBy = orderBy; }

    void setLimit(Limit limit) { this.limit = limit; }

    void copy(AbstractQuery query) {
        this.select = query.select;
        this.from = query.from;
        this.joins = query.joins;
        this.where = query.where;
        this.groupBy = query.groupBy;
        this.having = query.having;
        this.orderBy = query.orderBy;
        this.limit = query.limit;
        this.parameters = query.parameters;
    }

    //---------------------------------------------
    // Private methods
    //---------------------------------------------

    @GuardedBy("lock")
    private C4Query prepQueryLocked() throws CouchbaseLiteException {
        database = (Database) from.getSource();

        final String json = encodeAsJson();
        Log.v(DOMAIN, "Encoded query: %s", json);
        if (json == null) { throw new CouchbaseLiteException("Failed to generate JSON query."); }

        if (columnNames == null) { columnNames = getColumnNames(); }

        try { return database.getC4Database().createQuery(json); }
        catch (LiteCoreException e) { throw CBLStatus.convertException(e); }
    }

    private Map<String, Integer> getColumnNames() throws CouchbaseLiteException {
        final Map<String, Integer> map = new HashMap<>();
        int index = 0;
        int provisionKeyIndex = 0;
        for (SelectResult selectResult : select.getSelectResults()) {
            String name = selectResult.getColumnName();

            if (name != null && name.equals(PropertyExpression.PROPS_ALL)) { name = from.getColumnName(); }

            if (name == null) { name = "$" + (++provisionKeyIndex); }

            if (map.containsKey(name)) {
                throw new CouchbaseLiteException(
                    Log.formatStandardMessage("DuplicateSelectResultName", name),
                    CBLError.Domain.CBLITE,
                    CBLError.Code.INVALID_QUERY);
            }
            map.put(name, index);
            index++;
        }
        return map;
    }

    private String encodeAsJson() {
        try { return JsonUtils.toJson(asJson()).toString(); }
        catch (JSONException e) {
            Log.w(DOMAIN, "Error when encoding the query as a json string", e);
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "PMD.NPathComplexity"})
    private Map<String, Object> asJson() {
        final Map<String, Object> json = new HashMap<>();

        // DISTINCT:
        if (select != null && select.isDistinct()) { json.put("DISTINCT", true); }

        // result-columns / SELECT-RESULTS
        if (select != null && select.hasSelectResults()) { json.put("WHAT", select.asJSON()); }

        // JOIN:
        final List<Object> f = new ArrayList<>();
        final Map<String, Object> as = from.asJSON();
        if (!as.isEmpty()) { f.add(as); }

        if (joins != null) { f.addAll((List<Object>) joins.asJSON()); }

        if (!f.isEmpty()) { json.put("FROM", f); }

        if (where != null) { json.put("WHERE", where.asJSON()); }

        if (groupBy != null) { json.put("GROUP_BY", groupBy.asJSON()); }

        if (having != null) {
            final Object havingJson = having.asJSON();
            if (havingJson != null) { json.put("HAVING", havingJson); }
        }

        if (orderBy != null) { json.put("ORDER_BY", orderBy.asJSON()); }

        if (limit != null) {
            final List<Object> list = (List<Object>) limit.asJSON();
            json.put("LIMIT", list.get(0));
            if (list.size() > 1) { json.put("OFFSET", list.get(1)); }
        }

        return json;
    }

    private LiveQuery getLiveQuery() {
        synchronized (lock) {
            if (liveQuery == null) { liveQuery = new LiveQuery(this); }
            return liveQuery;
        }
    }

    // called from finalizer
    private void free(C4Query query) {
        if (query == null) { return; }

        final Object lock = getDbLock();
        if (lock != null) {
            synchronized (lock) { query.free(); }
            return;
        }

        Log.w(LogDomain.DATABASE, "Could not get DB lock to free query");
        query.free();
    }

    private Object getDbLock() {
        Database db = database;

        if (db == null) {
            final DataSource src = from;
            if (src != null) { db = (Database) src.getSource(); }
        }

        return (db == null) ? null : db.getLock();
    }
}
