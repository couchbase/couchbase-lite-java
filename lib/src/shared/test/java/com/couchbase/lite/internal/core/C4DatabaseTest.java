//
// C4DatabaseTest.java
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.couchbase.lite.utils.FileUtils;
import org.junit.Test;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.utils.TestUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


/**
 * Ported from c4DatabaseTest.cc
 */
public class C4DatabaseTest extends C4BaseTest {

    static C4Document nextDocument(C4DocEnumerator e) throws LiteCoreException {
        return e.next() ? e.getDocument() : null;
    }

    // - "Database ErrorMessages"
    @Test
    public void testDatabaseErrorMessages() {
        try {
            new C4Database("", 0, null, 0, 0, null);
            fail();
        }
        catch (LiteCoreException e) {
            assertEquals(C4Constants.ErrorDomain.LITE_CORE, e.domain);
            assertEquals(C4Constants.LiteCoreError.WRONG_FORMAT, e.code);
            assertEquals("file/data is not in the requested format", e.getMessage());
        }

        try {
            c4Database.get("a", true);
            fail();
        }
        catch (LiteCoreException e) {
            assertEquals(C4Constants.ErrorDomain.LITE_CORE, e.domain);
            assertEquals(C4Constants.LiteCoreError.NOT_FOUND, e.code);
            assertEquals("not found", e.getMessage());
        }

        try {
            c4Database.get(null, true);
            fail();
        }
        catch (LiteCoreException e) {
            assertEquals(C4Constants.ErrorDomain.LITE_CORE, e.domain);
            assertEquals(C4Constants.LiteCoreError.NOT_FOUND, e.code);
            assertEquals("not found", e.getMessage());
        }

        // NOTE: c4error_getMessage() is not supported by Java
    }

    // - "Database Info"
    @Test
    public void testDatabaseInfo() throws LiteCoreException {
        assertEquals(0, c4Database.getDocumentCount());
        assertEquals(0, c4Database.getLastSequence());

        byte[] publicUUID = c4Database.getPublicUUID();
        assertNotNull(publicUUID);
        assertTrue(publicUUID.length > 0);
        // Weird requirements of UUIDs according to the spec:
        assertTrue((publicUUID[6] & 0xF0) == 0x40);
        assertTrue((publicUUID[8] & 0xC0) == 0x80);
        byte[] privateUUID = c4Database.getPrivateUUID();
        assertNotNull(privateUUID);
        assertTrue(privateUUID.length > 0);
        assertTrue((privateUUID[6] & 0xF0) == 0x40);
        assertTrue((privateUUID[8] & 0xC0) == 0x80);
        assertFalse(Arrays.equals(publicUUID, privateUUID));

        reopenDB();

        // Make sure UUIDs are persistent:
        byte[] publicUUID2 = c4Database.getPublicUUID();
        byte[] privateUUID2 = c4Database.getPrivateUUID();
        assertTrue(Arrays.equals(publicUUID, publicUUID2));
        assertTrue(Arrays.equals(privateUUID, privateUUID2));
    }

    // - Database deletion lock
    @Test
    public void testDatabaseDeletionLock() throws IOException {
        try {
            C4Database.deleteDbAtPath(c4Database.getPath());
            fail();
        }
        catch (LiteCoreException e) {
            assertEquals(C4Constants.ErrorDomain.LITE_CORE, e.domain);
            assertEquals(C4Constants.LiteCoreError.BUSY, e.code);
        }

        try {
            C4Database.deleteDbAtPath(dbDir.getCanonicalPath());
            fail();
        }
        catch (LiteCoreException e) {
            assertEquals(C4Constants.ErrorDomain.LITE_CORE, e.domain);
            assertEquals(C4Constants.LiteCoreError.BUSY, e.code);
        }
    }

    // - Database Read-Only UUIDs
    @Test
    public void testDatabaseReadOnlyUUIDs() throws LiteCoreException {
        // Make sure UUIDs are available even if the db is opened read-only when they're first accessed.
        reopenDBReadOnly();
        assertNotNull(c4Database.getPublicUUID());
        assertNotNull(c4Database.getPrivateUUID());
    }

