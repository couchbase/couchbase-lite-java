//
// Document.java
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

import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import com.couchbase.lite.internal.CBLStatus;
import com.couchbase.lite.internal.core.C4Constants;
import com.couchbase.lite.internal.core.C4Database;
import com.couchbase.lite.internal.core.C4Document;
import com.couchbase.lite.internal.fleece.FLDict;
import com.couchbase.lite.internal.fleece.FLEncoder;
import com.couchbase.lite.internal.fleece.FLSliceResult;
import com.couchbase.lite.internal.fleece.MRoot;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * Readonly version of the Document.
 */
@SuppressWarnings("PMD.TooManyMethods")
public class Document implements DictionaryInterface, Iterable<String> {
    // !!! This code is from v1.x. Replace with c4rev_getGeneration().
    private static long generationFromRevID(String revID) {
        long generation = 0;
        final long length = Math.min(revID == null ? 0 : revID.length(), 9);
        for (int i = 0; i < length; ++i) {
            final char c = revID.charAt(i);
            if (Character.isDigit(c)) { generation = 10 * generation + Character.getNumericValue(c); }
            else if (c == '-') { return generation; }
            else { break; }
        }
        return 0;
    }

    //---------------------------------------------
    // member variables
    //---------------------------------------------

    @NonNull
    private final Object lock = new Object(); // lock for thread-safety

    @NonNull
    private final String id;
    private final boolean mutable;

    // note that while internalDict is guarded by lock, the content of the Dictionary is not.
    @SuppressWarnings("NullableProblems")
    @GuardedBy("lock")
    private Dictionary internalDict;

    @GuardedBy("lock")
    @Nullable
    private C4Document c4Document;

    @GuardedBy("lock")
    @Nullable
    private Database database;

    @GuardedBy("lock")
    @Nullable
    private FLDict data;

    // keep a ref to prevent GC
    @SuppressFBWarnings("URF_UNREAD_FIELD")
    @SuppressWarnings("PMD.UnusedPrivateField")
    @GuardedBy("lock")
    @Nullable
    private MRoot root;

    // This nasty little hack is set when a document is created by a replication filter,
    // without a c4doc.  Since that is the only place it is set, it is *also* used
    // in toMutable, as a flag meaning that this document was obtained from a replication filter,
    // to prevent modification of a doc while the replication is running.
    @GuardedBy("lock")
    @Nullable
    private String revId;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    // This is the only constructor that child classes should call
    protected Document(@Nullable Database database, @NonNull String id, @Nullable C4Document c4doc, boolean mutable) {
        this.database = database;
        this.mutable = mutable;
        this.id = id;
        setC4Document(c4doc, mutable);
    }

    Document(@NonNull Database database, @NonNull String id, @Nullable String revId, @Nullable FLDict body) {
        this(database, id, null, false);
        this.data = body;
        this.revId = revId;
        updateDictionaryLocked(false);
    }

