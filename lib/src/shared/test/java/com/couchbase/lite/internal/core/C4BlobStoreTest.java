//
// C4BlobStoreTest.java
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
package com.couchbase.lite.internal.core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogLevel;
import com.couchbase.lite.internal.CBLStatus;
import com.couchbase.lite.internal.fleece.FLSliceResult;
import com.couchbase.lite.utils.FileUtils;
import com.couchbase.lite.utils.Report;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


public class C4BlobStoreTest extends C4BaseTest {

    //
    // NOTE: JNI binding does not supports `c4blob_openStore()` with `C4EncryptionKey`
    //

    private File blobDir;
    private C4BlobStore blobStore;
    private C4BlobKey bogusKey;

    @Before
    @Override
    public void setUp() throws CouchbaseLiteException {
        super.setUp();

        blobDir = new File(tmpDir);

        try {
            blobStore = C4BlobStore.open(blobDir.getCanonicalPath(), C4Constants.DatabaseFlags.CREATE);
            bogusKey = new C4BlobKey("sha1-VVVVVVVVVVVVVVVVVVVVVVVVVVU=");
        }
        catch (LiteCoreException e) { throw CBLStatus.convertException(e); }
        catch (IOException e) { throw new IllegalStateException("IO error setting up directories", e); }
    }

    @After
    @Override
    public void tearDown() {
        try {
            if (blobStore != null) {
                final C4BlobStore store = blobStore;
                blobStore = null;

                try { store.delete(); }
                catch (LiteCoreException e) { throw new IllegalStateException("Failed deleting blob store", e); }
                finally { store.free(); }
            }

            if (blobDir != null) { FileUtils.eraseFileOrDir(blobDir); }
        }
        finally { super.tearDown(); }
    }

    // - parse blob keys
    @Test
    public void testParseBlobKeys() throws LiteCoreException {
        C4BlobKey key = new C4BlobKey("sha1-VVVVVVVVVVVVVVVVVVVVVVVVVVU=");
        assertEquals("sha1-VVVVVVVVVVVVVVVVVVVVVVVVVVU=", key.toString());
        key.free();
    }

    // - parse invalid blob keys
    @Test
    public void testParseInvalidBlobKeys() {
        parseInvalidBlobKeys("");
        parseInvalidBlobKeys("rot13-xxxx");
        parseInvalidBlobKeys("sha1-");
        parseInvalidBlobKeys("sha1-VVVVVVVVVVVVVVVVVVVVVV");
        parseInvalidBlobKeys("sha1-VVVVVVVVVVVVVVVVVVVVVVVVVVVVVVU");
    }

    // - missing blobs
    @Test
    public void testMissingBlobs() {
        assertEquals(-1, blobStore.getSize(bogusKey));

        try {
            blobStore.getContents(bogusKey);
            fail();
        }
        catch (LiteCoreException e) {
            assertEquals(e.code, C4Constants.LiteCoreError.NOT_FOUND);
        }
        try {
            blobStore.getFilePath(bogusKey);
            fail();
        }
        catch (LiteCoreException e) {
            assertEquals(e.code, C4Constants.LiteCoreError.NOT_FOUND);
        }
    }

    // - create blobs
    @Test
    public void testCreateBlobs() throws LiteCoreException {
        String blobToStore = "This is a blob to store in the store!";

        // Add blob to the store:
        C4BlobKey key = blobStore.create(blobToStore.getBytes(StandardCharsets.UTF_8));
        assertNotNull(key);
        assertEquals("sha1-QneWo5IYIQ0ZrbCG0hXPGC6jy7E=", key.toString());

        // Read it back and compare
        long blobSize = blobStore.getSize(key);
        assertTrue(blobSize >= blobToStore.getBytes(StandardCharsets.UTF_8).length);
        // TODO: Encryption
        assertEquals(blobToStore.getBytes(StandardCharsets.UTF_8).length, blobSize);

        FLSliceResult res = blobStore.getContents(key);
        assertNotNull(res);
        assertArrayEquals(blobToStore.getBytes(StandardCharsets.UTF_8), res.getBuf());
        assertEquals(blobToStore.getBytes(StandardCharsets.UTF_8).length, res.getBuf().length);
        res.free();

        String p = blobStore.getFilePath(key);
        // TODO: Encryption
        assertNotNull(p);
        String filename = "QneWo5IYIQ0ZrbCG0hXPGC6jy7E=.blob";
        assertEquals(p.length() - filename.length(), p.indexOf(filename));

        // Try storing it again
        C4BlobKey key2 = blobStore.create(blobToStore.getBytes(StandardCharsets.UTF_8));
        assertNotNull(key2);
        assertEquals(key.toString(), key2.toString());

        key.free();
    }