    // - "Database OpenBundle"
    @Test
    public void testDatabaseOpenBundle() throws LiteCoreException, IOException {
        int flags = getFlags();
        File bundlePath = new File(getScratchDirectoryPath("cbl_core_test_bundle"));
        if (bundlePath.exists()) { C4Database.deleteDbAtPath(bundlePath.getPath()); }

        C4Database bundle = new C4Database(
            bundlePath.getPath(),
            flags,
            null,
            getVersioning(),
            encryptionAlgorithm(),
            encryptionKey());

        assertNotNull(bundle);
        bundle.close();
        bundle.free();

        // Reopen without 'create' flag:
        flags &= ~C4Constants.DatabaseFlags.CREATE;
        bundle = new C4Database(
            bundlePath.getPath(),
            flags,
            null,
            getVersioning(),
            encryptionAlgorithm(),
            encryptionKey());
        assertNotNull(bundle);
        bundle.close();
        bundle.free();

        FileUtils.eraseFileOrDir(bundlePath);

        // Reopen with wrong storage type:
        // NOTE: Not supported

        // Open nonexistent bundle:
        try {
            String notExist = new File(getScratchDirectoryPath("bogus"), "no_such_bundle").getCanonicalPath();
            new C4Database(notExist, flags, null, getVersioning(), encryptionAlgorithm(), encryptionKey());
            fail();
        }
        catch (LiteCoreException e) {
            assertEquals(C4Constants.ErrorDomain.LITE_CORE, e.domain);
            assertEquals(C4Constants.LiteCoreError.NOT_FOUND, e.code);
        }
    }

    // - "Database CreateRawDoc"
    @Test
    public void testDatabaseCreateRawDoc() throws LiteCoreException {
        final String store = "test";
        final String key = "key";
        final String meta = "meta";
        boolean commit = false;
        c4Database.beginTransaction();
        try {
            c4Database.rawPut(store, key, meta, fleeceBody);
            commit = true;
        }
        finally {
            c4Database.endTransaction(commit);
        }

        C4RawDocument doc = c4Database.rawGet(store, key);
        assertNotNull(doc);
        assertEquals(doc.key(), key);
        assertEquals(doc.meta(), meta);
        assertTrue(Arrays.equals(doc.body(), fleeceBody));
        doc.free();

        // Nonexistent:
        try {
            c4Database.rawGet(store, "bogus");
            fail("Should not come here.");
        }
        catch (LiteCoreException ex) {
            assertEquals(C4Constants.ErrorDomain.LITE_CORE, ex.domain);
            assertEquals(C4Constants.LiteCoreError.NOT_FOUND, ex.code);
        }
    }

    // - "Database AllDocs"
    @Test
    public void testDatabaseAllDocs() throws LiteCoreException {
        setupAllDocs();

        assertEquals(99, c4Database.getDocumentCount());

        C4Document doc;
        int i;

        // No start or end ID:
        int iteratorFlags = C4Constants.EnumeratorFlags.DEFAULT;
        iteratorFlags &= ~C4Constants.EnumeratorFlags.INCLUDE_BODIES;
        C4DocEnumerator e = c4Database.enumerateAllDocs(iteratorFlags);
        assertNotNull(e);
        try {
            i = 1;
            while (e.next()) {
                assertNotNull(doc = e.getDocument());
                try {
                    String docID = String.format(Locale.ENGLISH, "doc-%03d", i);
                    assertEquals(docID, doc.getDocID());
                    assertEquals(REV_ID_1, doc.getRevID());
                    assertEquals(REV_ID_1, doc.getSelectedRevID());
                    assertEquals(i, doc.getSelectedSequence());
                    assertNull(doc.getSelectedBody());
                    // Doc was loaded without its body, but it should load on demand:
                    doc.loadRevisionBody();
                    assertTrue(Arrays.equals(fleeceBody, doc.getSelectedBody()));
                    i++;
                }
                finally {
                    doc.free();
                }
            }
            assertEquals(100, i);
        }
        finally {
            e.free();
        }
    }

