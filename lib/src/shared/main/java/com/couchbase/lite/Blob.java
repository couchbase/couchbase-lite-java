//
// Blob.java
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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import com.couchbase.lite.internal.core.C4BlobKey;
import com.couchbase.lite.internal.core.C4BlobReadStream;
import com.couchbase.lite.internal.core.C4BlobStore;
import com.couchbase.lite.internal.core.C4BlobWriteStream;
import com.couchbase.lite.internal.fleece.FLEncodable;
import com.couchbase.lite.internal.fleece.FLEncoder;
import com.couchbase.lite.internal.fleece.FLSliceResult;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * A Couchbase Lite Blob. A Blob appears as a property of a Document:
 * it contains arbitrary binary data, tagged with MIME type.
 * Blobs can be arbitrarily large, although some operations may require that the entire
 * content be loaded into memory.  The document's raw JSON form only contains
 * the Blob's metadata (type, length and digest of the data) in small object.
 * The data itself is stored externally to the document, keyed by the digest.)
 * <p>
 **/
public final class Blob implements FLEncodable {

    //---------------------------------------------
    // Constants
    //---------------------------------------------
    private static final LogDomain DOMAIN = LogDomain.DATABASE;

    /**
     * The sub-document property that identifies it as a special type of object.
     * For example, a blob is represented as `{"@type":"blob", "digest":"xxxx", ...}`
     */
    static final String META_PROP_TYPE = "@type";
    static final String TYPE_BLOB = "blob";

    static final String PROP_DIGEST = "digest";
    static final String PROP_LENGTH = "length";
    static final String PROP_CONTENT_TYPE = "content_type";
    static final String PROP_DATA = "data";
    static final String PROP_STUB = "stub";
    static final String PROP_REVPOS = "revpos";


    // Max size of data that will be cached in memory with the CBLBlob
    private static final int MAX_CACHED_CONTENT_LENGTH = 8 * 1024;


    //---------------------------------------------
    // Types
    //---------------------------------------------

    static final class BlobInputStream extends InputStream {
        private C4BlobKey key;
        private C4BlobStore store;
        private C4BlobReadStream blobStream;

        BlobInputStream(@NonNull C4BlobKey key, @NonNull C4BlobStore store) throws LiteCoreException {
            Preconditions.assertNotNull(key, "key");
            Preconditions.assertNotNull(store, "store");

            this.key = key;
            this.store = store;

            this.blobStream = store.openReadStream(key);
        }

        // not supported...
        @SuppressWarnings("PMD.UselessOverridingMethod")
        @Override
        public int available() throws IOException { return super.available(); }

        // I think we could support this.
        // Currently, however, we do not.
        @Override
        public boolean markSupported() { return false; }

        @Override
        public synchronized void mark(int readlimit) {
            throw new UnsupportedOperationException("'mark()' not supported");
        }

        @Override
        public synchronized void reset() {
            throw new UnsupportedOperationException("'reset()' not supported");
        }

        @Override
        public long skip(long n) throws IOException {
            if (key == null) { throw new IOException("Stream is closed"); }

            try {
                blobStream.seek(n);
                return n;
            }
            catch (LiteCoreException e) {
                throw new IOException(e);
            }
        }

        @Override
        public int read() throws IOException {
            if (key == null) { throw new IOException("Stream is closed"); }

            try {
                final byte[] bytes = blobStream.read(1);
                return (bytes.length <= 0) ? -1 : bytes[0];
            }
            catch (LiteCoreException e) {
                throw new IOException(e);
            }
        }

        @Override
        public int read(@NonNull byte[] buf) throws IOException { return read(buf, 0, buf.length); }

        @Override
        public int read(@NonNull byte[] buf, int off, int len) throws IOException {
            Preconditions.assertNotNull(buf, "buffer");
            if (off < 0) { throw new IndexOutOfBoundsException("Read offset < 0: " + off); }
            if (len < 0) { throw new IndexOutOfBoundsException("Read length < 0: " + len); }

            if (off + len > buf.length) {
                throw new IndexOutOfBoundsException(
                    "off + len > buf.length (" + off + ", " + len + ", " + buf.length + ")");
            }

            if (len == 0) { return 0; }

            if (key == null) { throw new IOException("Stream is closed"); }

            try {
                final int n = blobStream.read(buf, off, len);
                return (n <= 0) ? -1 : n;
            }
            catch (LiteCoreException e) {
                throw new IOException("Failed reading blob", e);
            }
        }