    // - delete blobs
    @Test
    public void testDeleteBlobs() throws LiteCoreException {
        String blobToStore = "This is a blob to store in the store!";

        // Add blob to the store:
        C4BlobKey key = blobStore.create(blobToStore.getBytes(StandardCharsets.UTF_8));
        assertNotNull(key);

        // Delete it
        blobStore.delete(key);

        // Try to read it (should be gone):
        long blobSize = blobStore.getSize(key);
        assertEquals(-1, blobSize);

        try {
            blobStore.getContents(key);
            fail();
        }
        catch (LiteCoreException ex) {
            assertEquals(C4Constants.ErrorDomain.LITE_CORE, ex.domain);
            assertEquals(C4Constants.LiteCoreError.NOT_FOUND, ex.code);
        }

        try {
            blobStore.getFilePath(key);
            fail();
        }
        catch (LiteCoreException ex) {
            assertEquals(C4Constants.ErrorDomain.LITE_CORE, ex.domain);
            assertEquals(C4Constants.LiteCoreError.NOT_FOUND, ex.code);
        }
    }

    // - read blob with stream
    @Test
    public void testReadBlobWithStream() throws LiteCoreException {
        String blob = "This is a blob to store in the store!";

        // Add blob to the store:
        C4BlobKey key = blobStore.create(blob.getBytes(StandardCharsets.UTF_8));
        assertNotNull(key);

        C4BlobReadStream stream = blobStore.openReadStream(key);
        assertNotNull(stream);

        assertEquals(blob.getBytes(StandardCharsets.UTF_8).length, stream.getLength());

        // Read it back, 6 bytes at a time:
        StringBuilder readBack = new StringBuilder();
        byte[] bytes;
        do {
            bytes = stream.read(6);
            readBack.append(new String(bytes));
        }
        while (bytes.length == 6);
        assertEquals(blob, readBack.toString());

        // Try seeking:
        stream.seek(10);
        bytes = stream.read(4);
        assertEquals(4, bytes.length);
        assertEquals("blob", new String(bytes));

        stream.close();
    }

    // - write blob with stream
    @Test
    public void testWriteBlobWithStream() throws LiteCoreException {
        C4BlobWriteStream stream = blobStore.openWriteStream();
        assertNotNull(stream);

        for (int i = 0; i < 1000; i++) {
            stream.write(String.format(Locale.ENGLISH, "This is line %03d.\n", i).getBytes(StandardCharsets.UTF_8));
        }

        // Get the blob key, and install it:
        C4BlobKey key = stream.computeBlobKey();
        assertNotNull(key);
        stream.install();
        stream.close();

        // Read it back using the key:
        FLSliceResult contents = blobStore.getContents(key);
        assertNotNull(contents);
        assertEquals(18000, contents.getSize());
        assertEquals(18000, contents.getBuf().length);
        contents.free();

        // Read it back random-access:
        C4BlobReadStream reader = blobStore.openReadStream(key);
        assertNotNull(reader);
        final int increment = 3 * 3 * 3 * 3;
        int line = increment;
        for (int i = 0; i < 1000; i++) {
            line = (line + increment) % 1000;
            Report.log(LogLevel.VERBOSE, "Reading line " + line + " at offset " + (18 * line));
            String buf = String.format(Locale.ENGLISH, "This is line %03d.\n", line);
            reader.seek(18 * line);
            byte[] readBuf = reader.read(18);
            assertNotNull(readBuf);
            assertEquals(18, readBuf.length);
            assertArrayEquals(readBuf, buf.getBytes(StandardCharsets.UTF_8));
        }
        stream.close();
        reader.close();

        key.free();
    }

    // - write blobs of many sizes
    @Test
    public void testWriteBlobsOfManySizes() throws LiteCoreException {
        // The interesting sizes for encrypted blobs are right around the file block size (4096)
        // and the cipher block size (16).

        List<Integer> kSizes = Arrays.asList(0, 1, 15, 16, 17, 4095, 4096, 4097,
            4096 + 15, 4096 + 16, 4096 + 17, 8191, 8192, 8193);
        for (int size : kSizes) {
            Report.log(LogLevel.INFO, "Testing " + size + "-byte blob");
            // Write the blob:
            C4BlobWriteStream stream = blobStore.openWriteStream();
            assertNotNull(stream);

            String chars = "ABCDEFGHIJKLMNOPQRSTUVWXY";
            for (int i = 0; i < size; i++) {
                int c = i % chars.length();
                stream.write(chars.substring(c, c + 1).getBytes(StandardCharsets.UTF_8));
            }

            // Get the blob key, and install it:
            C4BlobKey key = stream.computeBlobKey();
            stream.install();
            stream.close();

            // Read it back using the key:
            FLSliceResult contents = blobStore.getContents(key);
            assertNotNull(contents);
            assertEquals(size, contents.getSize());
            assertEquals(size, contents.getBuf().length);
            byte[] buf = contents.getBuf();
            for (int i = 0; i < size; i++) {
                assertEquals(chars.substring(i % chars.length(), i % chars.length() + 1)
                    .getBytes(StandardCharsets.UTF_8)[0], buf[i]);
            }
            contents.free();

            key.free();
        }
    }

    // - write blob and cancel
    @Test
    public void testWriteBlobAndCancel() throws LiteCoreException {
        C4BlobWriteStream stream = blobStore.openWriteStream();
        assertNotNull(stream);

        String buf = "This is line oops\n";
        stream.write(buf.getBytes(StandardCharsets.UTF_8));

        stream.close();
    }

    private void parseInvalidBlobKeys(String str) {
        try {
            new C4BlobKey(str);
            fail();
        }
        catch (LiteCoreException ignore) { }
    }
}