    // - "Database AllDocsInfo"
    @Test
    public void testAllDocsInfo() throws LiteCoreException {
        setupAllDocs();

        // No start or end ID:
        int iteratorFlags = C4Constants.EnumeratorFlags.DEFAULT;
        C4DocEnumerator e = c4Database.enumerateAllDocs(iteratorFlags);
        assertNotNull(e);
        try {
            C4Document doc;
            int i = 1;
            while ((doc = nextDocument(e)) != null) {
                try {
                    String docID = String.format(Locale.ENGLISH, "doc-%03d", i);
                    assertEquals(docID, doc.getDocID());
                    assertEquals(REV_ID_1, doc.getRevID());
                    assertEquals(REV_ID_1, doc.getSelectedRevID());
                    assertEquals(i, doc.getSequence());
                    assertEquals(i, doc.getSelectedSequence());
                    assertEquals(C4Constants.DocumentFlags.EXISTS, doc.getFlags());
                    i++;
                }
                finally {
                    doc.free();
                }
            }
            assertEquals(100, i);
        }
        finally {
            e.free();
        }

    }

    // - "Database Changes"
    @Test
    public void testDatabaseChanges() throws LiteCoreException {
        for (int i = 1; i < 100; i++) {
            String docID = String.format(Locale.ENGLISH, "doc-%03d", i);
            createRev(docID, REV_ID_1, fleeceBody);
        }

        C4Document doc;
        long seq;

        // Since start:
        int iteratorFlags = C4Constants.EnumeratorFlags.DEFAULT;
        iteratorFlags &= ~C4Constants.EnumeratorFlags.INCLUDE_BODIES;
        C4DocEnumerator e = c4Database.enumerateChanges(0, iteratorFlags);
        assertNotNull(e);
        try {
            seq = 1;
            while ((doc = nextDocument(e)) != null) {
                try {
                    String docID = String.format(Locale.ENGLISH, "doc-%03d", seq);
                    assertEquals(docID, doc.getDocID());
                    assertEquals(seq, doc.getSelectedSequence());
                    seq++;
                }
                finally {
                    doc.free();
                }
            }
            assertEquals(100L, seq);
        }
        finally {
            e.free();
        }

        // Since 6:
        e = c4Database.enumerateChanges(6, iteratorFlags);
        assertNotNull(e);
        try {
            seq = 7;
            while ((doc = nextDocument(e)) != null) {
                try {
                    String docID = String.format(Locale.ENGLISH, "doc-%03d", seq);
                    assertEquals(docID, doc.getDocID());
                    assertEquals(seq, doc.getSelectedSequence());
                    seq++;
                }
                finally {
                    doc.free();
                }
            }
            assertEquals(100L, seq);
        }
        finally {
            e.free();
        }

    }

    // - "Database Expired"
    @Test
    public void testDatabaseExpired() throws LiteCoreException {
        String docID = "expire_me";
        createRev(docID, REV_ID_1, fleeceBody);

        // unix time
        long expire = System.currentTimeMillis() / 1000 + 1;
        c4Database.setExpiration(docID, expire);

        expire = System.currentTimeMillis() / 1000 + 2;
        c4Database.setExpiration(docID, expire);
        c4Database.setExpiration(docID, expire);

        String docID2 = "expire_me_too";
        createRev(docID2, REV_ID_1, fleeceBody);
        c4Database.setExpiration(docID2, expire);

        String docID3 = "dont_expire_me";
        createRev(docID3, REV_ID_1, fleeceBody);
        try {
            Thread.sleep(2 * 1000); // sleep 2 sec
        }
        catch (InterruptedException e) {
        }

        assertEquals(expire, c4Database.getExpiration(docID));
        assertEquals(expire, c4Database.getExpiration(docID2));
        assertEquals(expire, c4Database.nextDocExpiration());
    }

    @Test
    public void testPurgeExpiredDocs() throws LiteCoreException {
        String docID = "expire_me";
        createRev(docID, REV_ID_1, fleeceBody);

        // unix time
        long expire = System.currentTimeMillis() / 1000 + 1;
        c4Database.setExpiration(docID, expire);

        expire = System.currentTimeMillis() / 1000 + 2;
        c4Database.setExpiration(docID, expire);

        String docID2 = "expire_me_too";
        createRev(docID2, REV_ID_1, fleeceBody);
        c4Database.setExpiration(docID2, expire);

        try {
            Thread.sleep(3 * 1000); // sleep 3 sec
        }
        catch (InterruptedException e) {
        }

        int cnt = c4Database.purgeExpiredDocs();

        assertEquals(cnt, 2);
    }