        @Override
        public void close() throws IOException {
            super.close();

            // close internal stream
            if (blobStream != null) {
                blobStream.close();
                blobStream = null;
            }

            // key should be free
            if (key != null) {
                key.free();
                key = null;
            }

            if (store != null) {
                store.free();
                store = null;
            }
        }
    }

    //---------------------------------------------
    // member variables
    //---------------------------------------------

    // A newly created unsaved blob will have either blobContent or blobContentStream non-null.
    // A new blob saved to the database will have database and digest.
    // A blob loaded from the database will have database, properties, and digest unless invalid

    /**
     * The type of content this CBLBlob represents; by convention this is a MIME type.
     */
    @NonNull
    private final String contentType;

    /**
     * The binary length of this CBLBlob.
     */
    private long blobLength;

    /**
     * The contents of a CBLBlob as a block of memory.
     * Assert((blobContentStream == null) || (blobContent == null))
     */
    @Nullable
    private byte[] blobContent;

    /**
     * The contents of a CBLBlob as a stream.
     * Assert((blobContentStream == null) || (blobContent == null))
     */
    @Nullable
    private InputStream blobContentStream;

    /**
     * Null if blob is new and unsaved
     */
    @Nullable
    private Database database;

    /**
     * The cryptographic digest of this CBLBlob's contents, which uniquely identifies it.
     */
    @Nullable
    private String blobDigest;

    /**
     * The metadata associated with this CBLBlob.
     * Only in blob read from database
     */
    @Nullable
    private Map<String, Object> properties;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    /**
     * Construct a Blob with the given in-memory data.
     *
     * @param contentType The type of content this Blob will represent
     * @param content     The data that this Blob will contain
     */
    public Blob(@NonNull String contentType, @NonNull byte[] content) {
        Preconditions.assertNotNull(contentType, "contentType");
        Preconditions.assertNotNull(content, "content");

        this.contentType = contentType;
        blobLength = content.length;
        blobContent = copyBytes(content);
        blobContentStream = null;
    }

    /**
     * Construct a Blob with the given stream of data.
     * The passed stream will be closed when it is copied either to memory
     * (see <code>getContent</code>) or to the database.
     * If it is closed before that, by client code, the attempt to store the blob will fail.
     * The converse is also true: the stream for a blob that is not saved or copied to memory
     * will not be closed (except during garbage collection).
     *
     * @param contentType The type of content this Blob will represent
     * @param stream      The stream of data that this Blob will consume
     */
    public Blob(@NonNull String contentType, @NonNull InputStream stream) {
        Preconditions.assertNotNull(contentType, "contentType");
        this.contentType = contentType;
        initStream(stream);
    }

    /**
     * Construct a Blob with the content of a file.
     * The blob can then be added as a property of a Document.
     * This constructor creates a stream that is not closed until the blob is stored in the db,
     * or copied to memory (except by garbage collection).
     *
     * @param contentType The type of content this Blob will represent
     * @param fileURL     A URL to a file containing the data that this Blob will represent.
     * @throws IOException on failure to open the file URL
     */
    public Blob(@NonNull String contentType, @NonNull URL fileURL) throws IOException {
        Preconditions.assertNotNull(contentType, "contentType");
        Preconditions.assertNotNull(fileURL, "fileUrl");

        if (!fileURL.getProtocol().equalsIgnoreCase("file")) {
            throw new IllegalArgumentException(Log.formatStandardMessage("NotFileBasedURL", fileURL));
        }

        this.contentType = contentType;

        initStream(fileURL.openStream());
    }

