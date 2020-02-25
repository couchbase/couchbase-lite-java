

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
import java.io.InputStreamReader;
import java.util.Locale;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;

import com.couchbase.lite.internal.utils.JsonUtils;
import com.couchbase.lite.utils.Fn;
import com.couchbase.lite.utils.Report;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


public abstract class BaseDbTest extends BaseTest {
    @FunctionalInterface
    public interface DocValidator extends Fn.ConsumerThrows<Document, CouchbaseLiteException> {}

    protected Database baseTestDb;

    @Before
    public void setUp() throws CouchbaseLiteException {
        baseTestDb = new Database(getUniqueName());
    }

    @After
    public void tearDown() {
        try { deleteDb(baseTestDb); }
        catch (CouchbaseLiteException e) { Report.log(LogLevel.INFO, "Failed to delete test Db", e);}
    }

    protected void reopenDB() throws CouchbaseLiteException {
        final String dbName = baseTestDb.getName();
        closeDb(baseTestDb);
        baseTestDb = new Database(dbName);
    }

    protected void recreateDB() throws CouchbaseLiteException {
        final String dbName = baseTestDb.getName();
        if (baseTestDb != null) { deleteDb(baseTestDb); }
        baseTestDb = new Database(dbName);
    }


    protected Document createDocInBaseTestDb(String docID) throws CouchbaseLiteException {
        long n = baseTestDb.getCount();

        MutableDocument doc = new MutableDocument(docID);
        doc.setValue("key", 1);
        saveDocInBaseTestDb(doc);
        assertTrue((n + 1) == baseTestDb.getCount());

        Document savedDoc = baseTestDb.getDocument(docID);
        assertEquals(1, savedDoc.getSequence());

        return savedDoc;
    }

    protected Document saveDocInBaseTestDb(MutableDocument doc, DocValidator validator) throws CouchbaseLiteException {
        validator.accept(doc);

        Document savedDoc = saveDocInBaseTestDb(doc);
        validator.accept(doc);
        validator.accept(savedDoc);

        return savedDoc;
    }

    protected Document saveDocInBaseTestDb(MutableDocument doc) throws CouchbaseLiteException {
        baseTestDb.save(doc);

        Document savedDoc = baseTestDb.getDocument(doc.getId());
        assertNotNull(savedDoc);
        assertEquals(doc.getId(), savedDoc.getId());

        return savedDoc;
    }

    protected void loadJSONResource(String name) throws Exception {
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
