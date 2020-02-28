//
// C4BaseTest.java
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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.After;
import org.junit.Before;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogLevel;
import com.couchbase.lite.PlatformBaseTest;
import com.couchbase.lite.internal.CBLStatus;
import com.couchbase.lite.internal.fleece.FLEncoder;
import com.couchbase.lite.internal.fleece.FLSliceResult;
import com.couchbase.lite.internal.fleece.FLValue;
import com.couchbase.lite.internal.utils.StopWatch;
import com.couchbase.lite.utils.FileUtils;
import com.couchbase.lite.utils.Report;
import com.couchbase.lite.utils.TestUtils;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;


public class C4BaseTest extends PlatformBaseTest {
    public static final String DOC_ID = "mydoc";
    public static final String REV_ID_1 = "1-abcd";
    public static final String REV_ID_2 = "2-c001d00d";
    public static final String REV_ID_3 = "3-deadbeef";

    protected C4Database c4Database;

    protected File dbDir;
    protected String tmpDir;
    protected File rootDir;

    protected byte[] fleeceBody;

    @Before
    @Override
    public void setUp() throws CouchbaseLiteException {
        super.setUp();

        final String tmpDirName = "c4-test-" + TestUtils.randomString(24);

        tmpDir = getScratchDirectoryPath(tmpDirName);
        rootDir = new File(getDatabaseDirectoryPath(), tmpDirName);
        dbDir = new File(rootDir, "cbl_core_test.sqlite3");

        try {
            if (!rootDir.mkdirs()) { throw new IOException("Can't create directory: " + rootDir); }

            C4.setenv("TMPDIR", tmpDir, 1);

            c4Database = new C4Database(
                dbDir.getCanonicalPath(),
                getFlags(),
                null,
                getVersioning(),
                encryptionAlgorithm(),
                encryptionKey());

            Map<String, Object> body = new HashMap<>();
            body.put("ans*wer", 42);
            fleeceBody = createFleeceBody(body);
        }
        catch (LiteCoreException e) { throw CBLStatus.convertException(e); }
        catch (IOException e) { throw new IllegalStateException("IO error setting up directories", e); }
    }

    @After
    @Override
    public void tearDown() {
        try {
            if (c4Database != null) {
                final C4Database db = c4Database;
                c4Database = null;

                try { db.close(); }
                catch (LiteCoreException e) { throw new IllegalStateException("Failed closing db", e); }
                finally { db.free(); }
            }
            if (rootDir != null) { FileUtils.eraseFileOrDir(rootDir); }
            if (tmpDir != null) { FileUtils.eraseFileOrDir(tmpDir); }
        }
        finally { super.tearDown(); }
    }

    protected void createRev(String docID, String revID, byte[] body) throws LiteCoreException {
        createRev(docID, revID, body, 0);
    }

    protected int getFlags() { return C4Constants.DatabaseFlags.CREATE | C4Constants.DatabaseFlags.SHARED_KEYS; }

    protected int getVersioning() { return C4Constants.DocumentVersioning.REVISION_TREES; }

    protected int encryptionAlgorithm() { return C4Constants.EncryptionAlgorithm.NONE; }

    protected byte[] encryptionKey() { return null; }

    protected long importJSONLines(String name) throws LiteCoreException, IOException {
        return importJSONLines(getAsset(name));
    }

    protected long importJSONLines(String name, String idPrefix) throws LiteCoreException, IOException {
        return importJSONLines(getAsset(name), idPrefix);
    }

    protected long importJSONLinesSafely(String name) throws CouchbaseLiteException {
        try { return importJSONLines(name); }
        catch (LiteCoreException e) { throw CBLStatus.convertException(e); }
        catch (IOException e) { throw new IllegalStateException("IO error importing JSON", e); }
    }

    protected void createRev(C4Database db, String docID, String revID, byte[] body)
        throws LiteCoreException {
        createRev(db, docID, revID, body, 0);
    }

    protected void createRev(String docID, String revID, byte[] body, int flags)
        throws LiteCoreException {
        createRev(this.c4Database, docID, revID, body, flags);
    }

    protected void reopenDB() throws LiteCoreException {
        if (c4Database != null) {
            c4Database.close();
            c4Database.free();
            c4Database = null;
        }
        c4Database = new C4Database(
            dbDir.getPath(), getFlags(), null, getVersioning(),
            encryptionAlgorithm(), encryptionKey());
        assertNotNull(c4Database);
    }

    protected void reopenDBReadOnly() throws LiteCoreException {
        if (c4Database != null) {
            c4Database.close();
            c4Database.free();
            c4Database = null;
        }
        int flag = getFlags() & ~C4Constants.DatabaseFlags.CREATE | C4Constants.DatabaseFlags.READ_ONLY;
        c4Database = new C4Database(
            dbDir.getPath(), flag, null, getVersioning(),
            encryptionAlgorithm(), encryptionKey());
        assertNotNull(c4Database);
    }