    Document(@NonNull Database database, @NonNull String id, boolean includeDeleted) throws CouchbaseLiteException {
        this(database, id, null, false);
        Preconditions.assertNotNull(database, "database");

        final C4Document doc;
        try {
            final C4Database c4db = database.getC4Database();
            if (c4db == null) { throw new IllegalStateException(Log.lookupStandardMessage("DBClosed")); }
            doc = c4db.get(id, true);
        }
        catch (LiteCoreException e) { throw CBLStatus.convertException(e); }

        if (!includeDeleted && (doc.getFlags() & C4Constants.DocumentFlags.DELETED) != 0) {
            throw new CouchbaseLiteException("DocumentNotFound", CBLError.Domain.CBLITE, CBLError.Code.NOT_FOUND);
        }

        // NOTE: c4doc should not be null.
        setC4Document(doc, false);
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * return the document's ID.
     *
     * @return the document's ID
     */
    @NonNull
    public String getId() { return id; }

    /**
     * Get the document's revision id.
     * The revision id in the Document class is a constant while the revision id in the MutableDocument
     * class is not. A newly created Document will have a null revision id. The revision id in
     * a MutableDocument will be updated on save. The revision id format is opaque, which means its format
     * has no meaning and shouldn't be parsed to get information.
     *
     * @return the document's revision id
     */
    @Nullable
    public String getRevisionID() {
        synchronized (lock) { return (c4Document == null) ? revId : c4Document.getSelectedRevID(); }
    }

    /**
     * Return the sequence number of the document in the database.
     * The sequence number indicates how recently the document has been changed.  Every time a document
     * is updated, the database assigns it the next sequential sequence number.  Thus, when a document's
     * sequence number changes it means that the document been update (on-disk).  If one document's sequence
     * is different than another's, the document with the larger sequence number was changed more recently.
     * Sequence numbers are not available for documents obtained from a replication filter.  This method
     * will always return 0 for such documents.
     *
     * @return the sequence number of the document in the database.
     */
    public long getSequence() {
        synchronized (lock) { return (c4Document == null) ? 0 : c4Document.getSelectedSequence(); }
    }

    /**
     * Return a mutable copy of the document
     *
     * @return the MutableDocument instance
     */
    @NonNull
    public MutableDocument toMutable() {
        synchronized (lock) {
            if (revId != null) {
                throw new UnsupportedOperationException("Documents from a replication filter may not be edited.");
            }
        }
        return new MutableDocument(this);
    }

    /**
     * Gets a number of the entries in the dictionary.
     *
     * @return the number of entries in the dictionary.
     */
    @Override
    public int count() { return getContent().count(); }

    //---------------------------------------------
    // API - Implements ReadOnlyDictionaryInterface
    //---------------------------------------------

    /**
     * Get an List containing all keys, or an empty List if the document has no properties.
     *
     * @return all keys
     */
    @NonNull
    @Override
    public List<String> getKeys() { return getContent().getKeys(); }

    /**
     * Gets a property's value as an object. The object types are Blob, Array,
     * Dictionary, Number, or String based on the underlying data type; or nil if the
     * property value is null or the property doesn't exist.
     *
     * @param key the key.
     * @return the object value or null.
     */
    @Nullable
    @Override
    public Object getValue(@NonNull String key) { return getContent().getValue(key); }

    /**
     * Gets a property's value as a String.
     * Returns null if the value doesn't exist, or its value is not a String.
     *
     * @param key the key
     * @return the String or null.
     */
    @Nullable
    @Override
    public String getString(@NonNull String key) { return getContent().getString(key); }

    /**
     * Gets a property's value as a Number.
     * Returns null if the value doesn't exist, or its value is not a Number.
     *
     * @param key the key
     * @return the Number or nil.
     */
    @Nullable
    @Override
    public Number getNumber(@NonNull String key) { return getContent().getNumber(key); }

    /**
     * Gets a property's value as an int.
     * Floating point values will be rounded. The value `true` is returned as 1, `false` as 0.
     * Returns 0 if the value doesn't exist or does not have a numeric value.
     *
     * @param key the key
     * @return the int value.
     */
    @Override
    public int getInt(@NonNull String key) { return getContent().getInt(key); }

    /**
     * Gets a property's value as an long.
     * Floating point values will be rounded. The value `true` is returned as 1, `false` as 0.
     * Returns 0 if the value doesn't exist or does not have a numeric value.
     *
     * @param key the key
     * @return the long value.
     */
    @Override
    public long getLong(@NonNull String key) { return getContent().getLong(key); }

    /**
     * Gets a property's value as an float.
     * Integers will be converted to float. The value `true` is returned as 1.0, `false` as 0.0.
     * Returns 0.0 if the value doesn't exist or does not have a numeric value.
     *
     * @param key the key
     * @return the float value.
     */
    @Override
    public float getFloat(@NonNull String key) { return getContent().getFloat(key); }

    /**
     * Gets a property's value as an double.
     * Integers will be converted to double. The value `true` is returned as 1.0, `false` as 0.0.
     * Returns 0.0 if the property doesn't exist or does not have a numeric value.
     *
     * @param key the key
     * @return the double value.
     */
    @Override
    public double getDouble(@NonNull String key) { return getContent().getDouble(key); }

    /**
     * Gets a property's value as a boolean. Returns true if the value exists, and is either `true`
     * or a nonzero number.
     *
     * @param key the key
     * @return the boolean value.
     */
    @Override
    public boolean getBoolean(@NonNull String key) { return getContent().getBoolean(key); }

    /**
     * Gets a property's value as a Blob.
     * Returns null if the value doesn't exist, or its value is not a Blob.
     *
     * @param key the key
     * @return the Blob value or null.
     */
    @Nullable
    @Override
    public Blob getBlob(@NonNull String key) { return getContent().getBlob(key); }

    /**
     * Gets a property's value as a Date.
     * JSON does not directly support dates, so the actual property value must be a string, which is
     * then parsed according to the ISO-8601 date format (the default used in JSON.)
     * Returns null if the value doesn't exist, is not a string, or is not parsable as a date.
     * NOTE: This is not a generic date parser! It only recognizes the ISO-8601 format, with or
     * without milliseconds.
     *
     * @param key the key
     * @return the Date value or null.
     */
    @Nullable
    @Override
    public Date getDate(@NonNull String key) { return getContent().getDate(key); }

    /**
     * Get a property's value as a Array, which is a mapping object of an array value.
     * Returns null if the property doesn't exists, or its value is not an Array.
     *
     * @param key the key
     * @return The Array object or null.
     */
    @Nullable
    @Override
    public Array getArray(@NonNull String key) { return getContent().getArray(key); }

    /**
     * Get a property's value as a Dictionary, which is a mapping object of
     * a Dictionary value.
     * Returns null if the property doesn't exists, or its value is not a Dictionary.
     *
     * @param key the key
     * @return The Dictionary object or null.
     */
    @Nullable
    @Override
    public Dictionary getDictionary(@NonNull String key) { return getContent().getDictionary(key); }

    /**
     * Gets content of the current object as an Map. The values contained in the returned
     * Map object are all JSON based values.
     *
     * @return the Map object representing the content of the current object in the JSON format.
     */
    @NonNull
    @Override
    public Map<String, Object> toMap() { return getContent().toMap(); }

    /**
     * Tests whether a property exists or not.
     * This can be less expensive than getValue(String),
     * because it does not have to allocate an Object for the property value.
     *
     * @param key the key
     * @return the boolean value representing whether a property exists or not.
     */
    @Override
    public boolean contains(@NonNull String key) { return getContent().contains(key); }

    //---------------------------------------------
    // Iterator implementation
    //---------------------------------------------

    /**
     * Gets  an iterator over the keys of the document's properties
     *
     * @return The key iterator
     */
    @NonNull
    @Override
    public Iterator<String> iterator() { return getKeys().iterator(); }

    //---------------------------------------------
    // Override
    //---------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (!(o instanceof Document)) { return false; }

        final Document doc = (Document) o;

        final Database db = getDatabase();
        final Database otherDb = doc.getDatabase();
        // Step 1: Check Database
        if ((db == null) ? otherDb != null : !db.equalsWithPath(otherDb)) { return false; }

        // Step 2: Check document ID
        // NOTE id never null?
        if (!id.equals(doc.id)) { return false; }

        // Step 3: Check content
        // NOTE: internalDict never null??
        return getContent().equals(doc.getContent());
    }