    @Test
    public void testPurgeDoc() throws LiteCoreException {
        String docID = "purge_me";
        createRev(docID, REV_ID_1, fleeceBody);
        try {
            c4Database.purgeDoc(docID);
        }
        catch (Exception e) {}
        try {
            c4Database.get(docID, true);
        }
        catch (LiteCoreException e) {
            assertEquals(C4Constants.ErrorDomain.LITE_CORE, e.domain);
            assertEquals(C4Constants.LiteCoreError.NOT_FOUND, e.code);
            assertEquals("not found", e.getMessage());
        }
    }

    // - "Database CancelExpire"
    @Test
    public void testDatabaseCancelExpire() throws LiteCoreException {
        String docID = "expire_me";
        createRev(docID, REV_ID_1, fleeceBody);

        // unix time
        long expire = System.currentTimeMillis() / 1000 + 2;
        c4Database.setExpiration(docID, expire);
        c4Database.setExpiration(docID, 0);

        try {
            Thread.sleep(2 * 1000); // sleep 2 sec
        }
        catch (InterruptedException e) {
        }

        assertNotNull(c4Database.get(docID, true));
    }

    // - "Database BlobStore"
    @Test
    public void testDatabaseBlobStore() throws LiteCoreException {
        C4BlobStore blobs = c4Database.getBlobStore();
        assertNotNull(blobs);
        // NOTE: BlobStore is from the database. Not necessary to call free()?
    }

    // - "Database Compact"
    @Test
    public void testDatabaseCompact() throws LiteCoreException {
        String doc1ID = "doc001";
        String doc2ID = "doc002";
        String doc3ID = "doc003";
        String doc4ID = "doc004";
        String content1 = "This is the first attachment";
        String content2 = "This is the second attachment";
        String content3 = "This is the third attachment";

        C4BlobKey key1, key2, key3;
        List<String> atts = new ArrayList<>();
        c4Database.beginTransaction();
        try {
            atts.add(content1);
            key1 = addDocWithAttachments(doc1ID, atts, "text/plain").get(0);

            atts.clear();
            atts.add(content2);
            key2 = addDocWithAttachments(doc2ID, atts, "text/plain").get(0);

            addDocWithAttachments(doc4ID, atts, "text/plain");

            atts.clear();
            atts.add(content3);
            key3 = addDocWithAttachments(doc3ID, atts, "text/plain")
                .get(0); // legacy: TODO need to implement legacy support
        }
        finally {
            c4Database.endTransaction(true);
        }

        C4BlobStore store = c4Database.getBlobStore();
        assertNotNull(store);
        c4Database.compact();
        assertTrue(store.getSize(key1) > 0);
        assertTrue(store.getSize(key2) > 0);
        assertTrue(store.getSize(key3) > 0);

        // Only reference to first blob is gone
        createRev(doc1ID, REV_ID_2, null, C4Constants.DocumentFlags.DELETED);
        c4Database.compact();
        assertTrue(store.getSize(key1) == -1);
        assertTrue(store.getSize(key2) > 0);
        assertTrue(store.getSize(key3) > 0);

        // Two references exist to the second blob, so it should still
        // exist after deleting doc002
        createRev(doc2ID, REV_ID_2, null, C4Constants.DocumentFlags.DELETED);
        c4Database.compact();
        assertTrue(store.getSize(key1) == -1);
        assertTrue(store.getSize(key2) > 0);
        assertTrue(store.getSize(key3) > 0);

        // After deleting doc4 both blobs should be gone
        createRev(doc4ID, REV_ID_2, null, C4Constants.DocumentFlags.DELETED);
        c4Database.compact();
        assertTrue(store.getSize(key1) == -1);
        assertTrue(store.getSize(key2) == -1);
        assertTrue(store.getSize(key3) > 0);

        // Delete doc with legacy attachment, and it too will be gone
        createRev(doc3ID, REV_ID_2, null, C4Constants.DocumentFlags.DELETED);
        c4Database.compact();
        assertTrue(store.getSize(key1) == -1);
        assertTrue(store.getSize(key2) == -1);
        assertTrue(store.getSize(key3) == -1);
    }