    protected byte[] json2fleece(String json) throws LiteCoreException {
        boolean commit = false;
        c4Database.beginTransaction();
        FLSliceResult body = null;
        try {
            body = c4Database.encodeJSON(json5(json).getBytes(StandardCharsets.UTF_8));
            byte[] bytes = body.getBuf();
            commit = true;
            return bytes;
        }
        finally {
            if (body != null) { body.free(); }
            c4Database.endTransaction(commit);
        }
    }

    protected String json5(String input) {
        String json = null;
        try {
            json = FLValue.json5ToJson(input);
        }
        catch (LiteCoreException e) {
            fail(e.getMessage());
        }
        assertNotNull(json);
        return json;
    }

    protected List<C4BlobKey> addDocWithAttachments(String docID, List<String> attachments, String contentType)
        throws LiteCoreException {
        List<C4BlobKey> keys = new ArrayList<>();
        StringBuilder json = new StringBuilder();
        json.append("{attached: [");
        for (String attachment : attachments) {
            C4BlobStore store = c4Database.getBlobStore();
            C4BlobKey key = store.create(attachment.getBytes(StandardCharsets.UTF_8));
            keys.add(key);
            String keyStr = key.toString();
            json.append("{'");
            json.append("@type");
            json.append("': '");
            json.append("blob");
            json.append("', ");
            json.append("digest: '");
            json.append(keyStr);
            json.append("', length: ");
            json.append(attachment.length());
            json.append(", content_type: '");
            json.append(contentType);
            json.append("'},");
        }
        json.append("]}");
        String jsonStr = json5(json.toString());
        FLSliceResult body = c4Database.encodeJSON(jsonStr.getBytes(StandardCharsets.UTF_8));

        // Save document:
        C4Document doc
            = c4Database.put(
            body,
            docID,
            C4Constants.RevisionFlags.HAS_ATTACHMENTS,
            false,
            false,
            new String[0],
            true,
            0,
            0);
        assertNotNull(doc);
        body.free();
        doc.free();
        return keys;
    }

    /**
     * @param flags C4RevisionFlags
     */
    private void createRev(C4Database db, String docID, String revID, byte[] body, int flags)
        throws LiteCoreException {
        boolean commit = false;
        db.beginTransaction();
        try {
            C4Document curDoc = db.get(docID, false);
            assertNotNull(curDoc);
            List<String> revIDs = new ArrayList<>();
            revIDs.add(revID);
            if (curDoc.getRevID() != null) { revIDs.add(curDoc.getRevID()); }
            String[] history = revIDs.toArray(new String[0]);
            C4Document doc = db.put(body, docID, flags,
                true, false, history, true,
                0, 0);
            assertNotNull(doc);
            doc.free();
            curDoc.free();
            commit = true;
        }
        finally {
            db.endTransaction(commit);
        }
    }

    private long importJSONLines(InputStream is) throws LiteCoreException, IOException {
        return importJSONLines(is, 120, true);
    }

    private long importJSONLines(InputStream is, String idPrefix) throws LiteCoreException, IOException {
        return importJSONLines(is, idPrefix, 120, true);
    }

    // Read a file that contains a JSON document per line. Every line becomes a document.
    private long importJSONLines(InputStream is, double timeout, boolean verbose)
        throws IOException, LiteCoreException {
        return importJSONLines(is, "", timeout, verbose);
    }

    // Read a file that contains a JSON document per line. Every line becomes a document.
    private long importJSONLines(InputStream is, String idPrefix, double timeout, boolean verbose)
        throws LiteCoreException, IOException {
        StopWatch st = new StopWatch();
        long numDocs = 0;
        boolean commit = false;
        c4Database.beginTransaction();
        try {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    FLSliceResult body = c4Database.encodeJSON(line.getBytes(StandardCharsets.UTF_8));
                    try {
                        String docID = String.format(Locale.ENGLISH, "%s%07d", idPrefix, numDocs + 1);

                        C4Document doc = c4Database.put(body, docID, 0,
                            false, false,
                            new String[0], true, 0, 0);
                        try {
                            assertNotNull(doc);
                        }
                        finally {
                            doc.free();
                        }
                    }
                    finally {
                        body.free();
                    }

                    numDocs++;
                    if (numDocs % 1000 == 0 && st.getElapsedTimeSecs() >= timeout) {
                        String msg = String.format(
                            Locale.ENGLISH, "Stopping JSON import after %.3f sec",
                            st.getElapsedTimeSecs());
                        throw new IOException(msg);
                    }
                    if (verbose && numDocs % 100000 == 0) {
                        Report.log(LogLevel.VERBOSE, String.valueOf(numDocs));
                    }
                }
            }
            commit = true;
        }
        finally {
            c4Database.endTransaction(commit);
        }

        return numDocs;
    }

    private byte[] createFleeceBody(Map<String, Object> body) throws LiteCoreException {
        FLEncoder enc = new FLEncoder();
        try {
            if (body == null) { enc.beginDict(0); }
            else {
                enc.beginDict(body.size());
                for (String key : body.keySet()) {
                    enc.writeKey(key);
                    enc.writeValue(body.get(key));
                }
            }
            enc.endDict();
            return enc.finish();
        }
        finally { enc.free(); }
    }
}
