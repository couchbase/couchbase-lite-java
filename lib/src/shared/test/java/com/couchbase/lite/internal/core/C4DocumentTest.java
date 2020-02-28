//
// C4DocumentTest.java
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

import java.io.IOException;
import java.util.Locale;

import org.junit.Test;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogLevel;
import com.couchbase.lite.internal.utils.StopWatch;
import com.couchbase.lite.utils.Report;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


public class C4DocumentTest extends C4BaseTest {
    interface Verification {
        void verify(C4Document doc) throws LiteCoreException;
    }

    // - "Invalid docID"

    @Test
    public void testInvalidDocIDEmpty() throws LiteCoreException { testInvalidDocID(""); }

    @Test
    public void testInvalidDocIDControlCharacter() throws LiteCoreException { testInvalidDocID("oops\noops"); }

    @Test
    public void testInvalidDocIDTooLong() throws LiteCoreException {
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < 241; i++) { str.append('x'); }
        testInvalidDocID(str.toString());
    }

    // - "FleeceDocs"
    @Test
    public void testFleeceDocs() throws LiteCoreException, IOException { importJSONLines("names_100.json"); }

    // - "Document PossibleAncestors"
    @Test
    public void testPossibleAncestors() throws LiteCoreException {
        createRev(DOC_ID, REV_ID_1, fleeceBody);
        createRev(DOC_ID, REV_ID_2, fleeceBody);
        createRev(DOC_ID, REV_ID_3, fleeceBody);

        C4Document doc = c4Database.get(DOC_ID, true);
        assertNotNull(doc);

        String newRevID = "3-f00f00";
        assertTrue(doc.selectFirstPossibleAncestorOf(newRevID));
        assertEquals(REV_ID_2, doc.getSelectedRevID());
        assertTrue(doc.selectNextPossibleAncestorOf(newRevID));
        assertEquals(REV_ID_1, doc.getSelectedRevID());
        assertFalse(doc.selectNextPossibleAncestorOf(newRevID));

        newRevID = "2-f00f00";
        assertTrue(doc.selectFirstPossibleAncestorOf(newRevID));
        assertEquals(REV_ID_1, doc.getSelectedRevID());
        assertFalse(doc.selectNextPossibleAncestorOf(newRevID));

        newRevID = "1-f00f00";
        assertFalse(doc.selectFirstPossibleAncestorOf(newRevID));

        doc.free();
    }

    // - "Document CreateVersionedDoc"
    @Test
    public void testCreateVersionedDoc() throws LiteCoreException {
        // Try reading doc with mustExist=true, which should fail:
        try {
            C4Document doc = c4Database.get(DOC_ID, true);
            doc.free();
            fail();
        }
        catch (LiteCoreException lce) {
            assertEquals(C4Constants.ErrorDomain.LITE_CORE, lce.domain);
            assertEquals(C4Constants.LiteCoreError.NOT_FOUND, lce.code);
        }

        // Now get the doc with mustExist=false, which returns an empty doc:
        C4Document doc = c4Database.get(DOC_ID, false);
        assertNotNull(doc);
        assertEquals(0, doc.getFlags());
        assertEquals(DOC_ID, doc.getDocID());
        assertNull(doc.getRevID());
        assertNull(doc.getSelectedRevID());
        doc.free();

        boolean commit = false;
        c4Database.beginTransaction();
        try {
            doc = c4Database.put(fleeceBody, DOC_ID, 0, true, false, new String[] {REV_ID_1}, true, 0, 0);
            assertNotNull(doc);
            assertEquals(REV_ID_1, doc.getRevID());
            assertEquals(REV_ID_1, doc.getSelectedRevID());
            assertArrayEquals(fleeceBody, doc.getSelectedBody());
            doc.free();
            commit = true;
        }
        finally {
            c4Database.endTransaction(commit);
        }

        // Reload the doc:
        doc = c4Database.get(DOC_ID, true);
        assertNotNull(doc);
        assertEquals(C4Constants.DocumentFlags.EXISTS, doc.getFlags());
        assertEquals(DOC_ID, doc.getDocID());
        assertEquals(REV_ID_1, doc.getRevID());
        assertEquals(REV_ID_1, doc.getSelectedRevID());
        assertEquals(1, doc.getSelectedSequence());
        assertArrayEquals(fleeceBody, doc.getSelectedBody());

        doc.free();

        // Get the doc by its sequence:
        doc = c4Database.getBySequence(1);
        assertNotNull(doc);
        assertEquals(C4Constants.DocumentFlags.EXISTS, doc.getFlags());
        assertEquals(DOC_ID, doc.getDocID());
        assertEquals(REV_ID_1, doc.getRevID());
        assertEquals(REV_ID_1, doc.getSelectedRevID());
        assertEquals(1, doc.getSelectedSequence());
        assertArrayEquals(fleeceBody, doc.getSelectedBody());

        doc.free();
    }

    // - "Document CreateMultipleRevisions"
    @Test
    public void testCreateMultipleRevisions() throws LiteCoreException {
        byte[] kFleeceBody2 = json2fleece("{'ok':'go'}");
        byte[] kFleeceBody3 = json2fleece("{'ubu':'roi'}");

        createRev(DOC_ID, REV_ID_1, fleeceBody);
        createRev(DOC_ID, REV_ID_2, kFleeceBody2, C4Constants.RevisionFlags.KEEP_BODY);
        createRev(DOC_ID, REV_ID_2, kFleeceBody2); // test redundant insert

        // Reload the doc:
        C4Document doc = c4Database.get(DOC_ID, true);
        assertNotNull(doc);
        assertEquals(C4Constants.DocumentFlags.EXISTS, doc.getFlags());
        assertEquals(DOC_ID, doc.getDocID());
        assertEquals(REV_ID_2, doc.getRevID());
        assertEquals(REV_ID_2, doc.getSelectedRevID());
        assertEquals(2, doc.getSelectedSequence());
        assertArrayEquals(kFleeceBody2, doc.getSelectedBody());

        // Select 1st revision:
        assertTrue(doc.selectParentRevision());
        assertEquals(REV_ID_1, doc.getSelectedRevID());
        assertEquals(1, doc.getSelectedSequence());
        assertNull(doc.getSelectedBody());
        assertFalse(doc.hasRevisionBody());
        assertFalse(doc.selectParentRevision());
        doc.free();

        // Add a 3rd revision:
        createRev(DOC_ID, REV_ID_3, kFleeceBody3);

        // Revision 2 should keep its body due to the kRevKeepBody flag:
        doc = c4Database.get(DOC_ID, true);
        assertNotNull(doc);
        assertTrue(doc.selectParentRevision());
        assertEquals(DOC_ID, doc.getDocID());
        assertEquals(REV_ID_3, doc.getRevID());
        assertEquals(REV_ID_2, doc.getSelectedRevID());
        assertEquals(2, doc.getSelectedSequence());
        assertArrayEquals(kFleeceBody2, doc.getSelectedBody());
        assertEquals(doc.getSelectedFlags(), C4Constants.RevisionFlags.KEEP_BODY);
        doc.free();

        // Purge doc
        boolean commit = false;
        c4Database.beginTransaction();
        try {
            doc = c4Database.get(DOC_ID, true);
            int nPurged = doc.purgeRevision(REV_ID_3);
            assertEquals(3, nPurged);
            doc.save(20);
            commit = true;
        }
        finally {
            c4Database.endTransaction(commit);
        }

        doc.free();
    }

    // - "Document maxRevTreeDepth"
    @Test
    public void testMaxRevTreeDepth() throws LiteCoreException {
        // NOTE: c4db_getMaxRevTreeDepth and c4db_setMaxRevTreeDepth are not supported by JNI.
        assertEquals(20, c4Database.getMaxRevTreeDepth());
        c4Database.setMaxRevTreeDepth(30);
        assertEquals(30, c4Database.getMaxRevTreeDepth());
        reopenDB();
        assertEquals(30, c4Database.getMaxRevTreeDepth());

        final int kNumRevs = 10000;
        StopWatch st = new StopWatch();
        C4Document doc = c4Database.get(DOC_ID, false);
        assertNotNull(doc);
        boolean commit = false;
        c4Database.beginTransaction();
        try {
            for (int i = 0; i < kNumRevs; i++) {
                String[] history = {doc.getRevID()};
                C4Document savedDoc = c4Database.put(fleeceBody, doc.getDocID(), 0, false, false, history, true, 30, 0);
                assertNotNull(savedDoc);
                doc.free();
                doc = savedDoc;
            }
            commit = true;
        }
        finally {
            c4Database.endTransaction(commit);
        }
        Report.log(
            LogLevel.INFO,
            String.format(Locale.ENGLISH, "Created %d revisions in %.3f ms", kNumRevs, st.getElapsedTimeMillis()));

        // Check rev tree depth:
        int nRevs = 0;
        assertTrue(doc.selectCurrentRevision());
        do { nRevs++; }
        while (doc.selectParentRevision());
        Report.log(LogLevel.INFO, String.format(Locale.ENGLISH, "Document rev tree depth is %d", nRevs));
        assertEquals(30, nRevs);
        doc.free();
    }

    // - "Document Put"
    @Test
    public void testPut() throws LiteCoreException {
        boolean commit = false;
        c4Database.beginTransaction();
        try {
            // Creating doc given ID:
            C4Document doc = c4Database.put(fleeceBody, DOC_ID, 0, false, false, new String[0], true, 0, 0);
            assertNotNull(doc);
            assertEquals(DOC_ID, doc.getDocID());
            String kExpectedRevID = "1-042ca1d3a1d16fd5ab2f87efc7ebbf50b7498032";
            assertEquals(kExpectedRevID, doc.getRevID());
            assertEquals(C4Constants.DocumentFlags.EXISTS, doc.getFlags());
            assertEquals(kExpectedRevID, doc.getSelectedRevID());
            doc.free();

            // Update doc:
            String[] history = {kExpectedRevID};

            doc = c4Database.put(json2fleece("{'ok':'go'}"), DOC_ID, 0, false, false, history, true, 0, 0);
            assertNotNull(doc);
            // NOTE: With current JNI binding, unable to check commonAncestorIndex value
            String kExpectedRevID2 = "2-201796aeeaa6ddbb746d6cab141440f23412ac51";
            assertEquals(kExpectedRevID2, doc.getRevID());
            assertEquals(C4Constants.DocumentFlags.EXISTS, doc.getFlags());
            assertEquals(kExpectedRevID2, doc.getSelectedRevID());
            doc.free();

            // Insert existing rev that conflicts:
            String kConflictRevID = "2-deadbeef";
            String[] history2 = {kConflictRevID, kExpectedRevID};
            doc = c4Database.put(json2fleece("{'from':'elsewhere'}"), DOC_ID, 0, true, true, history2, true, 0, 1);
            assertNotNull(doc);
            // NOTE: With current JNI binding, unable to check commonAncestorIndex value
            assertEquals(kExpectedRevID2, doc.getRevID());
            assertEquals(C4Constants.DocumentFlags.EXISTS | C4Constants.DocumentFlags.CONFLICTED, doc.getFlags());
            assertEquals(kConflictRevID, doc.getSelectedRevID());
            doc.free();

            commit = true;
        }
        finally {
            c4Database.endTransaction(commit);
        }
    }

    // - "Document Update"
    @Test
    public void testDocumentUpdate() throws LiteCoreException {
        C4Document doc;

        boolean commit = false;
        c4Database.beginTransaction();
        try {
            doc = c4Database.create(DOC_ID, fleeceBody, 0);
            assertNotNull(doc);
            commit = true;
        }
        finally {
            c4Database.endTransaction(commit);
        }

        String kExpectedRevID = "1-042ca1d3a1d16fd5ab2f87efc7ebbf50b7498032";
        assertEquals(kExpectedRevID, doc.getRevID());
        assertTrue(doc.exists());
        assertEquals(kExpectedRevID, doc.getSelectedRevID());
        assertEquals(DOC_ID, doc.getDocID());

        // Read the doc into another C4Document:
        C4Document doc2 = c4Database.get(DOC_ID, false);
        assertNotNull(doc2);
        assertEquals(kExpectedRevID, doc2.getRevID());

        commit = false;
        c4Database.beginTransaction();
        try {
            C4Document updatedDoc = doc.update(json2fleece("{'ok':'go'}"), 0);
            assertNotNull(updatedDoc);
            assertEquals(kExpectedRevID, doc.getSelectedRevID());
            assertEquals(kExpectedRevID, doc.getRevID());
            doc.free();
            doc = updatedDoc;
            commit = true;
        }
        finally {
            c4Database.endTransaction(commit);
        }

        String kExpectedRev2ID = "2-201796aeeaa6ddbb746d6cab141440f23412ac51";
        assertEquals(kExpectedRev2ID, doc.getRevID());
        assertTrue(doc.exists());
        assertEquals(kExpectedRev2ID, doc.getSelectedRevID());
        assertEquals(DOC_ID, doc.getDocID());

        // Now try to update the other C4Document, which will fail:
        c4Database.beginTransaction();
        try {
            doc2.update(json2fleece("{'ok':'no way'}"), 0);
            fail();
        }
        catch (LiteCoreException e) {
            assertEquals(C4Constants.ErrorDomain.LITE_CORE, e.domain);
            assertEquals(C4Constants.LiteCoreError.CONFLICT, e.code);
        }
        finally {
            c4Database.endTransaction(false);
        }

        // Try to create a new doc with the same ID, which will fail:
        c4Database.beginTransaction();
        try {
            c4Database.create(DOC_ID, json2fleece("{'ok':'no way'}"), 0);
            fail();
        }
        catch (LiteCoreException e) {
            assertEquals(C4Constants.ErrorDomain.LITE_CORE, e.domain);
            assertEquals(C4Constants.LiteCoreError.CONFLICT, e.code);
        }
        finally {
            c4Database.endTransaction(false);
        }

        doc.free();
        doc2.free();
    }

    // - "Document Conflict"

    @Test
    public void testDocumentConflictMerge4Win() throws LiteCoreException {
        final byte[] mergedBody = json2fleece("{'merged':true}");
        testDocumentConflict(doc -> {
            doc.resolveConflict("4-dddd", "3-aaaaaa", mergedBody, 0);
            assertTrue(doc.selectCurrentRevision());
            assertEquals("5-8647a1d644ddc7addc279d8cbfe74978b68f067b", doc.getSelectedRevID());
            assertArrayEquals(mergedBody, doc.getSelectedBody());
            assertTrue(doc.selectParentRevision());
            assertEquals("4-dddd", doc.getSelectedRevID());
        });
    }

    @Test
    public void testDocumentConflictMerge3Win() throws LiteCoreException {
        final byte[] mergedBody = json2fleece("{'merged':true}");
        testDocumentConflict(doc -> {
            doc.resolveConflict("3-aaaaaa", "4-dddd", mergedBody, 0);
            assertTrue(doc.selectCurrentRevision());
            assertEquals("4-d204defb3e1b28f0ecd78591ee04b6c1d109cb5c", doc.getSelectedRevID());
            assertArrayEquals(mergedBody, doc.getSelectedBody());
            assertTrue(doc.selectParentRevision());
            assertEquals("3-aaaaaa", doc.getSelectedRevID());
        });
    }

    private void testDocumentConflict(Verification verification) throws LiteCoreException {
        final byte[] kFleeceBody2 = json2fleece("{'ok':'go'}");
        final byte[] kFleeceBody3 = json2fleece("{'ubu':'roi'}");

        createRev(DOC_ID, REV_ID_1, fleeceBody);
        createRev(DOC_ID, REV_ID_2, kFleeceBody2, C4Constants.RevisionFlags.KEEP_BODY);
        createRev(DOC_ID, "3-aaaaaa", kFleeceBody3);

        boolean commit = false;
        c4Database.beginTransaction();
        try {
            // "Pull" a conflicting revision:
            String[] history = {"4-dddd", "3-ababab", REV_ID_2};
            C4Document doc = c4Database.put(kFleeceBody3, DOC_ID, 0, true,
                true, history, true, 0, 0);
            assertNotNull(doc);

            // Now check the common ancestor algorithm:
            assertTrue(doc.selectCommonAncestorRevision("3-aaaaaa", "4-dddd"));
            assertEquals(REV_ID_2, doc.getSelectedRevID());

            assertTrue(doc.selectCommonAncestorRevision("4-dddd", "3-aaaaaa"));
            assertEquals(REV_ID_2, doc.getSelectedRevID());

            assertTrue(doc.selectCommonAncestorRevision("3-ababab", "3-aaaaaa"));
            assertEquals(REV_ID_2, doc.getSelectedRevID());

            assertTrue(doc.selectCommonAncestorRevision("3-aaaaaa", "3-ababab"));
            assertEquals(REV_ID_2, doc.getSelectedRevID());

            assertTrue(doc.selectCommonAncestorRevision(REV_ID_2, "3-aaaaaa"));
            assertEquals(REV_ID_2, doc.getSelectedRevID());

            assertTrue(doc.selectCommonAncestorRevision("3-aaaaaa", REV_ID_2));
            assertEquals(REV_ID_2, doc.getSelectedRevID());

            assertTrue(doc.selectCommonAncestorRevision(REV_ID_2, REV_ID_2));
            assertEquals(REV_ID_2, doc.getSelectedRevID());

            verification.verify(doc);

            doc.free();
            commit = true;
        }
        finally {
            c4Database.endTransaction(commit);
        }
    }

    private void testInvalidDocID(String docID) throws LiteCoreException {
        c4Database.beginTransaction();
        try {
            c4Database.put(fleeceBody, docID, 0, false, false,
                new String[0], true, 0, 0);
            fail();
        }
        catch (LiteCoreException e) {
            assertEquals(C4Constants.ErrorDomain.LITE_CORE, e.domain);
            assertEquals(C4Constants.LiteCoreError.BAD_DOC_ID, e.code);
        }
        finally {
            c4Database.endTransaction(false);
        }
    }
}