    // - "Database copy"
    @Test
    public void testDatabaseCopy() throws LiteCoreException, IOException {
        String doc1ID = "doc001";
        String doc2ID = "doc002";

        createRev(doc1ID, REV_ID_1, fleeceBody);
        createRev(doc2ID, REV_ID_1, fleeceBody);

        String srcPath = c4Database.getPath();

        final String dbName = TestUtils.randomString(24) + DB_EXTENSION;

        File nuPath = new File(getScratchDirectoryPath(dbName));
        try { C4Database.deleteDbAtPath(nuPath.getCanonicalPath()); }
        catch (LiteCoreException e) { assertEquals(0, e.code); }

        C4Database.copyDb(
            srcPath,
            nuPath.getCanonicalPath(),
            getFlags(),
            null,
            C4Constants.DocumentVersioning.REVISION_TREES,
            C4Constants.EncryptionAlgorithm.NONE,
            null);

        C4Database nudb = new C4Database(
            nuPath.getCanonicalPath(),
            getFlags(),
            null,
            C4Constants.DocumentVersioning.REVISION_TREES,
            C4Constants.EncryptionAlgorithm.NONE,
            null);
        assertNotNull(nudb);
        assertEquals(2, nudb.getDocumentCount());
        nudb.delete();

        nudb = new C4Database(
            nuPath.getCanonicalPath(),
            getFlags(),
            null,
            C4Constants.DocumentVersioning.REVISION_TREES,
            C4Constants.EncryptionAlgorithm.NONE,
            null);
        assertNotNull(nudb);
        createRev(nudb, doc1ID, REV_ID_1, fleeceBody);
        assertEquals(1, nudb.getDocumentCount());
        nudb.close();

        String originalDest = nuPath.getCanonicalPath();

        nuPath = new File(new File(getScratchDirectoryPath("bogus"), "zqx3"), dbName);
        try {
            C4Database.copyDb(
                srcPath,
                nuPath.getCanonicalPath(),
                getFlags(),
                null,
                C4Constants.DocumentVersioning.REVISION_TREES,
                C4Constants.EncryptionAlgorithm.NONE,
                null);
            fail("expected call to c4db_copy to throw an exception");
        }
        catch (LiteCoreException ex) {
            assertEquals(C4Constants.LiteCoreError.NOT_FOUND, ex.code);
        }

        nudb = new C4Database(
            originalDest,
            getFlags(),
            null,
            C4Constants.DocumentVersioning.REVISION_TREES,
            C4Constants.EncryptionAlgorithm.NONE,
            null);
        assertNotNull(nudb);
        assertEquals(1, nudb.getDocumentCount());
        nudb.close();

        srcPath += "bogus" + File.separator;
        nuPath = new File(originalDest);
        try {
            // call to c4db_copy will internally throw an exception
            C4Database.copyDb(srcPath, nuPath.getCanonicalPath(),
                getFlags(),
                null,
                C4Constants.DocumentVersioning.REVISION_TREES,
                C4Constants.EncryptionAlgorithm.NONE,
                null);
            fail();
        }
        catch (LiteCoreException ex) {
            assertEquals(C4Constants.LiteCoreError.NOT_FOUND, ex.code);
        }

        nudb = new C4Database(
            originalDest,
            getFlags(),
            null,
            C4Constants.DocumentVersioning.REVISION_TREES,
            C4Constants.EncryptionAlgorithm.NONE,
            null);
        assertNotNull(nudb);
        assertEquals(1, nudb.getDocumentCount());

        nudb.delete();
    }

    private void setupAllDocs() throws LiteCoreException {
        for (int i = 1; i < 100; i++) {
            String docID = String.format(Locale.ENGLISH, "doc-%03d", i);
            createRev(docID, REV_ID_1, fleeceBody);
        }

        // Add a deleted doc to make sure it's skipped by default:
        createRev("doc-005DEL", REV_ID_1, null, C4Constants.DocumentFlags.DELETED);
    }
}