    // Initializer for an existing blob being read from a document
    Blob(@NonNull Database database, @NonNull Map<String, Object> properties) {
        this.database = database;

        this.properties = new HashMap<>(properties);
        this.properties.remove(META_PROP_TYPE);

        // NOTE: length field might not be set if length is unknown.
        final Object len = properties.get(PROP_LENGTH);
        if (len instanceof Number) { this.blobLength = ((Number) len).longValue(); }
        this.blobDigest = (String) properties.get(PROP_DIGEST);
        this.contentType = (String) properties.get(PROP_CONTENT_TYPE);

        final Object data = properties.get(PROP_DATA);
        if (data instanceof byte[]) { blobContent = (byte[]) data; }

        if ((this.blobDigest == null) && (blobContent == null)) {
            Log.w(DOMAIN, "Blob read from database has neither digest nor data.");
        }
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * Gets the contents of this blob as in in-memory byte array.
     * <b>Using this method will cause the entire contents of the blob to be read into memory!</b>
     *
     * @return the contents of a Blob as a block of memory
     */
    @Nullable
    public byte[] getContent() {
        // this will load blobContent from the blobContentStream (all of it!), if there is any
        if (blobContentStream != null) { readContentFromInitStream(); }

        if (blobContent != null) { return copyBytes(blobContent); }

        if (database != null) { return getContentFromDatabase(); }

        return null;
    }

    /**
     * Get a the contents of this blob as a stream.
     * The caller is responsible for closing the stream returned by this call.
     * Closing or deleting the database before this call completes may cause it to fail.
     * <b>When called on a blob created from a stream (or a file path), this method will return null!</b>
     *
     * @return a stream of of this blobs contents; null if none exsits or if this blob was initialized with a stream
     */
    @Nullable
    public InputStream getContentStream() {
        // refuse to provide a content stream, if this Blob was initialized from a content stream
        if (blobContentStream != null) { return null; }

        if (blobContent != null) { return new ByteArrayInputStream(blobContent); }

        if (database != null) { return getStreamFromDatabase(database); }

        return null;
    }

    /**
     * Return the type of of the content this blob contains.  By convention this is a MIME type.
     *
     * @return the type of blobContent
     */
    @NonNull
    public String getContentType() { return contentType; }

    /**
     * The number of byte of content this blob contains.
     *
     * @return The length of the blob or 0 if initialized with a stream.
     */
    public long length() { return blobLength; }

    /**
     * The cryptographic digest of this Blob's contents, which uniquely identifies it.
     *
     * @return The cryptograhic digest of this blob's contents; null if the content has not been saved in a database
     */
    @Nullable
    public String digest() { return blobDigest; }

    /**
     * Get the blob metadata
     *
     * @return metadata for this Blob
     */
    @NonNull
    public Map<String, Object> getProperties() {
        // Blob read from database;
        if (properties != null) { return properties; }

        final Map<String, Object> props = new HashMap<>();
        props.put(PROP_DIGEST, blobDigest);
        props.put(PROP_LENGTH, blobLength);
        props.put(PROP_CONTENT_TYPE, contentType);
        return props;
    }

    //---------------------------------------------
    // Override
    //---------------------------------------------

    //FLEncodable
    @Override
    public void encodeTo(@NonNull FLEncoder encoder) {
        final Object info = encoder.getExtraInfo();
        if (info != null) {
            final Database db = Preconditions.assertNotNull(((MutableDocument) info).getDatabase(), "db");
            installInDatabase(db);
        }

        final Map<String, Object> dict = getJsonRepresentation();
        encoder.beginDict(dict.size());
        for (Map.Entry<String, Object> entry : dict.entrySet()) {
            encoder.writeKey(entry.getKey());
            encoder.writeValue(entry.getValue());
        }
        encoder.endDict();
    }

    /**
     * Returns a string representation of the object.
     *
     * @return a string representation of the object
     */
    @NonNull
    @Override
    public String toString() { return "Blob[" + contentType + "; " + ((length() + 512) / 1024) + " KB]"; }

    /**
     * Get the blob hash code.
     *
     * <b>This method is quite expensive. Also, when called on a blob created from a stream
     * (or a file path), it will cause the entire contents of that stream to be read into memory!</b>
     *
     * @return hash code for the object
     */
    @Override
    public int hashCode() { return Arrays.hashCode(getContent()); }

    /**
     * Compare for equality.
     *
     * <b>This method is quite expensive. Also, when called on a blob created from a stream
     * (or a file path), it will cause the entire contents of that stream to be read into memory!</b>
     *
     * @return true if this object is the same as that one.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (!(o instanceof Blob)) { return false; }

        final Blob m = (Blob) o;
        return ((blobDigest != null) && (m.blobDigest != null))
            ? blobDigest.equals(m.blobDigest)
            : Arrays.equals(getContent(), m.getContent());
    }

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        final InputStream contentStream = blobContentStream;
        if (contentStream != null) {
            try { contentStream.close(); }
            catch (IOException ignore) { }
        }
        super.finalize();
    }

    //---------------------------------------------
    // Private (in class only)
    //---------------------------------------------

    @Nullable
    private byte[] copyBytes(@Nullable byte[] b) {
        if (b == null) { return null; }
        final int len = b.length;
        final byte[] copy = new byte[len];
        System.arraycopy(b, 0, copy, 0, len);
        return copy;
    }

    private void initStream(@NonNull InputStream stream) {
        Preconditions.assertNotNull(stream, "input stream");
        blobLength = 0;
        blobContent = null;
        blobContentStream = stream;
    }

    @SuppressFBWarnings("RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE")
    @Nullable
    private byte[] getContentFromDatabase() {
        Preconditions.assertNotNull(database, "database");

        C4BlobStore blobStore = null;
        C4BlobKey key = null;
        FLSliceResult res = null;
        final byte[] newContent;
        try {
            blobStore = database.getBlobStore();

            key = new C4BlobKey(blobDigest);

            res = blobStore.getContents(key);

            newContent = res.getBuf();
        }
        catch (LiteCoreException e) {
            final String msg = "Failed to read content from database for digest: " + blobDigest;
            Log.e(DOMAIN, msg, e);
            throw new IllegalStateException(msg, e);
        }
        finally {
            if (res != null) { res.free(); }
            if (key != null) { key.free(); }
            if (blobStore != null) { blobStore.free(); }
        }

        // cache content if less than 8K
        if ((newContent != null) && (newContent.length < MAX_CACHED_CONTENT_LENGTH)) { blobContent = newContent; }

        return newContent;
    }

    @NonNull
    private InputStream getStreamFromDatabase(@NonNull Database db) {
        C4BlobKey key = null;
        try {
            key = new C4BlobKey(blobDigest);
            return new BlobInputStream(key, db.getBlobStore());
        }
        catch (IllegalArgumentException | LiteCoreException e) {
            if (key != null) { key.free(); }
            throw new IllegalStateException("Failed opening blobContent stream.", e);
        }
    }

    private void installInDatabase(@NonNull Database db) {
        Preconditions.assertNotNull(db, "database");

        if (database != null) {
            if (this.database == db) { return; }

            throw new IllegalStateException(Log.lookupStandardMessage("BlobDifferentDatabase"));
        }

        C4BlobKey key = null;
        C4BlobStore store = null;
        try {
            store = db.getBlobStore();

            if (blobContent != null) { key = store.create(blobContent); }
            else if (blobContentStream != null) { key = writeDatabaseFromInitStream(store); }
            else { throw new IllegalStateException(Log.lookupStandardMessage("BlobContentNull")); }

            this.database = db;
            this.blobDigest = key.toString();
        }
        catch (Exception e) {
            throw new IllegalStateException("Failed reading blob content from database", e);
        }
        finally {
            if (key != null) { key.free(); }
            if (store != null) { store.free(); }
        }
    }

    private Map<String, Object> getJsonRepresentation() {
        final Map<String, Object> json = new HashMap<>(getProperties());
        json.put(META_PROP_TYPE, TYPE_BLOB);

        if (blobDigest != null) { json.put(PROP_DIGEST, blobDigest); }
        else { json.put(PROP_DATA, getContent()); }

        return json;
    }

    @SuppressFBWarnings("DE_MIGHT_IGNORE")
    private void readContentFromInitStream() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = Preconditions.assertNotNull(blobContentStream, "content stream")) {
            final byte[] buff = new byte[MAX_CACHED_CONTENT_LENGTH];
            int n;
            while ((n = in.read(buff)) >= 0) { out.write(buff, 0, n); }
        }
        catch (IOException e) {
            throw new IllegalStateException("Failed reading blob content stream", e);
        }
        finally {
            blobContentStream = null;
        }

        blobContent = out.toByteArray();
        blobLength = blobContent.length;
    }

    @SuppressFBWarnings("DE_MIGHT_IGNORE")
    @NonNull
    private C4BlobKey writeDatabaseFromInitStream(@NonNull C4BlobStore store) throws LiteCoreException, IOException {
        if (blobContentStream == null) { throw new IllegalStateException("Blob stream is null"); }

        final C4BlobKey key;

        int len = 0;
        final byte[] buffer;
        C4BlobWriteStream blobOut = null;
        try {
            blobOut = store.openWriteStream();

            buffer = new byte[MAX_CACHED_CONTENT_LENGTH];
            int n;
            while ((n = blobContentStream.read(buffer)) >= 0) {
                blobOut.write(buffer, n);
                len += n;
            }

            blobOut.install();

            key = blobOut.computeBlobKey();
        }
        finally {
            try { blobContentStream.close(); }
            catch (IOException ignore) { }
            blobContentStream = null;

            if (blobOut != null) { blobOut.close(); }
        }

        blobLength = len;

        // don't cache more than 8K
        if ((blobContent != null) && (blobContent.length <= MAX_CACHED_CONTENT_LENGTH)) { blobContent = buffer; }

        return key;
    }
}
