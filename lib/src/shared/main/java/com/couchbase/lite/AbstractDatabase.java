//
// AbstractDatabase.java
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
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.json.JSONException;

import com.couchbase.lite.internal.CBLInternalException;
import com.couchbase.lite.internal.CBLStatus;
import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.ExecutionService;
import com.couchbase.lite.internal.SocketFactory;
import com.couchbase.lite.internal.core.C4BlobStore;
import com.couchbase.lite.internal.core.C4Constants;
import com.couchbase.lite.internal.core.C4Database;
import com.couchbase.lite.internal.core.C4DatabaseChange;
import com.couchbase.lite.internal.core.C4DatabaseObserver;
import com.couchbase.lite.internal.core.C4Document;
import com.couchbase.lite.internal.core.C4ReplicationFilter;
import com.couchbase.lite.internal.core.C4Replicator;
import com.couchbase.lite.internal.core.C4ReplicatorListener;
import com.couchbase.lite.internal.core.SharedKeys;
import com.couchbase.lite.internal.fleece.FLEncoder;
import com.couchbase.lite.internal.fleece.FLSliceResult;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.JsonUtils;
import com.couchbase.lite.internal.utils.Preconditions;
import com.couchbase.lite.utils.FileUtils;
import com.couchbase.lite.utils.Fn;


/**
 * AbstractDatabase is a base class of A Couchbase Lite Database.
 */
@SuppressWarnings({"PMD.GodClass", "PMD.CyclomaticComplexity", "PMD.TooManyMethods"})
abstract class AbstractDatabase {

    /**
     * Gets the logging controller for the Couchbase Lite library to configure the
     * logging settings and add custom logging.
     * <p>
     */
    // Public API.  Do not fix the name.
    @SuppressWarnings({"ConstantName", "PMD.FieldNamingConventions"})
    @NonNull
    public static final com.couchbase.lite.Log log = new com.couchbase.lite.Log();

    //---------------------------------------------
    // Constants
    //---------------------------------------------
    private static final String ERROR_RESOLVER_FAILED = "Conflict resolution failed for document '%s': %s";
    private static final String WARN_WRONG_DATABASE = "The database to which the document produced by"
        + " conflict resolution for document '%s' belongs, '%s', is not the one in which it will be stored (%s)";
    private static final String WARN_WRONG_ID = "The ID of the document produced by conflict resolution"
        + " for document (%s) does not match the IDs of the conflicting documents (%s)";

    private static final LogDomain DOMAIN = LogDomain.DATABASE;

    @VisibleForTesting
    static final String DB_EXTENSION = ".cblite2";

    private static final int MAX_CHANGES = 100;

    private static final int SHUTDOWN_DELAY_SECS = 30;

    // A random but absurdly large number.
    private static final int MAX_CONFLICT_RESOLUTION_RETRIES = 13;

    // How long to wait after a database opens before expiring docs
    private static final long OPENING_PURGE_DELAY_MS = 3;
    private static final long STANDARD_PURGE_INTERVAL_MS = 1000;

    private static final int DEFAULT_DATABASE_FLAGS
        = C4Constants.DatabaseFlags.CREATE
        | C4Constants.DatabaseFlags.AUTO_COMPACT
        | C4Constants.DatabaseFlags.SHARED_KEYS;

    // ---------------------------------------------
    // API - public static methods
    // ---------------------------------------------

    /**
     * Deletes a database of the given name in the given directory.
     *
     * @param name      the database's name
     * @param directory the directory containing the database: the database's parent directory.
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the operation.
     */
    public static void delete(@NonNull String name, @Nullable File directory) throws CouchbaseLiteException {
        Preconditions.assertNotNull(name, "name");

        if (directory == null) { directory = new File(AbstractDatabaseConfiguration.getDbDirectory(null)); }

        if (!exists(name, directory)) {
            throw new CouchbaseLiteException(
                "Database not found for delete",
                CBLError.Domain.CBLITE,
                CBLError.Code.NOT_FOUND);
        }

        final File path = getDatabaseFile(directory, name);
        try {
            Log.i(DOMAIN, "delete(): path=%s", path.toString());
            C4Database.deleteDbAtPath(path.getPath());
        }
        catch (LiteCoreException e) {
            throw CBLStatus.convertException(e);
        }
    }

    /**
     * Checks whether a database of the given name exists in the given directory or not.
     *
     * @param name      the database's name
     * @param directory the path where the database is located.
     * @return true if exists, false otherwise.
     */
    public static boolean exists(@NonNull String name, @NonNull File directory) {
        Preconditions.assertNotNull(name, "name");
        Preconditions.assertNotNull(directory, "directory");
        return getDatabaseFile(directory, name).exists();
    }

    protected static void copy(
        @NonNull File path,
        @NonNull String name,
        @NonNull DatabaseConfiguration config,
        int algorithm,
        byte[] encryptionKey)
        throws CouchbaseLiteException {

        String fromPath = path.getPath();
        if (fromPath.charAt(fromPath.length() - 1) != File.separatorChar) { fromPath += File.separator; }
        String toPath = getDatabaseFile(new File(config.getDirectory()), name).getPath();
        if (toPath.charAt(toPath.length() - 1) != File.separatorChar) { toPath += File.separator; }

        // Setting the temp directory from the Database Configuration is deprecated and will go away:

        CouchbaseLiteInternal.setupDirectories(config.getRootDirectory());

        try {
            C4Database.copyDb(
                fromPath,
                toPath,
                DEFAULT_DATABASE_FLAGS,
                null,
                C4Constants.DocumentVersioning.REVISION_TREES,
                algorithm,
                encryptionKey);
        }
        catch (LiteCoreException e) {
            FileUtils.eraseFileOrDir(toPath);
            throw CBLStatus.convertException(e);
        }
    }

