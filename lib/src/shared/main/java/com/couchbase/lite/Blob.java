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
import java.util.Locale;
import java.util.Map;

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
 * Blobs can be arbitrarily large and their data is loaded only on demand
 * (when the `content` or `contentStream` properties are accessed),
 * not when the document is loaded. The document's raw JSON form only contains
 * the Blob's metadata (type, length and digest of the data) in small object.
 * The data itself is stored externally to the document, keyed by the digest.)
 */
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

    private static final String PROP_DIGEST = "digest";
    private static final String PROP_LENGTH = "length";
    private static final String PROP_CONTENT_TYPE = "content_type";
    private static final String PROP_DATA = "data";

    // Max size of data that will be cached in memory with the CBLBlob
    private static final int MAX_CACHED_CONTENT_LENGTH = 8 * 1024;


    //---------------------------------------------
    // Types
    //---------------------------------------------

    static final class BlobInputStream extends InputStream {
        private C4BlobKey key;
        private C4BlobStore store;
        private C4BlobReadStream readStream;

        BlobInputStream(@NonNull C4BlobKey key, @NonNull C4BlobStore store) throws LiteCoreException {
            Preconditions.checkArgNotNull(key, "key");
            Preconditions.checkArgNotNull(store, "store");

            this.key = key;
            this.store = store;

            this.readStream = store.openReadStream(key);
        }

        // not supported...
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
        public synchronized void reset() throws IOException {
            throw new UnsupportedOperationException("'reset()' not supported");
        }

        @Override
        public long skip(long n) throws IOException {
            if (key == null) { throw new IOException("Stream is closed"); }

            try {
                readStream.seek(n);
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
                final byte[] bytes = readStream.read(1);
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
            Preconditions.checkArgNotNull(buf, "buffer");
            if (off < 0) { throw new IndexOutOfBoundsException("Read offset < 0: " + off); }
            if (len < 0) { throw new IndexOutOfBoundsException("Read length < 0: " + len); }

            if (off + len > buf.length) {
                throw new IndexOutOfBoundsException(
                    "off + len > buf.length (" + off + ", " + len + ", " + buf.length + ")");
            }

            if (len == 0) { return 0; }

            if (key == null) { throw new IOException("Stream is closed"); }

            try {
                final int n = readStream.read(buf, off, len);
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
            if (readStream != null) {
                readStream.close();
                readStream = null;
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

    // A newly created unsaved blob will have either content or initialContentStream.
    // A new blob saved to the database will have database and digest.
    // A blob loaded from the database will have database, properties, and digest unless invalid

    /**
     * The type of content this CBLBlob represents; by convention this is a MIME type.
     */
    @NonNull
    private final String contentType;

    /**
     * Gets the contents of a CBLBlob as a block of memory.
     * Not recommended for very large blobs, as it may be slow and use up lots of RAM.
     * <p>
     * Non-null if new from data or if small and loaded from the db or stream source
     */
    @Nullable
    private byte[] content;

    /**
     * Non-null if new from stream
     */
    @Nullable
    private InputStream initialContentStream;

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
     * The binary length of this CBLBlob.
     */
    private long blobLength;

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
     * The blob can then be added as a property of a Document.
     *
     * @param contentType The type of content this Blob will represent
     * @param content     The data that this Blob will contain
     */
    public Blob(@NonNull String contentType, @NonNull byte[] content) {
        Preconditions.checkArgNotNull(contentType, "contentType");
        Preconditions.checkArgNotNull(content, "content");

        this.contentType = contentType;
        this.content = copyBytes(content);

        this.blobLength = this.content.length;
    }

    /**
     * Construct a Blob with the given stream of data.
     * The blob can then be added as a property of a Document.
     *
     * @param contentType The type of content this Blob will represent
     * @param stream      The stream of data that this Blob will consume
     */
    public Blob(@NonNull String contentType, @NonNull InputStream stream) {
        Preconditions.checkArgNotNull(contentType, "contentType");
        Preconditions.checkArgNotNull(stream, "stream");

        this.contentType = contentType;
        this.initialContentStream = stream;
        this.blobLength = 0L; // unknown
    }

    /**
     * Construct a Blob with the content of a file.
     * The blob can then be added as a property of a Document.
     *
     * @param contentType The type of content this Blob will represent
     * @param fileURL     A URL to a file containing the data that this Blob will represent.
     * @throws IOException on failure to open the file URL
     */
    public Blob(@NonNull String contentType, @NonNull URL fileURL) throws IOException {
        Preconditions.checkArgNotNull(contentType, "contentType");
        Preconditions.checkArgNotNull(fileURL, "fileUrl");

        if (!fileURL.getProtocol().equalsIgnoreCase("file")) {
            throw new IllegalArgumentException(fileURL + "must be a file-based URL.");
        }

        this.contentType = contentType;
        this.initialContentStream = fileURL.openStream();
        this.blobLength = 0L; // unknown
    }

    // Initializer for an existing blob being read from a document
    Blob(@NonNull Database database, @NonNull Map<String, Object> properties) {
        this.database = database;
        this.properties = new HashMap<>(properties);
        this.properties.remove(META_PROP_TYPE);

        // NOTE: length field might not be set if length is unknown.
        final Object len = properties.get("length");
        if (len instanceof Number) { this.blobLength = ((Number) len).longValue(); }
        this.blobDigest = (String) properties.get("digest");
        this.contentType = (String) properties.get("content_type");

        final Object data = properties.get(PROP_DATA);
        if (data instanceof byte[]) { content = (byte[]) data; }

        if ((this.blobDigest == null) && (content == null)) {
            Log.w(DOMAIN, "Blob read from database has neither digest nor data.");
        }
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * Gets the contents of a Blob as a block of memory.
     * Not recommended for very large blobs, as it may be slow and use up lots of RAM.
     *
     * @return the contents of a Blob as a block of memory
     */
    @Nullable
    public byte[] getContent() {
        if (content != null) { return copyBytes(content); }

        if (database != null) { return getBytesFromDatabase(); }

        // data must be in the initial content stream
        // No recourse but to read it
        if (initialContentStream == null) { throw new IllegalStateException("Initial content stream is null"); }
        try { return getBytesFromStream(initialContentStream); }
        finally {
            try { initialContentStream.close(); }
            catch (IOException e) { Log.i(DOMAIN, "Failed reading blob content stream", e); }
            initialContentStream = null;
        }
    }

    /**
     * Get the stream of content of a Blob.
     * The caller is responsible for closing the stream when finished.
     *
     * @return the stream of content of a Blob
     */
    @Nullable
    public InputStream getContentStream() {
        if (database == null) {
            // if content == null, this call is going to convert
            // some kind of stream into a byte array,
            // which we are promptly going to convert back to a stream
            final byte[] content = getContent();
            return (content == null) ? null : new ByteArrayInputStream(content);
        }

        C4BlobKey key = null;
        try {
            key = new C4BlobKey(blobDigest);
            return new BlobInputStream(key, database.getBlobStore());
        }
        catch (IllegalArgumentException | LiteCoreException e) {
            if (key != null) { key.free(); }
            throw new IllegalStateException("Failed opening content stream.", e);
        }
    }

    /**
     * Return the type of content this Blob represents; by convention this is a MIME type.
     *
     * @return the type of content
     */
    @NonNull
    public String getContentType() { return contentType; }

    /**
     * The binary length of this Blob
     *
     * @return The binary length of this Blob
     */
    public long length() { return blobLength; }

    /**
     * The cryptographic digest of this Blob's contents, which uniquely identifies it.
     *
     * @return The cryptograhic digest of this Blob's contents
     */
    @Nullable
    public String digest() { return blobDigest; }

    /**
     * Return the metadata associated with this Blob
     *
     * @return the metadata associated with this Blob
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
        if (info != null) { installInDatabase(((MutableDocument) info).getDatabase()); }

        final Map<String, Object> dict = jsonRepresentation();
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
    public String toString() {
        return String.format(Locale.ENGLISH, "Blob[%s; %d KB]", contentType, (length() + 512) / 1024);
    }

    @Override
    public int hashCode() { return Arrays.hashCode(getContent()); }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (!(o instanceof Blob)) { return false; }

        final Blob m = (Blob) o;
        return ((blobDigest != null) && (m.blobDigest != null))
            ? blobDigest.equals(m.blobDigest)
            : Arrays.equals(getContent(), m.getContent());
    }

    //---------------------------------------------
    // Private (in class only)
    //---------------------------------------------

    private Map<String, Object> jsonRepresentation() {
        final Map<String, Object> json = new HashMap<>(getProperties());
        json.put(META_PROP_TYPE, TYPE_BLOB);
        if (blobDigest != null) { json.put(PROP_DIGEST, blobDigest); }
        else { json.put(PROP_DATA, getContent()); }
        return json;
    }

    private void installInDatabase(@NonNull Database db) {
        Preconditions.checkArgNotNull(db, "database");

        if (database != null) {
            if (this.database == db) { return; }

            throw new IllegalStateException(
                "Attempt to save a document containing a blob that was saved to a another database. "
                    + "The save operation cannot be completed.");
        }

        C4BlobKey key = null;
        C4BlobStore store = null;
        try {
            store = db.getBlobStore();

            key = (content != null) ? store.create(content) : storeBytesInDatabase(store);

            this.blobDigest = key.toString();
            this.database = db;
        }
        catch (Exception e) {
            throw new IllegalStateException(e);
        }
        finally {
            if (key != null) { key.free(); }
            if (store != null) { store.free(); }
        }
    }

    @NonNull
    private byte[] getBytesFromStream(@NonNull InputStream in) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buff = new byte[MAX_CACHED_CONTENT_LENGTH];
        try {
            int n;
            while ((n = in.read(buff)) >= 0) { out.write(buff, 0, n); }
            buff = out.toByteArray();
        }
        catch (IOException e) {
            final String msg = "Failed reading blob content stream: " + in;
            Log.w(DOMAIN, msg, e);
            throw new IllegalStateException(msg, e);
        }
        finally {
            try { out.close(); }
            catch (IOException ignore) { }
        }

        blobLength = cacheContent(buff);

        return buff;
    }

    // Read contents from the BlobStore:
    // Don't have to close the BlobStore because it is created by the database.
    @Nullable
    private byte[] getBytesFromDatabase() {
        Preconditions.checkArgNotNull(database, "database");

        C4BlobStore blobStore = null;
        C4BlobKey key = null;
        FLSliceResult res = null;
        try {
            blobStore = database.getBlobStore();

            key = new C4BlobKey(blobDigest);

            res = blobStore.getContents(key);

            final byte[] newContent = res.getBuf();
            if (newContent == null) { return null; }

            cacheContent(newContent);

            return newContent;
        }
        catch (LiteCoreException e) {
            final String msg = "Failed to obtain BlobStore content for digest: " + blobDigest;
            Log.e(DOMAIN, msg, e);
            throw new IllegalStateException(msg, e);
        }
        finally {
            if (res != null) { res.free(); }
            if (key != null) { key.free(); }
            if (blobStore != null) { blobStore.free(); }
        }
    }

    private C4BlobKey storeBytesInDatabase(C4BlobStore store) throws LiteCoreException, IOException {
        final InputStream contentStream = getContentStream();
        if (contentStream == null) {
            throw new IllegalStateException(
                "No data available to write for install."
                    + "Please ensure that all blobs in the document have non-null content.");
        }

        C4BlobWriteStream blobOut = null;
        try {
            blobOut = store.openWriteStream();

            final byte[] buffer = new byte[MAX_CACHED_CONTENT_LENGTH];
            this.blobLength = 0;

            int n;
            while ((n = contentStream.read(buffer)) >= 0) {
                blobOut.write(buffer, n);
                this.blobLength += n;
            }

            blobOut.install();

            return blobOut.computeBlobKey();
        }
        finally {
            try { contentStream.close(); }
            catch (IOException ignore) { }

            if (blobOut != null) { blobOut.close(); }
        }
    }

    // Cache for later re-use, but only if we can fit the entire contents
    // in a MAX_CACHED_CONTENT_LENGTH-sized buffer.
    private int cacheContent(@NonNull byte[] newContent) {
        final int len = newContent.length;
        if (len <= MAX_CACHED_CONTENT_LENGTH) { content = newContent; }
        return len;
    }

    private byte[] copyBytes(@NonNull byte[] content) {
        final int len = content.length;
        final byte[] copy = new byte[len];
        System.arraycopy(content, 0, copy, 0, len);
        return copy;
    }
}