    @Override
    public int hashCode() {
        // NOTE id and internalDict never null
        final Database db = getDatabase();
        int result = db != null && db.getPath() != null ? db.getPath().hashCode() : 0;
        result = 31 * result + id.hashCode();
        result = 31 * result + getContent().hashCode();
        return result;
    }

    @SuppressWarnings("PMD.ConsecutiveLiteralAppends")
    @NonNull
    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder("Document")
            .append(isMutable() ? '+' : '.')
            .append(isDeleted() ? '?' : '.')
            .append('{').append(id).append('@').append(getRevisionID()).append(':');

        boolean first = true;
        for (String key : getKeys()) {
            if (first) { first = false; }
            else { buf.append(','); }

            buf.append(key).append("=>").append(getValue(key));
        }

        return buf.append('}').toString();
    }

    @NonNull
    protected final Dictionary getContent() {
        synchronized (lock) { return internalDict; }
    }

    protected final void setContent(@NonNull Dictionary content) {
        synchronized (lock) { internalDict = content; }
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    final boolean isMutable() { return mutable; }

    // !!! should use c4rev_getGeneration
    long generation() { return generationFromRevID(getRevisionID()); }

    final boolean isEmpty() { return getContent().isEmpty(); }

    final boolean isNewDocument() { return getRevisionID() == null; }

    /**
     * Return whether the document exists in the database.
     *
     * @return true if exists, false otherwise.
     */
    final boolean exists() {
        synchronized (lock) { return (c4Document != null) && c4Document.exists(); }
    }

    /**
     * Return whether the document is deleted
     *
     * @return true if deleted, false otherwise
     */
    final boolean isDeleted() {
        synchronized (lock) { return (c4Document != null) && c4Document.deleted(); }
    }

    @Nullable
    final Database getDatabase() {
        synchronized (lock) { return database; }
    }

    void setDatabase(@Nullable Database database) {
        synchronized (lock) { this.database = database; }
    }

    @Nullable
    final C4Document getC4doc() {
        synchronized (lock) { return c4Document; }
    }

    final void replaceC4Document(@Nullable C4Document c4doc) {
        synchronized (lock) { updateC4DocumentLocked(c4doc); }
    }

    final boolean selectConflictingRevision() throws LiteCoreException {
        boolean foundConflict = false;
        synchronized (lock) {
            if (c4Document == null) { return false; }

            while (!foundConflict) {
                try { c4Document.selectNextLeafRevision(true, true); }
                catch (LiteCoreException e) {
                    // NOTE: other platforms checks if return value from c4doc_selectNextLeafRevision() is false
                    if (e.code == 0) { break; }
                    else { throw e; }
                }
                foundConflict = c4Document.isSelectedRevFlags(C4Constants.RevisionFlags.IS_CONFLICT);
            }

            if (foundConflict) { setC4Document(c4Document, isMutable()); }
        }

        return foundConflict;
    }

    @NonNull
    final FLSliceResult encode() throws LiteCoreException {
        final Database db = getDatabase();
        if (db == null) { throw new IllegalStateException("encode called with null database"); }

        final FLEncoder encoder = db.getC4Database().getSharedFleeceEncoder();
        try {
            encoder.setExtraInfo(this);
            getContent().encodeTo(encoder);
            return encoder.finish2();
        }
        finally {
            encoder.setExtraInfo(null);
            encoder.reset();
        }
    }


    //---------------------------------------------
    // Private access
    //---------------------------------------------

    // Sets c4doc and updates the root dictionary
    private void setC4Document(@Nullable C4Document c4doc, boolean mutable) {
        synchronized (lock) {
            updateC4DocumentLocked(c4doc);
            data = ((c4doc == null) || c4doc.deleted()) ? null : c4doc.getSelectedBody2();
            updateDictionaryLocked(mutable);
        }
    }

    @GuardedBy("lock")
    private void updateC4DocumentLocked(@Nullable C4Document c4Doc) {
        if (c4Document == c4Doc) { return; }

        if (c4Doc != null) { revId = null; }

        c4Document = c4Doc;
    }

    @GuardedBy("lock")
    private void updateDictionaryLocked(boolean mutable) {
        if (data == null) {
            root = null;
            internalDict = mutable ? new MutableDictionary() : new Dictionary();
            return;
        }

        final Database db = getDatabase();
        if (db == null) { throw new IllegalStateException(""); }

        final MRoot newRoot = new MRoot(new DocContext(db, c4Document), data.toFLValue(), isMutable());
        root = newRoot;
        synchronized (db.getLock()) { internalDict = (Dictionary) newRoot.asNative(); }
    }
}