    /**
     * Set log level for the given log domain.
     *
     * @param domain The log domain
     * @param level  The log level
     * @deprecated As of 2.5 because it is being replaced with the
     * {@link com.couchbase.lite.Log#getConsole() getConsole} method from the {@link #log log} property.
     * This method will eventually be replaced with a no-op to preserve API compatibility.
     * Until it is, its interactions with other logging maybe be fairly unpredictable.
     */
    @Deprecated
    public static void setLogLevel(@NonNull LogDomain domain, @NonNull LogLevel level) {
        Preconditions.assertNotNull(domain, "domain");
        Preconditions.assertNotNull(level, "level");

        //noinspection deprecation
        final EnumSet<LogDomain> domains = (domain == LogDomain.ALL)
            ? LogDomain.ALL_DOMAINS
            : EnumSet.of(domain);

        final ConsoleLogger logger = log.getConsole();
        logger.setDomains(domains);
        logger.setLevel(level);
    }

    private static File getDatabaseFile(File dir, String name) {
        return new File(dir, name.replaceAll("/", ":") + DB_EXTENSION);
    }

    //---------------------------------------------
    // Member variables
    //---------------------------------------------

    protected C4Database c4db;

    @NonNull
    final DatabaseConfiguration config;

    // Main database lock object for thread-safety
    @NonNull
    private final Object lock = new Object();

    private final String name;

    @GuardedBy("lock")
    private final Set<LiveQuery> activeLiveQueries;
    private final Set<Replicator> activeReplications;
    private final Map<String, DocumentChangeNotifier> docChangeNotifiers;

    // Executor for purge and posting Database/Document changes.
    private final ExecutionService.CloseableExecutor postExecutor;
    // Executor for LiveQuery.
    private final ExecutionService.CloseableExecutor queryExecutor;

    private final boolean shellMode;
    private final SharedKeys sharedKeys;

    private final DocumentExpirationStrategy purgeStrategy;

    private ChangeNotifier<DatabaseChange> dbChangeNotifier;

    private C4DatabaseObserver c4DbObserver;

    private String path;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    /**
     * Construct a  AbstractDatabase with a given name and database config.
     * If the database does not yet exist, it will be created, unless the `readOnly` option is used.
     *
     * @param name   The name of the database. May NOT contain capital letters!
     * @param config The database config, Note: null config parameter is not allowed with Android platform
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the open operation.
     */
    protected AbstractDatabase(@NonNull String name, @NonNull DatabaseConfiguration config)
        throws CouchbaseLiteException {
        Preconditions.assertNotEmpty(name, "db name");
        Preconditions.assertNotNull(config, "config");

        CouchbaseLiteInternal.requireInit("Cannot create database");

        // Name:
        this.name = name;

        // Copy configuration
        this.config = config.readOnlyCopy();

        this.shellMode = false;

        this.postExecutor = CouchbaseLiteInternal.getExecutionService().getSerialExecutor();
        this.queryExecutor = CouchbaseLiteInternal.getExecutionService().getSerialExecutor();

        // synchronized on 'lock'
        this.activeReplications = new HashSet<>();
        this.activeLiveQueries = Collections.synchronizedSet(new HashSet<>());
        this.docChangeNotifiers = new HashMap<>();

        // !!! Remove this code.
        // Setting the temp directory from the Database Configuration is a bad idea and should be deprecated.
        // Directories should be set in the CouchbaseLite.init method
        CouchbaseLiteInternal.setupDirectories(config.getRootDirectory());

        // Can't open the DB until the file system is set up.
        this.c4db = openC4Db();

        // Initialize a shared keys:
        this.sharedKeys = new SharedKeys(c4db);

        this.purgeStrategy = new DocumentExpirationStrategy(this, STANDARD_PURGE_INTERVAL_MS, postExecutor);
        this.purgeStrategy.schedulePurge(OPENING_PURGE_DELAY_MS);

        // warn if logging has not been turned on
        Log.warn();
    }

