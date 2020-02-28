

//
// Copyright (c) 2020 Couchbase, Inc All rights reserved.
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;

import com.couchbase.lite.internal.utils.DateUtils;
import com.couchbase.lite.internal.utils.JsonUtils;
import com.couchbase.lite.utils.Fn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


public abstract class BaseDbTest extends BaseTest {
    @FunctionalInterface
    public interface DocValidator extends Fn.ConsumerThrows<Document, CouchbaseLiteException> {}

    protected Database baseTestDb;

    @Before
    @Override
    public void setUp() throws CouchbaseLiteException {
        super.setUp();

        baseTestDb = createDb();

        assertNotNull(baseTestDb);
        assertTrue(baseTestDb.isOpen());
    }

    @After
    @Override
    public void tearDown() {
        try { deleteDb(baseTestDb); }
        finally { super.tearDown(); }
    }

    protected final void reopenBaseTestDb() throws CouchbaseLiteException { baseTestDb = reopenDb(baseTestDb); }

    protected final void recreateBastTestDb() throws CouchbaseLiteException { baseTestDb = recreateDb(baseTestDb); }

    protected final Document createDocInBaseTestDb(String docID) throws CouchbaseLiteException {
        final long n = baseTestDb.getCount() + 1;

        MutableDocument doc = new MutableDocument(docID);
        doc.setValue("key", 1);
        saveDocInBaseTestDb(doc);
        assertEquals(n, baseTestDb.getCount());

        Document savedDoc = baseTestDb.getDocument(docID);
        assertEquals(1, savedDoc.getSequence());

        return savedDoc;
    }

    protected final Document saveDocInBaseTestDb(MutableDocument doc) throws CouchbaseLiteException {
        baseTestDb.save(doc);

        Document savedDoc = baseTestDb.getDocument(doc.getId());
        assertNotNull(savedDoc);
        assertEquals(doc.getId(), savedDoc.getId());

        return savedDoc;
    }

    protected final Document saveDocInBaseTestDb(MutableDocument doc, DocValidator validator)
        throws CouchbaseLiteException {
        validator.accept(doc);

        Document savedDoc = saveDocInBaseTestDb(doc);
        validator.accept(doc);
        validator.accept(savedDoc);

        return savedDoc;
    }

    // used from other package's tests
    protected final void populateData(MutableDocument doc) {
        doc.setValue("true", true);
        doc.setValue("false", false);
        doc.setValue("string", "string");
        doc.setValue("zero", 0);
        doc.setValue("one", 1);
        doc.setValue("minus_one", -1);
        doc.setValue("one_dot_one", 1.1);
        doc.setValue("date", DateUtils.fromJson(TEST_DATE));
        doc.setValue("null", null);

        // Dictionary:
        MutableDictionary dict = new MutableDictionary();
        dict.setValue("street", "1 Main street");
        dict.setValue("city", "Mountain View");
        dict.setValue("state", "CA");
        doc.setValue("dict", dict);

        // Array:
        MutableArray array = new MutableArray();
        array.addValue("650-123-0001");
        array.addValue("650-123-0002");
        doc.setValue("array", array);

        // Blob:
        doc.setValue("blob", new Blob("text/plain", BLOB_CONTENT.getBytes(StandardCharsets.UTF_8)));
    }

    // used from other package's tests
    protected final void populateDataByTypedSetter(MutableDocument doc) {
        doc.setBoolean("true", true);
        doc.setBoolean("false", false);
        doc.setString("string", "string");
        doc.setNumber("zero", 0);
        doc.setInt("one", 1);
        doc.setLong("minus_one", -1);
        doc.setDouble("one_dot_one", 1.1);
        doc.setDate("date", DateUtils.fromJson(TEST_DATE));
        doc.setString("null", null);

        // Dictionary:
        MutableDictionary dict = new MutableDictionary();
        dict.setString("street", "1 Main street");
        dict.setString("city", "Mountain View");
        dict.setString("state", "CA");
        doc.setDictionary("dict", dict);

        // Array:
        MutableArray array = new MutableArray();
        array.addString("650-123-0001");
        array.addString("650-123-0002");
        doc.setArray("array", array);

        // Blob:
        doc.setValue("blob", new Blob("text/plain", BLOB_CONTENT.getBytes(StandardCharsets.UTF_8)));
    }

    protected final void loadJSONResource(String name) throws IOException, JSONException, CouchbaseLiteException {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(getAsset(name)))) {
            int n = 1;
            String line;
            while ((line = in.readLine()) != null) {
                if (line.trim().isEmpty()) { continue; }

                MutableDocument doc = new MutableDocument(String.format(Locale.ENGLISH, "doc-%03d", n++));
                doc.setData(JsonUtils.fromJson(new JSONObject(line)));

                saveDocInBaseTestDb(doc);
            }
        }
    }
}