    /**
     * Initialize Database with a give C4Database object in the shell mode. The life of the
     * C4Database object will be managed by the caller. This is currently used for creating a
     * Dictionary as an input of the predict() method of the PredictiveModel.
     */
    protected AbstractDatabase(@NonNull C4Database c4db) {
        Preconditions.assertNotNull(c4db, "c4db");

        CouchbaseLiteInternal.requireInit("Cannot create database");

        this.c4db = c4db;

        this.name = null;

        this.config = new DatabaseConfiguration();
        this.shellMode = true;

        this.postExecutor = null;
        this.queryExecutor = null;

        this.activeReplications = null;
        this.activeLiveQueries = null;
        this.docChangeNotifiers = null;

        this.sharedKeys = null;

        this.purgeStrategy = null;
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    // GET EXISTING DOCUMENT

    /**
     * Return the database name
     *
     * @return the database's name
     */
    @NonNull
    public String getName() { return this.name; }

    /**
     * Return the database's path. If the database is closed or deleted, null value will be returned.
     *
     * @return the database's path.
     */
    public String getPath() {
        synchronized (lock) { return c4db == null ? null : getC4Database().getPath(); }
    }

    /**
     * The number of documents in the database.
     *
     * @return the number of documents in the database, 0 if database is closed.
     */
    public long getCount() {
        synchronized (lock) { return c4db == null ? 0L : getC4Database().getDocumentCount(); }
    }

    /**
     * Returns a READONLY config object which will throw a runtime exception
     * when any setter methods are called.
     *
     * @return the READONLY copied config object
     */
    @NonNull
    public DatabaseConfiguration getConfig() { return config.readOnlyCopy(); }

    /**
     * Gets an existing Document object with the given ID. If the document with the given ID doesn't
     * exist in the database, the value returned will be null.
     *
     * @param id the document ID
     * @return the Document object
     */
    public Document getDocument(@NonNull String id) {
        Preconditions.assertNotNull(id, "id");

        synchronized (lock) {
            mustBeOpen();
            try { return new Document((Database) this, id, false); }
            catch (CouchbaseLiteException ex) {
                // only 404 - Not Found error throws CouchbaseLiteException
                return null;
            }
        }
    }

    /**
     * Saves a document to the database. When write operations are executed
     * concurrently, the last writer will overwrite all other written values.
     * Calling this method is the same as calling the ave(MutableDocument, ConcurrencyControl)
     * method with LAST_WRITE_WINS concurrency control.
     *
     * @param document The document.
     * @throws CouchbaseLiteException on error
     */
    public void save(@NonNull MutableDocument document) throws CouchbaseLiteException {
        save(document, ConcurrencyControl.LAST_WRITE_WINS);
    }

    /**
     * Saves a document to the database. When used with LAST_WRITE_WINS
     * concurrency control, the last write operation will win if there is a conflict.
     * When used with FAIL_ON_CONFLICT concurrency control, save will fail with false value
     *
     * @param document           The document.
     * @param concurrencyControl The concurrency control.
     * @return true if successful. false if the FAIL_ON_CONFLICT concurrency
     * @throws CouchbaseLiteException on error
     */
    public boolean save(@NonNull MutableDocument document, @NonNull ConcurrencyControl concurrencyControl)
        throws CouchbaseLiteException {
        try {
            saveInternal(document, null, false, concurrencyControl);
            return true;
        }
        catch (CouchbaseLiteException e) {
            if (!CouchbaseLiteException.isConflict(e)) { throw e; }
        }
        return false;
    }

    /**
     * Saves a document to the database. Conflicts will be resolved by the passed ConflictHandler
     *
     * @param document        The document.
     * @param conflictHandler A conflict handler.
     * @return true if successful. false if the FAIL_ON_CONFLICT concurrency
     * @throws CouchbaseLiteException on error
     */
    public boolean save(@NonNull MutableDocument document, @NonNull ConflictHandler conflictHandler)
        throws CouchbaseLiteException {
        Preconditions.assertNotNull(document, "document");
        Preconditions.assertNotNull(conflictHandler, "conflictHandler");
        saveWithConflictHandler(document, conflictHandler);
        return true;
    }

    /**
     * Deletes a document from the database. When write operations are executed
     * concurrently, the last writer will overwrite all other written values.
     * Calling this function is the same as calling the delete(Document, ConcurrencyControl)
     * function with LAST_WRITE_WINS concurrency control.
     *
     * @param document The document.
     * @throws CouchbaseLiteException on error
     */
    public void delete(@NonNull Document document) throws CouchbaseLiteException {
        delete(document, ConcurrencyControl.LAST_WRITE_WINS);
    }

    /**
     * Deletes a document from the database. When used with lastWriteWins concurrency
     * control, the last write operation will win if there is a conflict.
     * When used with FAIL_ON_CONFLICT concurrency control, delete will fail with
     * 'false' value returned.
     *
     * @param document           The document.
     * @param concurrencyControl The concurrency control.
     * @throws CouchbaseLiteException on error
     */
    public boolean delete(@NonNull Document document, @NonNull ConcurrencyControl concurrencyControl)
        throws CouchbaseLiteException {
        // NOTE: synchronized in save(Document, boolean, ConcurrencyControl, ConflictHandler) method
        try {
            saveInternal(document, null, true, concurrencyControl);
            return true;
        }
        catch (CouchbaseLiteException e) {
            if (!CouchbaseLiteException.isConflict(e)) { throw e; }
        }
        return false;
    }

    // Batch operations:

    /**
     * Purges the given document from the database. This is more drastic than delete(Document),
     * it removes all traces of the document. The purge will NOT be replicated to other databases.
     *
     * @param document the document to be purged.
     */
    public void purge(@NonNull Document document) throws CouchbaseLiteException {
        Preconditions.assertNotNull(document, "document");

        if (document.isNewDocument()) {
            throw new CouchbaseLiteException("DocumentNotFound", CBLError.Domain.CBLITE, CBLError.Code.NOT_FOUND);
        }

        synchronized (lock) {
            prepareDocument(document);

            try { purge(document.getId()); }
            catch (CouchbaseLiteException e) {
                // Ignore not found (already deleted)
                if (e.getCode() != CBLError.Code.NOT_FOUND) { throw e; }
            }

            document.replaceC4Document(null); // Reset c4doc:
        }
    }

    /**
     * Purges the given document id for the document in database. This is more drastic than delete(Document),
     * it removes all traces of the document. The purge will NOT be replicated to other databases.
     *
     * @param id the document ID
     */
    public void purge(@NonNull String id) throws CouchbaseLiteException {
        Preconditions.assertNotNull(id, "id");
        synchronized (lock) { purgeSynchronized(id); }
    }

    // Database changes:

    /**
     * Sets an expiration date on a document. After this time, the document
     * will be purged from the database.
     *
     * @param id         The ID of the Document
     * @param expiration Nullable expiration timestamp as a Date, set timestamp to null
     *                   to remove expiration date time from doc.
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the operation.
     */
    public void setDocumentExpiration(@NonNull String id, Date expiration) throws CouchbaseLiteException {
        Preconditions.assertNotNull(id, "id");

        if (purgeStrategy == null) {
            Log.w(LogDomain.DATABASE, "Attempt to set document expiration without a purge strategy");
            return;
        }

        synchronized (lock) {
            try {
                getC4Database().setExpiration(id, (expiration == null) ? 0 : expiration.getTime());
                purgeStrategy.schedulePurge(0);
            }
            catch (LiteCoreException e) {
                throw CBLStatus.convertException(e);
            }
        }
    }

    /**
     * Returns the expiration time of the document. null will be returned if there is
     * no expiration time set
     *
     * @param id The ID of the Document
     * @return Date a nullable expiration timestamp of the document or null if time not set.
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the operation.
     */
    public Date getDocumentExpiration(@NonNull String id) throws CouchbaseLiteException {
        Preconditions.assertNotNull(id, "id");

        synchronized (lock) {
            try {
                if (getC4Database().get(id, true) == null) {
                    throw new CouchbaseLiteException(
                        "DocumentNotFound",
                        CBLError.Domain.CBLITE,
                        CBLError.Code.NOT_FOUND);
                }
                final long timestamp = getC4Database().getExpiration(id);
                return (timestamp == 0) ? null : new Date(timestamp);
            }
            catch (LiteCoreException e) {
                throw CBLStatus.convertException(e);
            }
        }
    }

    /**
     * Runs a group of database operations in a batch. Use this when performing bulk write operations
     * like multiple inserts/updates; it saves the overhead of multiple database commits, greatly
     * improving performance.
     *
     * @param runnable the action which is implementation of Runnable interface
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the operation.
     */
    public void inBatch(@NonNull Runnable runnable) throws CouchbaseLiteException {
        Preconditions.assertNotNull(runnable, "runnable");

        synchronized (lock) {
            mustBeOpen();
            try {
                final C4Database db = getC4Database();
                boolean commit = false;
                db.beginTransaction();
                try {
                    runnable.run();
                    commit = true;
                }
                finally {
                    db.endTransaction(commit);
                }
            }
            catch (RuntimeException e) {
                throw new CouchbaseLiteException(e);
            }
            catch (LiteCoreException e) {
                throw CBLStatus.convertException(e);
            }
        }

        postDatabaseChanged();
    }

    // Compaction:

    /**
     * Compacts the database file by deleting unused attachment files and vacuuming the SQLite database
     */
    public void compact() throws CouchbaseLiteException {
        synchronized (lock) {
            mustBeOpen();
            try { getC4Database().compact(); }
            catch (LiteCoreException e) { throw CBLStatus.convertException(e); }
        }
    }

    // Document changes:

    /**
     * Adds a change listener for the changes that occur in the database. The changes will be delivered on the UI
     * thread for the Android platform and on an arbitrary thread for the Java platform. When developing a Java
     * Desktop application using Swing or JavaFX that needs to update the UI after receiving the changes, make
     * sure to schedule the UI update on the UI thread by using SwingUtilities.invokeLater(Runnable) or
     * Platform.runLater(Runnable) respectively.
     *
     * @param listener callback
     */
    @NonNull
    public ListenerToken addChangeListener(@NonNull DatabaseChangeListener listener) {
        return addChangeListener(null, listener);
    }

    // Others:

    /**
     * Adds a change listener for the changes that occur in the database with an executor on which the changes will be
     * posted to the listener. If the executor is not specified, the changes will be delivered on the UI thread for
     * the Android platform and on an arbitrary thread for the Java platform.
     *
     * @param listener callback
     */
    @NonNull
    public ListenerToken addChangeListener(@Nullable Executor executor, @NonNull DatabaseChangeListener listener) {
        Preconditions.assertNotNull(listener, "listener");

        synchronized (lock) {
            mustBeOpen();
            return addDatabaseChangeListenerLocked(executor, listener);
        }
    }

    /**
     * Removes the change listener added to the database.
     *
     * @param token returned by a previous call to addChangeListener or addDocumentListener.
     */
    public void removeChangeListener(@NonNull ListenerToken token) {
        Preconditions.assertNotNull(token, "token");

        synchronized (lock) {
            mustBeOpen();
            if (!(token instanceof ChangeListenerToken) || (((ChangeListenerToken) token).getKey() == null)) {
                removeDatabaseChangeListenerLocked(token);
            }
            else {
                removeDocumentChangeListenerLocked((ChangeListenerToken) token);
            }
        }
    }

    /**
     * Adds a change listener for the changes that occur to the specified document.
     * The changes will be delivered on the UI thread for the Android platform and on an arbitrary
     * thread for the Java platform. When developing a Java Desktop application using Swing or JavaFX
     * that needs to update the UI after receiving the changes, make sure to schedule the UI update
     * on the UI thread by using SwingUtilities.invokeLater(Runnable) or Platform.runLater(Runnable)
     * respectively.
     */
    @NonNull
    public ListenerToken addDocumentChangeListener(@NonNull String id, @NonNull DocumentChangeListener listener) {
        return addDocumentChangeListener(id, null, listener);
    }

    /**
     * Adds a change listener for the changes that occur to the specified document with an executor on which
     * the changes will be posted to the listener.  If the executor is not specified, the changes will be
     * delivered on the UI thread for the Android platform and on an arbitrary thread for the Java platform.
     */
    @NonNull
    public ListenerToken addDocumentChangeListener(
        @NonNull String id,
        @Nullable Executor executor,
        @NonNull DocumentChangeListener listener) {
        Preconditions.assertNotNull(id, "id");
        Preconditions.assertNotNull(listener, "listener");

        synchronized (lock) {
            mustBeOpen();
            return addDocumentChangeListenerLocked(id, executor, listener);
        }
    }

    /**
     * Closes a database.
     *
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the operation.
     */
    public void close() throws CouchbaseLiteException {
        synchronized (lock) {
            if (c4db == null) { return; }

            Log.i(DOMAIN, "Closing %s at path %s", this, c4db.getPath());

            verifyQuiescent();

            closeC4DB();

            shutdown();
        }
    }

    /**
     * Deletes a database.
     *
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the operation.
     */
    public void delete() throws CouchbaseLiteException {
        synchronized (lock) {
            mustBeOpen();

            Log.i(DOMAIN, "Deleting %s at path %s", this, c4db.getPath());

            verifyQuiescent();

            try { c4db.delete(); }
            catch (LiteCoreException e) { throw CBLStatus.convertException(e); }

            shutdown();
        }
    }


    @SuppressWarnings("unchecked")
    @NonNull
    public List<String> getIndexes() throws CouchbaseLiteException {
        synchronized (lock) {
            mustBeOpen();
            try { return (List<String>) c4db.getIndexes().asObject(); }
            catch (LiteCoreException e) { throw CBLStatus.convertException(e); }
        }
    }

    public void createIndex(@NonNull String name, @NonNull Index index) throws CouchbaseLiteException {
        Preconditions.assertNotNull(name, "name");
        Preconditions.assertNotNull(index, "index");

        synchronized (lock) {
            mustBeOpen();
            try {
                final AbstractIndex abstractIndex = (AbstractIndex) index;
                final String json = JsonUtils.toJson(abstractIndex.items()).toString();
                getC4Database().createIndex(
                    name,
                    json,
                    abstractIndex.type().getValue(),
                    abstractIndex.language(),
                    abstractIndex.ignoreAccents());
            }
            catch (LiteCoreException e) {
                throw CBLStatus.convertException(e);
            }
            catch (JSONException e) {
                throw new CouchbaseLiteException(e);
            }
        }
    }

    public void deleteIndex(@NonNull String name) throws CouchbaseLiteException {
        synchronized (lock) {
            mustBeOpen();
            try { c4db.deleteIndex(name); }
            catch (LiteCoreException e) { throw CBLStatus.convertException(e); }
        }
    }

    @VisibleForTesting
    public File getDbFile() { return isOpen() ? getFilePath() : new File(path); }

    //---------------------------------------------
    // Override public method
    //---------------------------------------------

    @NonNull
    @Override
    public String toString() {
        return "Database{@" + Integer.toHexString(hashCode()) + ", name='" + name + "'}";
    }

    //---------------------------------------------
    // Protected level access
    //---------------------------------------------

    protected void mustBeOpen() {
        if (c4db == null) { throw new IllegalStateException(Log.lookupStandardMessage("DBClosed")); }
    }

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        shutdownNow();
        super.finalize();
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    @NonNull
    Object getLock() { return lock; }

    boolean equalsWithPath(Database other) {
        if (other == null) { return false; }

        final File path = getFilePath();
        final File otherPath = other.getFilePath();

        if ((path == null) && (otherPath == null)) { return true; }

        return (path != null) && path.equals(otherPath);
    }

    @NonNull
    C4BlobStore getBlobStore() throws LiteCoreException {
        synchronized (lock) {
            mustBeOpen();
            return c4db.getBlobStore();
        }
    }

    // Instead of clone()
    Database copy() throws CouchbaseLiteException { return new Database(this.name, this.config); }

    //////// DATABASES:

    boolean isOpen() { return c4db != null; }

    File getFilePath() {
        final String path = getPath();
        return path != null ? new File(path) : null;
    }

    /**
     * NOTE: In general the mustBeOpen() method has already been called by the caller.
     * It locks the db to guarantee that c4db is not be closed.
     * Calling mustBeOpen here just avoids an NPE.
     *
     * @return a reference to C4Database instance if db is open.
     */
    C4Database getC4Database() {
        mustBeOpen();

        return c4db;
    }

    //////// DOCUMENTS:

    void addActiveLiveQuery(@NonNull LiveQuery query) { activeLiveQueries.add(query); }

    void removeActiveLiveQuery(@NonNull LiveQuery query) { activeLiveQueries.remove(query); }

    //////// REPLICATORS:

    @SuppressWarnings("PMD.ExcessiveParameterList")
    C4Replicator createRemoteReplicator(
        Replicator replicator,
        String scheme,
        String host,
        int port,
        String path,
        String remoteDatabaseName,
        int push,
        int pull,
        byte[] options,
        C4ReplicatorListener listener,
        C4ReplicationFilter pushFilter,
        C4ReplicationFilter pullFilter,
        SocketFactory socketFactoryContext,
        int framing)
        throws LiteCoreException {
        final C4Replicator c4Repl;
        synchronized (lock) {
            c4Repl = getC4Database().createRemoteReplicator(
                scheme,
                host,
                port,
                path,
                remoteDatabaseName,
                push,
                pull,
                options,
                listener,
                pushFilter,
                pullFilter,
                replicator,
                socketFactoryContext,
                framing);
            activeReplications.add(replicator); // keeps me from being deallocated
        }
        return c4Repl;
    }

    @SuppressWarnings("PMD.ExcessiveParameterList")
    C4Replicator createLocalReplicator(
        Replicator replicator,
        C4Database otherLocalDB,
        int push,
        int pull,
        byte[] options,
        C4ReplicatorListener listener,
        C4ReplicationFilter pushFilter,
        C4ReplicationFilter pullFilter)
        throws LiteCoreException {
        final C4Replicator c4Repl;
        synchronized (lock) {
            c4Repl = getC4Database().createLocalReplicator(
                otherLocalDB,
                push,
                pull,
                options,
                listener,
                pushFilter,
                pullFilter,
                replicator);
            activeReplications.add(replicator); // keeps me from being deallocated
        }
        return c4Repl;
    }

    void removeActiveReplicator(Replicator replicator) {
        synchronized (lock) { activeReplications.remove(replicator); }
    }

    //////// RESOLVING REPLICATED CONFLICTS:

    void resolveReplicationConflict(
        @Nullable ConflictResolver resolver,
        @NonNull String docId,
        @NonNull Fn.Consumer<CouchbaseLiteException> callback) {
        int n = 0;
        CouchbaseLiteException err = null;
        try {
            while (true) {
                if (n++ > MAX_CONFLICT_RESOLUTION_RETRIES) {
                    err = new CouchbaseLiteException(
                        "Too many attempts to resolve a conflicted document: " + n,
                        CBLError.Domain.CBLITE,
                        CBLError.Code.UNEXPECTED_ERROR);
                    break;
                }

                try {
                    resolveConflictOnce(resolver, docId);
                    callback.accept(null);
                    return;
                }
                catch (CouchbaseLiteException e) {
                    if (!CouchbaseLiteException.isConflict(e)) {
                        err = e;
                        break;
                    }
                }
                catch (CBLInternalException e) {
                    // This error occurs when a resolver that starts after this one
                    // fixes the conflict before this one does.  When this one attempts
                    // to save, it gets a conflict error and retries.  During the retry,
                    // it cannot find a conflicting revision and throws this error.
                    // The other resolver did the right thing, so there is no reason
                    // to report an error.
                    if (e.getCode() != CBLInternalException.FAILED_SELECTING_CONFLICTING_REVISION) {
                        err = new CouchbaseLiteException(e);
                    }
                    break;
                }
            }
        }
        catch (RuntimeException e) {
            final String msg = e.getMessage();
            err = new CouchbaseLiteException(
                (msg != null) ? msg : "Conflict resolution failed",
                e,
                CBLError.Domain.CBLITE,
                CBLError.Code.UNEXPECTED_ERROR);
        }

        callback.accept(err);
    }

    //////// Execution:

    void scheduleOnPostNotificationExecutor(@NonNull Runnable task, long delayMs) {
        CouchbaseLiteInternal.getExecutionService().postDelayedOnExecutor(delayMs, postExecutor, task);
    }

    void scheduleOnQueryExecutor(@NonNull Runnable task, long delayMs) {
        CouchbaseLiteInternal.getExecutionService().postDelayedOnExecutor(delayMs, queryExecutor, task);
    }

    abstract int getEncryptionAlgorithm();

    abstract byte[] getEncryptionKey();

    //---------------------------------------------
    // Private (in class only)
    //---------------------------------------------

    //////// DATABASES:
    private void beginTransaction() throws CouchbaseLiteException {
        try { getC4Database().beginTransaction(); }
        catch (LiteCoreException e) { throw CBLStatus.convertException(e); }
    }

    private void endTransaction(boolean commit) throws CouchbaseLiteException {
        try { getC4Database().endTransaction(commit); }
        catch (LiteCoreException e) { throw CBLStatus.convertException(e); }
    }

    private C4Database openC4Db() throws CouchbaseLiteException {
        final File dbFile = getDatabaseFile(new File(config.getDirectory()), this.name);
        Log.i(DOMAIN, "Opening %s at path %s", this, dbFile.getPath());

        try {
            return new C4Database(
                dbFile.getPath(),
                getDatabaseFlags(),
                null,
                C4Constants.DocumentVersioning.REVISION_TREES,
                getEncryptionAlgorithm(),
                getEncryptionKey());
        }
        catch (LiteCoreException e) {
            if (e.code == CBLError.Code.NOT_A_DATABSE_FILE) {
                throw new CouchbaseLiteException(
                    "The provided encryption key was incorrect.",
                    e,
                    CBLError.Domain.CBLITE,
                    e.code);
            }

            if (e.code == CBLError.Code.CANT_OPEN_FILE) {
                throw new CouchbaseLiteException("CreateDBDirectoryFailed", e, CBLError.Domain.CBLITE, e.code);
            }

            throw CBLStatus.convertException(e);
        }
    }

    private int getDatabaseFlags() { return DEFAULT_DATABASE_FLAGS; }

    // --- C4Database
    private void closeC4DB() throws CouchbaseLiteException {
        try { getC4Database().close(); }
        catch (LiteCoreException e) { throw CBLStatus.convertException(e); }
    }

    //////// DOCUMENTS:

    // --- Database changes:

    @GuardedBy("lock")
    @NonNull
    private ListenerToken addDatabaseChangeListenerLocked(
        @Nullable Executor executor,
        @NonNull DatabaseChangeListener listener) {
        if (dbChangeNotifier == null) {
            dbChangeNotifier = new ChangeNotifier<>();
            registerC4DBObserver();
        }
        return dbChangeNotifier.addChangeListener(executor, listener);
    }

    // --- Notification: - C4DatabaseObserver/C4DocumentObserver

    @GuardedBy("lock")
    private void removeDatabaseChangeListenerLocked(@NonNull ListenerToken token) {
        if (dbChangeNotifier.removeChangeListener(token) == 0) {
            freeC4DBObserver();
            dbChangeNotifier = null;
        }
    }

    // --- Document changes:

    @GuardedBy("lock")
    @NonNull
    private ListenerToken addDocumentChangeListenerLocked(
        @NonNull String docID,
        @Nullable Executor executor,
        @NonNull DocumentChangeListener listener) {
        DocumentChangeNotifier docNotifier = docChangeNotifiers.get(docID);
        if (docNotifier == null) {
            docNotifier = new DocumentChangeNotifier((Database) this, docID);
            docChangeNotifiers.put(docID, docNotifier);
        }
        final ChangeListenerToken token = docNotifier.addChangeListener(executor, listener);
        token.setKey(docID);
        return token;
    }

    // NOTE: calling method must be synchronized.
    @GuardedBy("lock")
    private void removeDocumentChangeListenerLocked(@NonNull ChangeListenerToken token) {
        final String docID = (String) token.getKey();
        if (docChangeNotifiers.containsKey(docID)) {
            final DocumentChangeNotifier notifier = docChangeNotifiers.get(docID);
            if (notifier != null && notifier.removeChangeListener(token) == 0) {
                notifier.stop();
                docChangeNotifiers.remove(docID);
            }
        }
    }

    private void registerC4DBObserver() {
        c4DbObserver = c4db.createDatabaseObserver(
            (observer, context) -> scheduleOnPostNotificationExecutor(this::postDatabaseChanged, 0),
            this);
    }

    // called from finalizer
    @GuardedBy("lock")
    private void freeC4DBObserver() {
        final C4DatabaseObserver observer = c4DbObserver;
        c4DbObserver = null;

        if (observer == null) { return; }
        observer.free();
    }

    // called from finalizer
    @GuardedBy("lock")
    private void freeC4Observers(boolean cleanup) {
        final Map<String, DocumentChangeNotifier> notifiers = docChangeNotifiers;

        freeC4DBObserver();

        if (notifiers == null) { return; }

        for (DocumentChangeNotifier notifier : notifiers.values()) { notifier.stop(); }

        if (cleanup) { notifiers.clear(); }
    }

    private void postDatabaseChanged() {
        synchronized (lock) {
            if ((c4DbObserver == null) || (c4db == null)) { return; }

            boolean external = false;
            int nChanges;
            List<String> docIDs = new ArrayList<>();
            do {
                // Read changes in batches of kMaxChanges:
                final C4DatabaseChange[] c4DbChanges = c4DbObserver.getChanges(MAX_CHANGES);
                nChanges = (c4DbChanges == null) ? 0 : c4DbChanges.length;
                final boolean newExternal = (nChanges > 0) && c4DbChanges[0].isExternal();
                if ((!docIDs.isEmpty()) && ((nChanges <= 0) || (external != newExternal) || (docIDs.size() > 1000))) {
                    dbChangeNotifier.postChange(new DatabaseChange((Database) this, docIDs));
                    docIDs = new ArrayList<>();
                }

                external = newExternal;
                for (int i = 0; i < nChanges; i++) { docIDs.add(c4DbChanges[i].getDocID()); }
            }
            while (nChanges > 0);
        }
    }

    private void prepareDocument(Document document) throws CouchbaseLiteException {
        mustBeOpen();

        if (document.getDatabase() == null) { document.setDatabase((Database) this); }
        else if (document.getDatabase() != this) {
            throw new CouchbaseLiteException(
                "DocumentAnotherDatabase",
                CBLError.Domain.CBLITE,
                CBLError.Code.INVALID_PARAMETER);
        }
    }

    //////// RESOLVE REPLICATED CONFLICTS:
    private void resolveConflictOnce(@Nullable ConflictResolver resolver, @NonNull String docID)
        throws CouchbaseLiteException, CBLInternalException {
        final Document localDoc;
        final Document remoteDoc;
        synchronized (lock) {
            localDoc = new Document((Database) this, docID, true);
            remoteDoc = getConflictingRevision(docID);
        }

        final Document resolvedDoc;
        // If both docs have been deleted, we're done here
        if (localDoc.isDeleted() && remoteDoc.isDeleted()) { resolvedDoc = remoteDoc; }
        else {
            // Resolve with conflict resolver:
            resolvedDoc = resolveConflict(
                (resolver != null) ? resolver : ConflictResolver.DEFAULT,
                docID,
                localDoc,
                remoteDoc);
        }

        synchronized (lock) {
            boolean commit = false;
            beginTransaction();
            try {
                saveResolvedDocument(resolvedDoc, localDoc, remoteDoc);
                commit = true;
            }
            finally { endTransaction(commit); }
        }
    }

    private Document getConflictingRevision(@NonNull String docID)
        throws CouchbaseLiteException, CBLInternalException {
        final Document remoteDoc = new Document((Database) this, docID, true);
        try {
            if (!remoteDoc.selectConflictingRevision()) {
                final String msg = "Unable to select conflicting revision for doc '" + docID + "'. Skipping.";
                Log.w(DOMAIN, msg);
                throw new CBLInternalException(CBLInternalException.FAILED_SELECTING_CONFLICTING_REVISION, msg);
            }
        }
        catch (LiteCoreException e) { throw CBLStatus.convertException(e); }

        return remoteDoc;
    }

    private Document resolveConflict(
        @NonNull ConflictResolver resolver,
        @NonNull String docID,
        @NonNull Document localDoc,
        @NonNull Document remoteDoc)
        throws CouchbaseLiteException {
        final Conflict conflict
            = new Conflict(localDoc.isDeleted() ? null : localDoc, remoteDoc.isDeleted() ? null : remoteDoc);

        Log.v(
            DOMAIN,
            "Resolving doc '%s' (local=%s and remote=%s) with resolver %s",
            docID,
            localDoc.getRevisionID(),
            remoteDoc.getRevisionID(),
            resolver);

        final Document doc;
        try { doc = resolver.resolve(conflict); }
        catch (Exception err) {
            final String msg = String.format(ERROR_RESOLVER_FAILED, docID, err.getLocalizedMessage());
            Log.w(DOMAIN, msg);
            throw new CouchbaseLiteException(msg, err, CBLError.Domain.CBLITE, CBLError.Code.UNEXPECTED_ERROR);
        }

        if (doc == null) { return null; }

        final Database target = doc.getDatabase();
        if (!this.equals(target)) {
            if (target == null) { doc.setDatabase((Database) this); }
            else {
                final String msg = String.format(WARN_WRONG_DATABASE, docID, target.getName(), getName());
                Log.w(DOMAIN, msg);
                throw new CouchbaseLiteException(msg, CBLError.Domain.CBLITE, CBLError.Code.UNEXPECTED_ERROR);
            }
        }

        if (!docID.equals(doc.getId())) {
            Log.w(DOMAIN, WARN_WRONG_ID, doc.getId(), docID);
            return new MutableDocument(docID, doc);
        }

        return doc;
    }

    // Call in a transaction
    @GuardedBy("lock")
    @SuppressWarnings("PMD.NPathComplexity")
    private void saveResolvedDocument(
        @Nullable Document resolvedDoc,
        @NonNull Document localDoc,
        @NonNull Document remoteDoc)
        throws CouchbaseLiteException {
        FLSliceResult mergedBody = null;
        int mergedFlags = 0x00;

        if (resolvedDoc == null) {
            if (remoteDoc.isDeleted()) { resolvedDoc = remoteDoc; }
            else if (localDoc.isDeleted()) { resolvedDoc = localDoc; }
        }

        if (resolvedDoc != null) {
            if (resolvedDoc != localDoc) { resolvedDoc.setDatabase((Database) this); }

            final C4Document c4Doc = resolvedDoc.getC4doc();
            if (c4Doc != null) { mergedFlags = c4Doc.getSelectedFlags(); }
        }

        try {
            // Unless the remote revision is being used as-is, we need a new revision:
            if (resolvedDoc != remoteDoc) {
                if ((resolvedDoc != null) && !resolvedDoc.isDeleted()) { mergedBody = resolvedDoc.encode(); }
                else {
                    mergedFlags |= C4Constants.RevisionFlags.DELETED;
                    final FLEncoder enc = getC4Database().getSharedFleeceEncoder();
                    try {
                        enc.writeValue(new HashMap<>()); // Need an empty dictionary body
                        mergedBody = enc.finish2();
                    }
                    finally { enc.reset(); }
                }
            }

            // Merged body:
            final byte[] mergedBodyBytes = mergedBody == null ? null : mergedBody.getBuf();

            // Ask LiteCore to do the resolution:
            final C4Document rawDoc = Preconditions.assertNotNull(localDoc.getC4doc(), "raw doc is null");
            // The remote branch has to win so that the doc revision history matches the server's.
            rawDoc.resolveConflict(remoteDoc.getRevisionID(), localDoc.getRevisionID(), mergedBodyBytes, mergedFlags);
            rawDoc.save(0);

            Log.i(DOMAIN, "Conflict resolved as doc '%s' rev %s", rawDoc.getDocID(), rawDoc.getRevID());
        }
        catch (LiteCoreException e) {
            throw CBLStatus.convertException(e);
        }
        finally {
            if (mergedBody != null) { mergedBody.free(); }
        }
    }

    private void saveWithConflictHandler(@NonNull MutableDocument document, @NonNull ConflictHandler handler)
        throws CouchbaseLiteException {
        Document oldDoc = null;
        int n = 0;
        while (true) {
            if (n++ > MAX_CONFLICT_RESOLUTION_RETRIES) {
                throw new CouchbaseLiteException(
                    "Too many attempts to resolve a conflicted document: " + n,
                    CBLError.Domain.CBLITE,
                    CBLError.Code.UNEXPECTED_ERROR);
            }

            try {
                saveInternal(document, oldDoc, false, ConcurrencyControl.FAIL_ON_CONFLICT);
                return;
            }
            catch (CouchbaseLiteException e) {
                if (!CouchbaseLiteException.isConflict(e)) { throw e; }
            }

            // Conflict
            synchronized (lock) { oldDoc = new Document((Database) this, document.getId(), true); }

            try {
                if (!handler.handle(document, (oldDoc.isDeleted()) ? null : oldDoc)) {
                    throw new CouchbaseLiteException(
                        "Conflict handler returned false",
                        CBLError.Domain.CBLITE,
                        CBLError.Code.CONFLICT
                    );
                }
            }
            catch (Exception e) {
                throw new CouchbaseLiteException(
                    "Conflict handler threw an exception",
                    e,
                    CBLError.Domain.CBLITE,
                    CBLError.Code.CONFLICT
                );
            }
        }
    }

    // The main save method.
    private void saveInternal(
        @NonNull Document document,
        @Nullable Document baseDoc,
        boolean deleting,
        @NonNull ConcurrencyControl concurrencyControl)
        throws CouchbaseLiteException {
        Preconditions.assertNotNull(document, "document");
        Preconditions.assertNotNull(concurrencyControl, "concurrencyControl");

        if (deleting && (!document.exists())) {
            throw new CouchbaseLiteException(
                "DeleteDocFailedNotSaved",
                CBLError.Domain.CBLITE,
                CBLError.Code.NOT_FOUND);
        }

        synchronized (lock) {
            prepareDocument(document);

            boolean commit = false;
            beginTransaction();
            try {
                try {
                    saveInTransaction(document, (baseDoc == null) ? null : baseDoc.getC4doc(), deleting);
                    commit = true;
                    return;
                }
                catch (CouchbaseLiteException e) {
                    if (!CouchbaseLiteException.isConflict(e)) { throw e; }
                }

                // Conflict

                // return false if FAIL_ON_CONFLICT
                if (concurrencyControl.equals(ConcurrencyControl.FAIL_ON_CONFLICT)) {
                    throw new CouchbaseLiteException("Conflict", CBLError.Domain.CBLITE, CBLError.Code.CONFLICT);
                }

                commit = saveConflicted(document, deleting);
            }
            finally {
                endTransaction(commit);
            }
        }
    }

    private boolean saveConflicted(@NonNull Document document, boolean deleting)
        throws CouchbaseLiteException {

        final C4Document curDoc;

        try { curDoc = getC4Database().get(document.getId(), true); }
        catch (LiteCoreException e) {
            // here if deleting and the curDoc doesn't exist.
            if (deleting
                && (e.domain == C4Constants.ErrorDomain.LITE_CORE)
                && (e.code == C4Constants.LiteCoreError.NOT_FOUND)) {
                return false;
            }

            // here if the save failed.
            throw CBLStatus.convertException(e);
        }

        // here if deleting and the curDoc has already been deleted
        if (deleting && curDoc.deleted()) {
            document.replaceC4Document(curDoc);
            return false;
        }

        // Save changes on the current branch:
        saveInTransaction(document, curDoc, deleting);

        return true;
    }

    // Low-level save method
    @SuppressFBWarnings("RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE")
    private void saveInTransaction(@NonNull Document document, @Nullable C4Document base, boolean deleting)
        throws CouchbaseLiteException {
        FLSliceResult body = null;
        try {
            int revFlags = 0;
            if (deleting) { revFlags = C4Constants.RevisionFlags.DELETED; }
            else if (!document.isEmpty()) {
                // Encode properties to Fleece data:
                body = document.encode();
                if (C4Document.dictContainsBlobs(body, sharedKeys.getFLSharedKeys())) {
                    revFlags |= C4Constants.RevisionFlags.HAS_ATTACHMENTS;
                }
            }

            // Save to database:
            C4Document c4Doc = (base != null) ? base : document.getC4doc();

            c4Doc = (c4Doc != null)
                ? c4Doc.update(body, revFlags)
                : getC4Database().create(document.getId(), body, revFlags);

            document.replaceC4Document(c4Doc);
        }
        catch (LiteCoreException e) {
            throw CBLStatus.convertException(e);
        }
        finally {
            if (body != null) { body.free(); }
        }
    }

    private void purgeSynchronized(@NonNull String id) throws CouchbaseLiteException {
        boolean commit = false;
        beginTransaction();
        try {
            getC4Database().purgeDoc(id);
            commit = true;
        }
        catch (LiteCoreException e) {
            throw CBLStatus.convertException(e);
        }
        finally {
            endTransaction(commit);
        }
    }

    @GuardedBy("lock")
    private void verifyQuiescent() throws CouchbaseLiteException {
        if (hasActiveReplicators()) {
            throw new CouchbaseLiteException(
                "DeleteDBFailedReplications",
                CBLError.Domain.CBLITE,
                CBLError.Code.BUSY);
        }

        if (!activeLiveQueries.isEmpty()) {
            throw new CouchbaseLiteException(
                "DeleteDBFailedQueryListeners",
                CBLError.Domain.CBLITE,
                CBLError.Code.BUSY);
        }
    }

    private boolean hasActiveReplicators() {
        synchronized (lock) { return !activeReplications.isEmpty(); }
    }

    @GuardedBy("lock")
    private void shutdown() {
        // cancel purge
        if (purgeStrategy != null) { purgeStrategy.cancelPurges(); }

        // release instances
        freeC4Observers(true);

        if ((!shellMode) && (c4db != null)) {
            path = c4db.getPath();
            c4db = null;
        }

        // shutdown executor service
        shutdownExecutors(postExecutor, queryExecutor, SHUTDOWN_DELAY_SECS);
    }

    // called from the finalizer
    private void shutdownNow() {
        final DocumentExpirationStrategy strategy = purgeStrategy;

        final ExecutionService.CloseableExecutor pExec = postExecutor;
        final ExecutionService.CloseableExecutor qExec = queryExecutor;

        // cancel purge
        if (strategy != null) { strategy.cancelPurges(); }

        // release instances
        // not guarded by "lock" here but probably ok
        // since called only from the finalizer
        freeC4Observers(false);

        // shutdown executor service with no delay
        shutdownExecutors(pExec, qExec, 0);
    }

    // called from the finalizer
    private void shutdownExecutors(
        ExecutionService.CloseableExecutor pExec,
        ExecutionService.CloseableExecutor qExec,
        int waitTime) {
        // shutdown executor service
        if (pExec != null) { pExec.stop(waitTime, TimeUnit.SECONDS); }
        if (qExec != null) { qExec.stop(waitTime, TimeUnit.SECONDS); }
    }
}
