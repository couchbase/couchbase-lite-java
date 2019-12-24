//
// QueryTest.java
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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Ignore;
import org.junit.Test;

import com.couchbase.lite.utils.Report;

import static com.couchbase.lite.utils.TestUtils.assertThrows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


public class QueryTest extends BaseQueryTest {
    private static Expression EXPR_NUMBER1 = Expression.property("number1");
    private static Expression EXPR_NUMBER2 = Expression.property("number2");

    private static SelectResult SR_DOCID = SelectResult.expression(Meta.id);
    private static SelectResult SR_SEQUENCE = SelectResult.expression(Meta.sequence);
    private static SelectResult SR_DELETED = SelectResult.expression(Meta.deleted);
    private static SelectResult SR_EXPIRATION = SelectResult.expression(Meta.expiration);
    private static SelectResult SR_ALL = SelectResult.all();
    private static SelectResult SR_NUMBER1 = SelectResult.property("number1");

    @Test
    public void testQueryDocumentExpiration() throws Exception {
        long now = System.currentTimeMillis();

        // this one should expire
        MutableDocument doc = new MutableDocument("doc");
        doc.setInt("answer", 42);
        doc.setString("notHere", "string");
        save(doc);
        db.setDocumentExpiration("doc", new Date(now + 500L));

        // this one is deleted
        MutableDocument doc10 = new MutableDocument("doc10");
        doc10.setInt("answer", 42);
        doc10.setString("notHere", "string");
        save(doc10);
        db.setDocumentExpiration("doc10", new Date(now + 2000L)); //deleted doc
        db.delete(doc10);

        // should be in the result set
        MutableDocument doc1 = new MutableDocument("doc1");
        doc1.setInt("answer", 42);
        doc1.setString("a", "string");
        save(doc1);
        db.setDocumentExpiration("doc1", new Date(now + 2000L));

        // should be in the result set
        MutableDocument doc2 = new MutableDocument("doc2");
        doc2.setInt("answer", 42);
        doc2.setString("b", "string");
        save(doc2);
        db.setDocumentExpiration("doc2", new Date(now + 3000L));

        // should be in the result set
        MutableDocument doc3 = new MutableDocument("doc3");
        doc3.setInt("answer", 42);
        doc3.setString("c", "string");
        save(doc3);
        db.setDocumentExpiration("doc3", new Date(now + 4000L));

        Thread.sleep(1000);

        // This should get all but the one that has expired
        // and the one that was deleted
        Query query = QueryBuilder.select(SR_DOCID, SR_EXPIRATION)
            .from(DataSource.database(db))
            .where(Meta.expiration.lessThan(Expression.longValue(now + 6000L)));

        int rows = verifyQuery(query, false, (n, result) -> { });
        assertEquals(3, rows);
    }

    @Test
    public void testQueryDocumentIsNotDeleted() throws Exception {
        MutableDocument doc1a = new MutableDocument("doc1");
        doc1a.setInt("answer", 42);
        doc1a.setString("a", "string");
        db.save(doc1a);

        Query query = QueryBuilder.select(SR_DOCID, SR_DELETED)
            .from(DataSource.database(db))
            .where(Meta.id.equalTo(Expression.string("doc1"))
                .and(Meta.deleted.equalTo(Expression.booleanValue(false))));

        int rows = verifyQuery(query, false, (n, result) -> {
            assertEquals(result.getString(0), "doc1");
            assertFalse(result.getBoolean(1));
        });
        assertEquals(1, rows);
    }

    @Test
    public void testQueryDocumentIsDeleted() throws Exception {
        MutableDocument doc1a = new MutableDocument("doc1");
        doc1a.setInt("answer", 42);
        doc1a.setString("a", "string");
        save(doc1a);

        db.delete(db.getDocument("doc1"));

        Query query = QueryBuilder.select(SR_DOCID, SR_DELETED)
            .from(DataSource.database(db))
            .where(Meta.deleted.equalTo(Expression.booleanValue(true))
                .and(Meta.id.equalTo(Expression.string("doc1"))));

        int numRows = verifyQuery(query, false, (n, result) -> { });
        assertEquals(1, numRows);
    }

    @Test
    public void testNoWhereQuery() throws Exception {
        loadJSONResource("names_100.json");
        Query query = QueryBuilder.select(SR_DOCID, SR_SEQUENCE).from(DataSource.database(db));
        int numRows = verifyQuery(query, (n, result) -> {
            String docID = result.getString(0);
            String expectedID = String.format(Locale.ENGLISH, "doc-%03d", n);
            assertEquals(expectedID, docID);

            int sequence = result.getInt(1);
            assertEquals(n, sequence);

            Document doc = db.getDocument(docID);
            assertEquals(expectedID, doc.getId());
            assertEquals(n, doc.getSequence());
        });
        assertEquals(100, numRows);
    }

    @Test
    public void testWhereComparison() throws Exception {
        Object[][] cases = {
            {EXPR_NUMBER1.lessThan(Expression.intValue(3)), docids(1, 2)},
            {EXPR_NUMBER1.greaterThanOrEqualTo(Expression.intValue(3)), docids(3, 4, 5, 6, 7, 8, 9, 10)},
            {EXPR_NUMBER1.lessThanOrEqualTo(Expression.intValue(3)), docids(1, 2, 3)},
            {EXPR_NUMBER1.greaterThan(Expression.intValue(3)), docids(4, 5, 6, 7, 8, 9, 10)},
            {EXPR_NUMBER1.greaterThan(Expression.intValue(6)), docids(7, 8, 9, 10)},
            {EXPR_NUMBER1.lessThanOrEqualTo(Expression.intValue(6)), docids(1, 2, 3, 4, 5, 6)},
            {EXPR_NUMBER1.greaterThanOrEqualTo(Expression.intValue(6)), docids(6, 7, 8, 9, 10)},
            {EXPR_NUMBER1.lessThan(Expression.intValue(6)), docids(1, 2, 3, 4, 5)},
            {EXPR_NUMBER1.equalTo(Expression.intValue(7)), docids(7)},
            {EXPR_NUMBER1.notEqualTo(Expression.intValue(7)), docids(1, 2, 3, 4, 5, 6, 8, 9, 10)}
        };
        List<Map<String, Object>> numbers = loadNumberedDocs(10);
        runTestWithNumbers(numbers, cases);
    }

    @Test
    public void testWhereArithmetic() throws Exception {
        Object[][] cases = {
            {
                EXPR_NUMBER1.multiply(Expression.intValue(2)).greaterThan(Expression.intValue(3)),
                docids(2, 3, 4, 5, 6, 7, 8, 9, 10)
            },
            {
                EXPR_NUMBER1.divide(Expression.intValue(2)).greaterThan(Expression.intValue(3)),
                docids(8, 9, 10)
            },
            {
                EXPR_NUMBER1.modulo(Expression.intValue(2)).equalTo(Expression.intValue(0)),
                docids(2, 4, 6, 8, 10)
            },
            {
                EXPR_NUMBER1.add(Expression.intValue(5)).greaterThan(Expression.intValue(10)),
                docids(6, 7, 8, 9, 10)
            },
            {
                EXPR_NUMBER1.subtract(Expression.intValue(5)).greaterThan(Expression.intValue(0)),
                docids(6, 7, 8, 9, 10)
            },
            {
                EXPR_NUMBER1.multiply(EXPR_NUMBER2).greaterThan(Expression.intValue(10)),
                docids(2, 3, 4, 5, 6, 7, 8)
            },
            {
                EXPR_NUMBER2.divide(EXPR_NUMBER1).greaterThan(Expression.intValue(3)),
                docids(1, 2)
            },
            {
                EXPR_NUMBER2.modulo(EXPR_NUMBER1).equalTo(Expression.intValue(0)),
                docids(1, 2, 5, 10)
            },
            {
                EXPR_NUMBER1.add(EXPR_NUMBER2).equalTo(Expression.intValue(10)),
                docids(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
            },
            {
                EXPR_NUMBER1.subtract(EXPR_NUMBER2).greaterThan(Expression.intValue(0)),
                docids(6, 7, 8, 9, 10)
            }
        };
        List<Map<String, Object>> numbers = loadNumberedDocs(10);
        runTestWithNumbers(numbers, cases);
    }

    @Test
    public void testWhereAndOr() throws Exception {
        Object[][] cases = {
            {
                EXPR_NUMBER1.greaterThan(Expression.intValue(3)).and(EXPR_NUMBER2.greaterThan(Expression.intValue(3))),
                docids(4, 5, 6)
            },
            {
                EXPR_NUMBER1.lessThan(Expression.intValue(3)).or(EXPR_NUMBER2.lessThan(Expression.intValue(3))),
                docids(1, 2, 8, 9, 10)
            }
        };
        List<Map<String, Object>> numbers = loadNumberedDocs(10);
        runTestWithNumbers(numbers, cases);
    }

    @Test
    public void testWhereNullOrMissing() throws Exception {
        // https://github.com/couchbase/couchbase-lite-ios/issues/1670
        MutableDocument doc1 = new MutableDocument("doc1");
        doc1.setValue("name", "Scott");
        doc1.setValue("address", null);
        save(doc1);

        MutableDocument doc2 = new MutableDocument("doc2");
        doc2.setValue("name", "Tiger");
        doc2.setValue("address", "123 1st ave.");
        doc2.setValue("age", 20);
        save(doc2);

        Expression name = Expression.property("name");
        Expression address = Expression.property("address");
        Expression age = Expression.property("age");
        Expression work = Expression.property("work");

        Object[][] cases = {
            {name.isNullOrMissing(), docids()},
            {name.notNullOrMissing(), docids(1, 2)},
            {address.isNullOrMissing(), docids(1)},
            {address.notNullOrMissing(), docids(2)},
            {age.isNullOrMissing(), docids(1)},
            {age.notNullOrMissing(), docids(2)},
            {work.isNullOrMissing(), docids(1, 2)},
            {work.notNullOrMissing(), docids()},
        };

        for (Object[] c : cases) {
            Expression exp = (Expression) c[0];
            final String[] documentIDs = (String[]) c[1];
            Query query = QueryBuilder.select(SR_DOCID).from(DataSource.database(db)).where(exp);
            int numRows = verifyQuery(query, true, (n, result) -> {
                if (n < documentIDs.length) {
                    String docID = documentIDs[(int) n - 1];
                    assertEquals(docID, result.getString(0));
                }
            });
            assertEquals(documentIDs.length, numRows);
        }
    }

    @Test
    public void testWhereIs() throws Exception {
        final MutableDocument doc1 = new MutableDocument();
        doc1.setValue("string", "string");
        save(doc1);

        Query query = QueryBuilder
            .select(SR_DOCID)
            .from(DataSource.database(db))
            .where(Expression.property("string").is(Expression.string("string")));

        int numRows = verifyQuery(query, (n, result) -> {
            String docID = result.getString(0);
            assertEquals(doc1.getId(), docID);
            Document doc = db.getDocument(docID);
            assertEquals(doc1.getValue("string"), doc.getValue("string"));
        });
        assertEquals(1, numRows);
    }

    @Test
    public void testWhereIsNot() throws Exception {
        final MutableDocument doc1 = new MutableDocument();
        doc1.setValue("string", "string");
        save(doc1);

        Query query = QueryBuilder
            .select(SR_DOCID)
            .from(DataSource.database(db))
            .where(Expression.property("string").isNot(Expression.string("string1")));

        int numRows = verifyQuery(query, (n, result) -> {
            String docID = result.getString(0);
            assertEquals(doc1.getId(), docID);
            Document doc = db.getDocument(docID);
            assertEquals(doc1.getValue("string"), doc.getValue("string"));
        });
        assertEquals(1, numRows);
    }

    @Test
    public void testWhereBetween() throws Exception {
        Object[][] cases = {
            {EXPR_NUMBER1.between(Expression.intValue(3), Expression.intValue(7)), docids(3, 4, 5, 6, 7)}
        };
        List<Map<String, Object>> numbers = loadNumberedDocs(10);
        runTestWithNumbers(numbers, cases);
    }

    @Test
    public void testWhereIn() throws Exception {
        loadJSONResource("names_100.json");

        final Expression[] expected = {
            Expression.string("Marcy"),
            Expression.string("Margaretta"),
            Expression.string("Margrett"),
            Expression.string("Marlen"),
            Expression.string("Maryjo")};

        DataSource ds = DataSource.database(db);
        Expression exprFirstName = Expression.property("name.first");
        SelectResult srFirstName = SelectResult.property("name.first");
        Ordering orderByFirstName = Ordering.property("name.first");
        Query query = QueryBuilder.select(srFirstName)
            .from(ds)
            .where(exprFirstName.in(expected))
            .orderBy(orderByFirstName);

        int numRows = verifyQuery(query, (n, result) -> {
            String name = result.getString(0);
            assertEquals(expected[n - 1].asJSON(), name);
        });
        assertEquals(expected.length, numRows);
    }

    @Test
    public void testWhereLike() throws Exception {
        loadJSONResource("names_100.json");

        Expression w = Expression.property("name.first").like(Expression.string("%Mar%"));
        Query query = QueryBuilder
            .select(SR_DOCID)
            .from(DataSource.database(db))
            .where(w)
            .orderBy(Ordering.property("name.first").ascending());

        final List<String> firstNames = new ArrayList<>();
        int numRows = verifyQuery(query, false, (n, result) -> {
            String docID = result.getString(0);
            Document doc = db.getDocument(docID);
            Map<String, Object> name = doc.getDictionary("name").toMap();
            if (name != null) {
                String firstName = (String) name.get("first");
                if (firstName != null) {
                    firstNames.add(firstName);
                }
            }
        });
        assertEquals(5, numRows);
        assertEquals(5, firstNames.size());
    }

    @Test
    public void testWhereRegex() throws Exception {
        loadJSONResource("names_100.json");

        Expression w = Expression.property("name.first").regex(Expression.string("^Mar.*"));
        Query query = QueryBuilder
            .select(SR_DOCID)
            .from(DataSource.database(db))
            .where(w)
            .orderBy(Ordering.property("name.first").ascending());

        final List<String> firstNames = new ArrayList<>();
        int numRows = verifyQuery(query, false, (n, result) -> {
            String docID = result.getString(0);
            Document doc = db.getDocument(docID);
            Map<String, Object> name = doc.getDictionary("name").toMap();
            if (name != null) {
                String firstName = (String) name.get("first");
                if (firstName != null) {
                    firstNames.add(firstName);
                }
            }
        });
        assertEquals(5, numRows);
        assertEquals(5, firstNames.size());
    }

    @Test
    public void testWhereMatch() throws Exception {
        loadJSONResource("sentences.json");

        Index index = IndexBuilder.fullTextIndex(FullTextIndexItem.property("sentence"));
        db.createIndex("sentence", index);

        SelectResult S_SENTENCE = SelectResult.property("sentence");
        FullTextExpression SENTENCE = FullTextExpression.index("sentence");
        Query query = QueryBuilder
            .select(SR_DOCID, S_SENTENCE)
            .from(DataSource.database(db))
            .where(SENTENCE.match("'Dummie woman'"))
            .orderBy(Ordering.expression(FullTextFunction.rank("sentence")).descending());

        int numRows = verifyQuery(query, (n, result) -> {
            assertNotNull(result.getString(0));
            assertNotNull(result.getString(1));
        });
        assertEquals(2, numRows);
    }

    @Test
    public void testOrderBy() throws Exception {
        loadJSONResource("names_100.json");

        boolean[] cases = {true, false};
        for (final boolean ascending : cases) {
            Ordering o;
            if (ascending) { o = Ordering.expression(Expression.property("name.first")).ascending(); }
            else { o = Ordering.expression(Expression.property("name.first")).descending(); }

            Query query = QueryBuilder.select(SR_DOCID).from(DataSource.database(db)).orderBy(o);
            final List<String> firstNames = new ArrayList<String>();

            int numRows = verifyQuery(query, (n, result) -> {
                String docID = result.getString(0);
                Document doc = db.getDocument(docID);
                Map<String, Object> name = doc.getDictionary("name").toMap();
                String firstName = (String) name.get("first");
                firstNames.add(firstName);
            });
            assertEquals(100, numRows);

            List<String> sorted = new ArrayList<>(firstNames);
            Collections.sort(sorted, (o1, o2) -> ascending ? o1.compareTo(o2) : o2.compareTo(o1));
            String[] array1 = firstNames.toArray(new String[firstNames.size()]);
            String[] array2 = firstNames.toArray(new String[sorted.size()]);
            assertTrue(Arrays.equals(array1, array2));
        }
    }

    @Test
    public void testSelectDistinct() throws Exception {
        // https://github.com/couchbase/couchbase-lite-ios/issues/1669
        // https://github.com/couchbase/couchbase-lite-core/issues/81
        final MutableDocument doc1 = new MutableDocument();
        doc1.setValue("number", 20);
        save(doc1);

        MutableDocument doc2 = new MutableDocument();
        doc2.setValue("number", 20);
        save(doc2);

        SelectResult S_NUMBER = SelectResult.property("number");
        Query query = QueryBuilder.selectDistinct(S_NUMBER).from(DataSource.database(db));

        int numRows = verifyQuery(query, (n, result) -> assertEquals(20, result.getInt(0)));
        assertEquals(1, numRows);
    }

    @Test
    public void testJoin() throws Exception {
        loadNumberedDocs(100);

        final MutableDocument doc1 = new MutableDocument("joinme");
        doc1.setValue("theone", 42);
        save(doc1);

        DataSource mainDS = DataSource.database(this.db).as("main");
        DataSource secondaryDS = DataSource.database(this.db).as("secondary");

        Expression mainPropExpr = Expression.property("number1").from("main");
        Expression secondaryExpr = Expression.property("theone").from("secondary");
        Expression joinExpr = mainPropExpr.equalTo(secondaryExpr);
        Join join = Join.join(secondaryDS).on(joinExpr);

        SelectResult MAIN_DOC_ID = SelectResult.expression(Meta.id.from("main"));

        Query query = QueryBuilder.select(MAIN_DOC_ID).from(mainDS).join(join);

        int numRows = verifyQuery(query, (n, result) -> {
            String docID = result.getString(0);
            Document doc = db.getDocument(docID);
            assertEquals(42, doc.getInt("number1"));
        });
        assertEquals(1, numRows);
    }

    @Test
    public void testLeftJoin() throws Exception {
        loadNumberedDocs(100);

        final MutableDocument joinme = new MutableDocument("joinme");
        joinme.setValue("theone", 42);
        save(joinme);

        DataSource mainDS = DataSource.database(this.db).as("main");
        DataSource secondaryDS = DataSource.database(this.db).as("secondary");

        Expression mainPropExpr = Expression.property("number1").from("main");
        Expression secondaryExpr = Expression.property("theone").from("secondary");
        Expression joinExpr = mainPropExpr.equalTo(secondaryExpr);
        Join join = Join.leftJoin(secondaryDS).on(joinExpr);

        SelectResult sr1 = SelectResult.expression(Expression.property("number2").from("main"));
        SelectResult sr2 = SelectResult.expression(Expression.property("theone").from("secondary"));

        Query query = QueryBuilder.select(sr1, sr2).from(mainDS).join(join);

        int numRows = verifyQuery(query, (n, result) -> {
            if (n == 41) {
                assertEquals(59, result.getInt(0));
                assertNull(result.getValue(1));
            }
            if (n == 42) {
                assertEquals(58, result.getInt(0));
                assertEquals(42, result.getInt(1));
            }
        });
        assertEquals(101, numRows);
    }

    @Test
    public void testCrossJoin() throws Exception {
        loadNumberedDocs(10);

        DataSource mainDS = DataSource.database(this.db).as("main");
        DataSource secondaryDS = DataSource.database(this.db).as("secondary");

        Join join = Join.crossJoin(secondaryDS);

        SelectResult sr1 = SelectResult.expression(Expression.property("number1").from("main"));
        SelectResult sr2 = SelectResult.expression(Expression.property("number2").from("secondary"));

        Query query = QueryBuilder.select(sr1, sr2).from(mainDS).join(join);

        int numRows = verifyQuery(query, (n, result) -> {
            int num1 = result.getInt(0);
            int num2 = result.getInt(1);
            assertEquals((num1 - 1) % 10, (n - 1) / 10);
            assertEquals((10 - num2) % 10, n % 10);
        });
        assertEquals(100, numRows);
    }

    @Test
    public void testGroupBy() throws Exception {
        loadJSONResource("names_100.json");

        final List<String> expectedStates = Arrays.asList("AL", "CA", "CO", "FL", "IA");
        final List<Integer> expectedCounts = Arrays.asList(1, 6, 1, 1, 3);
        final List<String> expectedMaxZips = Arrays.asList("35243", "94153", "81223", "33612", "50801");

        DataSource ds = DataSource.database(this.db);

        Expression state = Expression.property("contact.address.state");
        Expression count = Function.count(Expression.intValue(1));
        Expression zip = Expression.property("contact.address.zip");
        Expression maxZip = Function.max(zip);
        Expression gender = Expression.property("gender");

        SelectResult rsState = SelectResult.property("contact.address.state");
        SelectResult rsCount = SelectResult.expression(count);
        SelectResult rsMaxZip = SelectResult.expression(maxZip);

        Expression groupByExpr = state;
        Ordering ordering = Ordering.expression(state);

        Query query = QueryBuilder
            .select(rsState, rsCount, rsMaxZip)
            .from(ds)
            .where(gender.equalTo(Expression.string("female")))
            .groupBy(groupByExpr)
            .orderBy(ordering);

        int numRows = verifyQuery(query, (n, result) -> {
            String state1 = (String) result.getValue(0);
            long count1 = (long) result.getValue(1);
            String maxZip1 = (String) result.getValue(2);
            if (n - 1 < expectedStates.size()) {
                assertEquals(expectedStates.get(n - 1), state1);
                assertEquals((int) expectedCounts.get(n - 1), count1);
                assertEquals(expectedMaxZips.get(n - 1), maxZip1);
            }
        });
        assertEquals(31, numRows);

        // With HAVING:
        final List<String> expectedStates2 = Arrays.asList("CA", "IA", "IN");
        final List<Integer> expectedCounts2 = Arrays.asList(6, 3, 2);
        final List<String> expectedMaxZips2 = Arrays.asList("94153", "50801", "47952");

        Expression havingExpr = count.greaterThan(Expression.intValue(1));

        query = QueryBuilder
            .select(rsState, rsCount, rsMaxZip)
            .from(ds)
            .where(gender.equalTo(Expression.string("female")))
            .groupBy(groupByExpr)
            .having(havingExpr)
            .orderBy(ordering);

        numRows = verifyQuery(query, (n, result) -> {
            String state12 = (String) result.getValue(0);
            long count12 = (long) result.getValue(1);
            String maxZip12 = (String) result.getValue(2);
            if (n - 1 < expectedStates2.size()) {
                assertEquals(expectedStates2.get(n - 1), state12);
                assertEquals((long) expectedCounts2.get(n - 1), count12);
                assertEquals(expectedMaxZips2.get(n - 1), maxZip12);
            }
        });
        assertEquals(15, numRows);
    }

    @Test
    public void testParameters() throws Exception {
        loadNumberedDocs(100);

        DataSource dataSource = DataSource.database(this.db);


        Expression paramN1 = Expression.parameter("num1");
        Expression paramN2 = Expression.parameter("num2");

        Ordering ordering = Ordering.expression(EXPR_NUMBER1);

        Query query = QueryBuilder
            .select(SR_NUMBER1)
            .from(dataSource)
            .where(EXPR_NUMBER1.between(paramN1, paramN2))
            .orderBy(ordering);
        Parameters params = new Parameters(query.getParameters())
            .setValue("num1", 2)
            .setValue("num2", 5);
        query.setParameters(params);

        final long[] expectedNumbers = {2, 3, 4, 5};
        int numRows = verifyQuery(query, (n, result) -> {
            long number = (long) result.getValue(0);
            assertEquals(expectedNumbers[n - 1], number);
        });
        assertEquals(4, numRows);
    }

    @Test
    public void testMeta() throws Exception {
        loadNumberedDocs(5);

        DataSource dataSource = DataSource.database(this.db);

        Query query = QueryBuilder
            .select(SR_DOCID, SR_SEQUENCE, SR_NUMBER1)
            .from(dataSource)
            .orderBy(Ordering.expression(Meta.sequence));

        final String[] expectedDocIDs = {"doc1", "doc2", "doc3", "doc4", "doc5"};
        final long[] expectedSeqs = {1, 2, 3, 4, 5};
        final long[] expectedNumbers = {1, 2, 3, 4, 5};

        int numRows = verifyQuery(query, (n, result) -> {
            String docID1 = (String) result.getValue(0);
            String docID2 = result.getString(0);
            String docID3 = (String) result.getValue("id");
            String docID4 = result.getString("id");

            long seq1 = (long) result.getValue(1);
            long seq2 = result.getLong(1);
            long seq3 = (long) result.getValue("sequence");
            long seq4 = result.getLong("sequence");

            long number = (long) result.getValue(2);

            assertEquals(expectedDocIDs[n - 1], docID1);
            assertEquals(expectedDocIDs[n - 1], docID2);
            assertEquals(expectedDocIDs[n - 1], docID3);
            assertEquals(expectedDocIDs[n - 1], docID4);

            assertEquals(expectedSeqs[n - 1], seq1);
            assertEquals(expectedSeqs[n - 1], seq2);
            assertEquals(expectedSeqs[n - 1], seq3);
            assertEquals(expectedSeqs[n - 1], seq4);

            assertEquals(expectedNumbers[n - 1], number);
        });
        assertEquals(5, numRows);
    }

    @Test
    public void testLimit() throws Exception {
        loadNumberedDocs(10);

        DataSource dataSource = DataSource.database(this.db);

        Query query = QueryBuilder
            .select(SR_NUMBER1)
            .from(dataSource)
            .orderBy(Ordering.expression(EXPR_NUMBER1))
            .limit(Expression.intValue(5));

        final long[] expectedNumbers = {1, 2, 3, 4, 5};
        int numRows = verifyQuery(query, (n, result) -> {
            long number = (long) result.getValue(0);
            assertEquals(expectedNumbers[n - 1], number);
        });
        assertEquals(5, numRows);

        Expression paramExpr = Expression.parameter("LIMIT_NUM");
        query = QueryBuilder
            .select(SR_NUMBER1)
            .from(dataSource)
            .orderBy(Ordering.expression(EXPR_NUMBER1))
            .limit(paramExpr);
        Parameters params = new Parameters(query.getParameters()).setValue("LIMIT_NUM", 3);
        query.setParameters(params);

        final long[] expectedNumbers2 = {1, 2, 3};
        numRows = verifyQuery(query, (n, result) -> {
            long number = (long) result.getValue(0);
            assertEquals(expectedNumbers2[n - 1], number);
        });
        assertEquals(3, numRows);
    }

    @Test
    public void testLimitOffset() throws Exception {
        loadNumberedDocs(10);

        DataSource dataSource = DataSource.database(this.db);

        Query query = QueryBuilder
            .select(SR_NUMBER1)
            .from(dataSource)
            .orderBy(Ordering.expression(EXPR_NUMBER1))
            .limit(Expression.intValue(5), Expression.intValue(3));

        final long[] expectedNumbers = {4, 5, 6, 7, 8};
        int numRows = verifyQuery(query, (n, result) -> {
            long number = (long) result.getValue(0);
            assertEquals(expectedNumbers[n - 1], number);
        });
        assertEquals(5, numRows);

        Expression paramLimitExpr = Expression.parameter("LIMIT_NUM");
        Expression paramOffsetExpr = Expression.parameter("OFFSET_NUM");
        query = QueryBuilder
            .select(SR_NUMBER1)
            .from(dataSource)
            .orderBy(Ordering.expression(EXPR_NUMBER1))
            .limit(paramLimitExpr, paramOffsetExpr);
        Parameters params = new Parameters(query.getParameters())
            .setValue("LIMIT_NUM", 3)
            .setValue("OFFSET_NUM", 5);
        query.setParameters(params);

        final long[] expectedNumbers2 = {6, 7, 8};
        numRows = verifyQuery(query, (n, result) -> {
            long number = (long) result.getValue(0);
            assertEquals(expectedNumbers2[n - 1], number);
        });
        assertEquals(3, numRows);
    }

    @Test
    public void testQueryResult() throws Exception {
        loadJSONResource("names_100.json");

        SelectResult RES_FNAME = SelectResult.property("name.first").as("firstname");
        SelectResult RES_LNAME = SelectResult.property("name.last").as("lastname");
        SelectResult RES_GENDER = SelectResult.property("gender");
        SelectResult RES_CITY = SelectResult.property("contact.address.city");

        DataSource DS = DataSource.database(db);

        Query query = QueryBuilder
            .select(RES_FNAME, RES_LNAME, RES_GENDER, RES_CITY)
            .from(DS);

        int numRows = verifyQuery(query, (n, result) -> {
            assertEquals(4, result.count());
            assertEquals(result.getValue(0), result.getValue("firstname"));
            assertEquals(result.getValue(1), result.getValue("lastname"));
            assertEquals(result.getValue(2), result.getValue("gender"));
            assertEquals(result.getValue(3), result.getValue("city"));
        });
        assertEquals(100, numRows);
    }

    @Test
    public void testQueryProjectingKeys() throws Exception {
        loadNumberedDocs(100);

        DataSource DS = DataSource.database(db);

        Expression AVG = Function.avg(EXPR_NUMBER1);
        Expression CNT = Function.count(EXPR_NUMBER1);
        Expression MIN = Function.min(EXPR_NUMBER1);
        Expression MAX = Function.max(EXPR_NUMBER1);
        Expression SUM = Function.sum(EXPR_NUMBER1);

        SelectResult RES_AVG = SelectResult.expression(AVG);
        SelectResult RES_CNT = SelectResult.expression(CNT);
        SelectResult RES_MIN = SelectResult.expression(MIN).as("min");
        SelectResult RES_MAX = SelectResult.expression(MAX);
        SelectResult RES_SUM = SelectResult.expression(SUM).as("sum");

        Query query = QueryBuilder
            .select(RES_AVG, RES_CNT, RES_MIN, RES_MAX, RES_SUM)
            .from(DS);

        int numRows = verifyQuery(query, (n, result) -> {
            assertEquals(5, result.count());
            assertEquals(result.getValue(0), result.getValue("$1"));
            assertEquals(result.getValue(1), result.getValue("$2"));
            assertEquals(result.getValue(2), result.getValue("min"));
            assertEquals(result.getValue(3), result.getValue("$3"));
            assertEquals(result.getValue(4), result.getValue("sum"));
        });
        assertEquals(1, numRows);
    }

    @Test
    public void testQuantifiedOperators() throws Exception {
        loadJSONResource("names_100.json");

        DataSource ds = DataSource.database(db);

        Expression exprLikes = Expression.property("likes");
        VariableExpression exprVarLike = ArrayExpression.variable("LIKE");

        // ANY:
        Query query = QueryBuilder
            .select(SR_DOCID)
            .from(ds)
            .where(ArrayExpression
                .any(exprVarLike)
                .in(exprLikes)
                .satisfies(exprVarLike.equalTo(Expression.string("climbing"))));

        final AtomicInteger i = new AtomicInteger(0);
        final String[] expected = {"doc-017", "doc-021", "doc-023", "doc-045", "doc-060"};
        int numRows = verifyQuery(query, false, (n, result) -> {
            assertEquals(expected[i.getAndIncrement()], result.getString(0));
        });
        assertEquals(expected.length, numRows);

        // EVERY:
        query = QueryBuilder
            .select(SR_DOCID)
            .from(ds)
            .where(ArrayExpression
                .every(ArrayExpression.variable("LIKE"))
                .in(exprLikes)
                .satisfies(exprVarLike.equalTo(Expression.string("taxes"))));

        numRows = verifyQuery(query, false, (n, result) -> {
            if (n == 1) { assertEquals("doc-007", result.getString(0)); }
        });
        assertEquals(42, numRows);

        // ANY AND EVERY:
        query = QueryBuilder
            .select(SR_DOCID)
            .from(ds)
            .where(ArrayExpression
                .anyAndEvery(ArrayExpression.variable("LIKE"))
                .in(exprLikes)
                .satisfies(exprVarLike.equalTo(Expression.string("taxes"))));

        numRows = verifyQuery(query, false, (n, result) -> { });
        assertEquals(0, numRows);
    }

    @Test
    public void testAggregateFunctions() throws Exception {
        loadNumberedDocs(100);

        DataSource ds = DataSource.database(this.db);

        Expression avg = Function.avg(EXPR_NUMBER1);
        Expression cnt = Function.count(EXPR_NUMBER1);
        Expression min = Function.min(EXPR_NUMBER1);
        Expression max = Function.max(EXPR_NUMBER1);
        Expression sum = Function.sum(EXPR_NUMBER1);

        SelectResult rsAvg = SelectResult.expression(avg);
        SelectResult rsCnt = SelectResult.expression(cnt);
        SelectResult rsMin = SelectResult.expression(min);
        SelectResult rsMax = SelectResult.expression(max);
        SelectResult rsSum = SelectResult.expression(sum);

        Query query = QueryBuilder.select(rsAvg, rsCnt, rsMin, rsMax, rsSum).from(ds);
        int numRows = verifyQuery(query, (n, result) -> {
            assertEquals(50.5F, (float) result.getValue(0), 0.0F);
            assertEquals(100L, (long) result.getValue(1));
            assertEquals(1L, (long) result.getValue(2));
            assertEquals(100L, (long) result.getValue(3));
            assertEquals(5050L, (long) result.getValue(4));
        });
        assertEquals(1, numRows);
    }

    @Test
    public void testArrayFunctions() throws Exception {
        MutableDocument doc = new MutableDocument("doc1");
        MutableArray array = new MutableArray();
        array.addValue("650-123-0001");
        array.addValue("650-123-0002");
        doc.setValue("array", array);
        save(doc);

        DataSource ds = DataSource.database(db);
        Expression exprArray = Expression.property("array");
        Expression exprArrayLength = ArrayFunction.length(exprArray);
        SelectResult srArrayLength = SelectResult.expression(exprArrayLength);

        Query query = QueryBuilder.select(srArrayLength).from(ds);

        int numRows = verifyQuery(query, (n, result) -> assertEquals(2, result.getInt(0)));
        assertEquals(1, numRows);

        Expression exArrayContains1 = ArrayFunction.contains(exprArray, Expression.string("650-123-0001"));
        Expression exArrayContains2 = ArrayFunction.contains(exprArray, Expression.string("650-123-0003"));
        SelectResult srArrayContains1 = SelectResult.expression(exArrayContains1);
        SelectResult srArrayContains2 = SelectResult.expression(exArrayContains2);

        query = QueryBuilder.select(srArrayContains1, srArrayContains2).from(ds);

        numRows = verifyQuery(query, (n, result) -> {
            assertEquals(true, result.getBoolean(0));
            assertEquals(false, result.getBoolean(1));
        });
        assertEquals(1, numRows);
    }

    @Test
    public void testArrayFunctionsEmptyArgs() throws Exception {
        Expression exprArray = Expression.property("array");

        assertThrows(
            IllegalArgumentException.class,
            () -> ArrayFunction.contains(null, Expression.string("650-123-0001")));

        assertThrows(IllegalArgumentException.class, () -> ArrayFunction.contains(exprArray, null));

        assertThrows(IllegalArgumentException.class, () -> ArrayFunction.length(null));
    }

    @Test
    public void testMathFunctions() throws Exception {
        double num = 0.6;

        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue("number", num);
        save(doc);

        final Double[] expectedValues = {
            Math.abs(num),
            Math.acos(num),
            Math.asin(num),
            Math.atan(num),
            Math.atan2(num, 90.0), // NOTE: Math.atan2(double y, double x);
            Math.ceil(num),
            Math.cos(num),
            num * 180.0 / Math.PI,
            Math.exp(num),
            Math.floor(num),
            Math.log(num),
            Math.log10(num),
            Math.pow(num, 2),
            num * Math.PI / 180.0,
            (double) Math.round(num),
            Math.round(num * 10.0) / 10.0,
            (double) 1,
            Math.sin(num),
            Math.sqrt(num),
            Math.tan(num),
            (double) 0,
            0.6
        };

        Expression p = Expression.property("number");
        List<Expression> functions = Arrays.asList(
            Function.abs(p),
            Function.acos(p),
            Function.asin(p),
            Function.atan(p),
            Function.atan2(Expression.doubleValue(90.0), Expression.doubleValue(num)),
            Function.ceil(p),
            Function.cos(p),
            Function.degrees(p),
            Function.exp(p),
            Function.floor(p),
            Function.ln(p),
            Function.log(p),
            Function.power(p, Expression.intValue(2)),
            Function.radians(p),
            Function.round(p),
            Function.round(p, Expression.intValue(1)),
            Function.sign(p),
            Function.sin(p),
            Function.sqrt(p),
            Function.tan(p),
            Function.trunc(p),
            Function.trunc(p, Expression.intValue(1))
        );
        final AtomicInteger index = new AtomicInteger(0);
        for (Expression f : functions) {
            Query query = QueryBuilder
                .select(SelectResult.expression(f))
                .from(DataSource.database(db));

            int numRows = verifyQuery(query, (n, result) -> {
                double expected = expectedValues[index.intValue()];
                assertEquals(expected, result.getDouble(0), 0.0001);
            });
            assertEquals(1, numRows);

            index.incrementAndGet();
        }
    }

    @Test
    public void testStringFunctions() throws Exception {
        final String str = "  See you 18r  ";
        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue("greeting", str);
        save(doc);

        DataSource ds = DataSource.database(db);

        Expression prop = Expression.property("greeting");

        // Contains:
        Expression fnContains1 = Function.contains(prop, Expression.string("8"));
        Expression fnContains2 = Function.contains(prop, Expression.string("9"));
        SelectResult srFnContains1 = SelectResult.expression(fnContains1);
        SelectResult srFnContains2 = SelectResult.expression(fnContains2);

        Query query = QueryBuilder.select(srFnContains1, srFnContains2).from(ds);

        int numRows = verifyQuery(query, (n, result) -> {
            assertTrue(result.getBoolean(0));
            assertFalse(result.getBoolean(1));
        });
        assertEquals(1, numRows);

        // Length
        Expression fnLength = Function.length(prop);
        query = QueryBuilder.select(SelectResult.expression(fnLength)).from(ds);

        numRows = verifyQuery(query, (n, result) -> assertEquals(str.length(), result.getInt(0)));
        assertEquals(1, numRows);

        // Lower, Ltrim, Rtrim, Trim, Upper:
        Expression fnLower = Function.lower(prop);
        Expression fnLTrim = Function.ltrim(prop);
        Expression fnRTrim = Function.rtrim(prop);
        Expression fnTrim = Function.trim(prop);
        Expression fnUpper = Function.upper(prop);

        query = QueryBuilder.select(
            SelectResult.expression(fnLower),
            SelectResult.expression(fnLTrim),
            SelectResult.expression(fnRTrim),
            SelectResult.expression(fnTrim),
            SelectResult.expression(fnUpper))
            .from(ds);

        numRows = verifyQuery(query, (n, result) -> {
            assertEquals(str.toLowerCase(Locale.ENGLISH), result.getString(0));
            assertEquals(str.replaceAll("^\\s+", ""), result.getString(1));
            assertEquals(str.replaceAll("\\s+$", ""), result.getString(2));
            assertEquals(str.trim(), result.getString(3));
            assertEquals(str.toUpperCase(Locale.ENGLISH), result.getString(4));
        });
        assertEquals(1, numRows);
    }

    @Test
    public void testSelectAll() throws Exception {
        loadNumberedDocs(100);

        DataSource.As ds = DataSource.database(db);

        Expression exprTestDBNum1 = Expression.property("number1").from("testdb");
        SelectResult srTestDBNum1 = SelectResult.expression(exprTestDBNum1);
        SelectResult srTestDBAll = SelectResult.all().from("testdb");

        // SELECT *
        Query query = QueryBuilder.select(SR_ALL).from(ds);

        int numRows = verifyQuery(query, (n, result) -> {
            assertEquals(1, result.count());
            Dictionary a1 = result.getDictionary(0);
            Dictionary a2 = result.getDictionary(db.getName());
            assertEquals(n, a1.getInt("number1"));
            assertEquals(100 - n, a1.getInt("number2"));
            assertEquals(n, a2.getInt("number1"));
            assertEquals(100 - n, a2.getInt("number2"));
        });
        assertEquals(100, numRows);

        // SELECT *, number1
        query = QueryBuilder.select(SR_ALL, SR_NUMBER1).from(ds);

        numRows = verifyQuery(query, (n, result) -> {
            assertEquals(2, result.count());
            Dictionary a1 = result.getDictionary(0);
            Dictionary a2 = result.getDictionary(db.getName());
            assertEquals(n, a1.getInt("number1"));
            assertEquals(100 - n, a1.getInt("number2"));
            assertEquals(n, a2.getInt("number1"));
            assertEquals(100 - n, a2.getInt("number2"));
            assertEquals(n, result.getInt(1));
            assertEquals(n, result.getInt("number1"));
        });
        assertEquals(100, numRows);

        // SELECT testdb.*
        query = QueryBuilder.select(srTestDBAll).from(ds.as("testdb"));

        numRows = verifyQuery(query, (n, result) -> {
            assertEquals(1, result.count());
            Dictionary a1 = result.getDictionary(0);
            Dictionary a2 = result.getDictionary("testdb");
            assertEquals(n, a1.getInt("number1"));
            assertEquals(100 - n, a1.getInt("number2"));
            assertEquals(n, a2.getInt("number1"));
            assertEquals(100 - n, a2.getInt("number2"));
        });
        assertEquals(100, numRows);

        // SELECT testdb.*, testdb.number1
        query = QueryBuilder.select(srTestDBAll, srTestDBNum1).from(ds.as("testdb"));

        numRows = verifyQuery(query, (n, result) -> {
            assertEquals(2, result.count());
            Dictionary a1 = result.getDictionary(0);
            Dictionary a2 = result.getDictionary("testdb");
            assertEquals(n, a1.getInt("number1"));
            assertEquals(100 - n, a1.getInt("number2"));
            assertEquals(n, a2.getInt("number1"));
            assertEquals(100 - n, a2.getInt("number2"));
            assertEquals(n, result.getInt(1));
            assertEquals(n, result.getInt("number1"));
        });
        assertEquals(100, numRows);
    }

    @Test
    public void testUnicodeCollationWithLocaleNone() throws Exception {
        String[] letters = {"B", "A", "Z", "Å"};
        for (String letter : letters) {
            MutableDocument doc = new MutableDocument();
            doc.setValue("string", letter);
            save(doc);
        }

        Collation noLocale = Collation.unicode()
            .locale(null)
            .ignoreCase(false)
            .ignoreAccents(false);

        Query query = QueryBuilder.select(SelectResult.property("string"))
            .from(DataSource.database(db))
            .orderBy(Ordering.expression(Expression.property("string").collate(noLocale)));

        final String[] expected = {"A", "Å", "B", "Z"};
        int numRows = verifyQuery(query, (n, result) -> assertEquals(expected[n - 1], result.getString(0)));
        assertEquals(expected.length, numRows);
    }

    @Test
    public void testUnicodeCollationWithLocaleSpanish() throws Exception {
        String[] letters = {"B", "A", "Z", "Å"};
        for (String letter : letters) {
            MutableDocument doc = new MutableDocument();
            doc.setValue("string", letter);
            save(doc);
        }

        Collation localeEspanol = Collation.unicode()
            .locale("es")
            .ignoreCase(false)
            .ignoreAccents(false);

        Query query = QueryBuilder.select(SelectResult.property("string"))
            .from(DataSource.database(db))
            .orderBy(Ordering.expression(Expression.property("string").collate(localeEspanol)));

        final String[] expected = {"A", "Å", "B", "Z"};
        int numRows = verifyQuery(query, (n, result) -> assertEquals(expected[n - 1], result.getString(0)));
        assertEquals(expected.length, numRows);
    }

    @Ignore("This test is platform dependent")
    @Test
    public void testUnicodeCollationWithLocaleSwedish() throws Exception {
        String[] letters = {"B", "A", "Z", "Å"};
        for (String letter : letters) {
            MutableDocument doc = new MutableDocument();
            doc.setValue("string", letter);
            save(doc);
        }

        Collation localeSvenska = Collation.unicode()
            .locale("se")
            .ignoreCase(false)
            .ignoreAccents(false);

        Query query = QueryBuilder.select(SelectResult.property("string"))
            .from(DataSource.database(db))
            .orderBy(Ordering.expression(Expression.property("string").collate(localeSvenska)));

        final String[] expected = {"A", "B", "Z", "Å"};
        int numRows = verifyQuery(query, (n, result) -> assertEquals(expected[n - 1], result.getString(0)));
        assertEquals(expected.length, numRows);
    }

    @Test
    public void testCompareWithUnicodeCollation() throws Exception {
        Collation bothSensitive = Collation.unicode().locale(null).ignoreCase(false).ignoreAccents(false);
        Collation accentSensitive = Collation.unicode().locale(null).ignoreCase(true).ignoreAccents(false);
        Collation caseSensitive = Collation.unicode().locale(null).ignoreCase(false).ignoreAccents(true);
        Collation noSensitive = Collation.unicode().locale(null).ignoreCase(true).ignoreAccents(true);

        List<List<Object>> testData = new ArrayList<>(
            Arrays.asList(
                // Edge cases: empty and 1-char strings:
                Arrays.asList("", "", true, bothSensitive),
                Arrays.asList("", "a", false, bothSensitive),
                Arrays.asList("a", "a", true, bothSensitive),

                // Case sensitive: lowercase come first by unicode rules:
                Arrays.asList("a", "A", false, bothSensitive),
                Arrays.asList("abc", "abc", true, bothSensitive),
                Arrays.asList("Aaa", "abc", false, bothSensitive),
                Arrays.asList("abc", "abC", false, bothSensitive),
                Arrays.asList("AB", "abc", false, bothSensitive),

                // Case insenstive:
                Arrays.asList("ABCDEF", "ZYXWVU", false, accentSensitive),
                Arrays.asList("ABCDEF", "Z", false, accentSensitive),

                Arrays.asList("a", "A", true, accentSensitive),
                Arrays.asList("abc", "ABC", true, accentSensitive),
                Arrays.asList("ABA", "abc", false, accentSensitive),

                Arrays.asList("commonprefix1", "commonprefix2", false, accentSensitive),
                Arrays.asList("commonPrefix1", "commonprefix2", false, accentSensitive),

                Arrays.asList("abcdef", "abcdefghijklm", false, accentSensitive),
                Arrays.asList("abcdeF", "abcdefghijklm", false, accentSensitive),

                // Now bring in non-ASCII characters:
                Arrays.asList("a", "á", false, accentSensitive),
                Arrays.asList("", "á", false, accentSensitive),
                Arrays.asList("á", "á", true, accentSensitive),
                Arrays.asList("•a", "•A", true, accentSensitive),

                Arrays.asList("test a", "test á", false, accentSensitive),
                Arrays.asList("test á", "test b", false, accentSensitive),
                Arrays.asList("test á", "test Á", true, accentSensitive),
                Arrays.asList("test á1", "test Á2", false, accentSensitive),

                // Case sensitive, diacritic sensitive:
                Arrays.asList("ABCDEF", "ZYXWVU", false, bothSensitive),
                Arrays.asList("ABCDEF", "Z", false, bothSensitive),
                Arrays.asList("a", "A", false, bothSensitive),
                Arrays.asList("abc", "ABC", false, bothSensitive),
                Arrays.asList("•a", "•A", false, bothSensitive),
                Arrays.asList("test a", "test á", false, bothSensitive),
                Arrays.asList("Ähnlichkeit", "apple", false, bothSensitive), // Because 'h'-vs-'p' beats 'Ä'-vs-'a'
                Arrays.asList("ax", "Äz", false, bothSensitive),
                Arrays.asList("test a", "test Á", false, bothSensitive),
                Arrays.asList("test Á", "test e", false, bothSensitive),
                Arrays.asList("test á", "test Á", false, bothSensitive),
                Arrays.asList("test á", "test b", false, bothSensitive),
                Arrays.asList("test u", "test Ü", false, bothSensitive),

                // Case sensitive, diacritic insensitive
                Arrays.asList("abc", "ABC", false, caseSensitive),
                Arrays.asList("test á", "test a", true, caseSensitive),
                Arrays.asList("test a", "test á", true, caseSensitive),
                Arrays.asList("test á", "test A", false, caseSensitive),
                Arrays.asList("test á", "test b", false, caseSensitive),
                Arrays.asList("test á", "test Á", false, caseSensitive),

                // Case and diacritic insensitive
                Arrays.asList("test á", "test Á", true, noSensitive)
            )
        );

        for (List<Object> data : testData) {
            MutableDocument mDoc = new MutableDocument();
            mDoc.setValue("value", data.get(0));
            Document doc = save(mDoc);

            Expression VALUE = Expression.property("value");
            Expression comparison = (Boolean) data.get(2) == true ?
                VALUE.collate((Collation) data.get(3)).equalTo(Expression.value(data.get(1))) :
                VALUE.collate((Collation) data.get(3)).lessThan(Expression.value(data.get(1)));

            Query query = QueryBuilder.select().from(DataSource.database(db)).where(comparison);

            int numRows = verifyQuery(query, (n, result) -> {
                assertEquals(1, n);
                assertNotNull(result);
            });
            assertEquals(data.toString(), 1, numRows);

            db.delete(doc);
        }
    }

    @Test
    public void testLiveQuery() throws Exception {
        loadNumberedDocs(100);

        Query query = QueryBuilder
            .select(SR_DOCID)
            .from(DataSource.database(db))
            .where(EXPR_NUMBER1.lessThan(Expression.intValue(10)))
            .orderBy(Ordering.property("number1").ascending());

        final CountDownLatch latch = new CountDownLatch(2);
        QueryChangeListener listener = change -> {
            ResultSet rs = change.getResults();
            if (latch.getCount() == 2) {
                int count = 0;
                while (rs.next() != null) { count++; }
                assertEquals(9, count);
            }
            else if (latch.getCount() == 1) {
                Result result;
                int count = 0;
                while ((result = rs.next()) != null) {
                    if (count == 0) {
                        Document doc = db.getDocument(result.getString(0));
                        assertEquals(-1L, doc.getValue("number1"));
                    }
                    count++;
                }
                assertEquals(10, count);
            }
            latch.countDown();
        };
        ListenerToken token = query.addChangeListener(executor, listener);
        try {
            // create one doc
            executeAsync(500, new Runnable() {
                @Override
                public void run() {
                    try {
                        createDocNumbered(-1, 100);
                    }
                    catch (CouchbaseLiteException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
            // wait till listener is called
            assertTrue(latch.await(20, TimeUnit.SECONDS));
        }
        finally {
            query.removeChangeListener(token);
        }
    }

    @Test
    public void testLiveQueryNoUpdate() throws Exception {
        _testLiveQueryNoUpdate(false);
    }

    @Test
    public void testLiveQueryNoUpdateConsumeAll() throws Exception {
        _testLiveQueryNoUpdate(true);
    }

    private void _testLiveQueryNoUpdate(final boolean consumeAll) throws Exception {
        loadNumberedDocs(100);

        Query query = QueryBuilder
            .select()
            .from(DataSource.database(db))
            .where(EXPR_NUMBER1.lessThan(Expression.intValue(10)))
            .orderBy(Ordering.property("number1").ascending());

        final CountDownLatch latch = new CountDownLatch(2);
        QueryChangeListener listener = change -> {
            ResultSet rs = change.getResults();
            if (consumeAll) {
                while (rs.next() != null) { ; }
            }
            latch.countDown();
            // should come only once!
        };
        ListenerToken token = query.addChangeListener(executor, listener);
        try {
            // create one doc
            executeAsync(500, new Runnable() {
                @Override
                public void run() {
                    try {
                        createDocNumbered(111, 100);
                    }
                    catch (CouchbaseLiteException e) {
                        throw new RuntimeException(e);
                    }
                }
            });

            // wait till listener is called
            assertFalse(latch.await(5, TimeUnit.SECONDS));
            assertEquals(1, latch.getCount());
        }
        finally {
            query.removeChangeListener(token);
        }
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1356
    @Test
    public void testCountFunctions() throws Exception {
        loadNumberedDocs(100);

        DataSource ds = DataSource.database(this.db);
        Expression cnt = Function.count(EXPR_NUMBER1);

        SelectResult rsCnt = SelectResult.expression(cnt);
        Query query = QueryBuilder.select(rsCnt).from(ds);

        int numRows = verifyQuery(query, (n, result) -> assertEquals(100L, (long) result.getValue(0)));
        assertEquals(1, numRows);
    }

    @Test
    public void testJoinWithArrayContains() throws Exception {
        // Data preparation
        // Hotels
        MutableDocument hotel1 = new MutableDocument("hotel1");
        hotel1.setString("type", "hotel");
        hotel1.setString("name", "Hilton");
        db.save(hotel1);

        MutableDocument hotel2 = new MutableDocument("hotel2");
        hotel2.setString("type", "hotel");
        hotel2.setString("name", "Sheraton");
        db.save(hotel2);

        MutableDocument hotel3 = new MutableDocument("hotel2");
        hotel3.setString("type", "hotel");
        hotel3.setString("name", "Marriott");
        db.save(hotel3);

        // Bookmark
        MutableDocument bookmark1 = new MutableDocument("bookmark1");
        bookmark1.setString("type", "bookmark");
        bookmark1.setString("title", "Bookmark For Hawaii");
        MutableArray hotels1 = new MutableArray();
        hotels1.addString("hotel1");
        hotels1.addString("hotel2");
        bookmark1.setArray("hotels", hotels1);
        db.save(bookmark1);

        MutableDocument bookmark2 = new MutableDocument("bookmark2");
        bookmark2.setString("type", "bookmark");
        bookmark2.setString("title", "Bookmark for New York");
        MutableArray hotels2 = new MutableArray();
        hotels2.addString("hotel3");
        bookmark2.setArray("hotels", hotels2);
        db.save(bookmark2);

        // Join Query
        DataSource mainDS = DataSource.database(this.db).as("main");
        DataSource secondaryDS = DataSource.database(this.db).as("secondary");

        Expression typeExpr = Expression.property("type").from("main");
        Expression hotelsExpr = Expression.property("hotels").from("main");
        Expression hotelIdExpr = Meta.id.from("secondary");
        Expression joinExpr = ArrayFunction.contains(hotelsExpr, hotelIdExpr);
        Join join = Join.join(secondaryDS).on(joinExpr);

        SelectResult srMainAll = SelectResult.all().from("main");
        SelectResult srSecondaryAll = SelectResult.all().from("secondary");
        Query query = QueryBuilder
            .select(srMainAll, srSecondaryAll)
            .from(mainDS)
            .join(join)
            .where(typeExpr.equalTo(Expression.string("bookmark")));

        verifyQuery(query, (n, result) -> Report.log(LogLevel.INFO, "RESULT: " + result.toMap()));
    }

    @Test
    public void testJoinWithEmptyArgs() {
        DataSource mainDS = DataSource.database(this.db).as("main");

        assertThrows(
            IllegalArgumentException.class,
            () -> QueryBuilder.select(SelectResult.all()).from(mainDS).join((Join[]) null));

        assertThrows(
            IllegalArgumentException.class,
            () -> QueryBuilder.select(SelectResult.all()).from(mainDS).where(null));

        assertThrows(
            IllegalArgumentException.class,
            () -> QueryBuilder.select(SelectResult.all()).from(mainDS).groupBy((Expression[]) null));

        assertThrows(
            IllegalArgumentException.class,
            () -> QueryBuilder.select(SelectResult.all()).from(mainDS).orderBy((Ordering[]) null));

        assertThrows(
            IllegalArgumentException.class,
            () -> QueryBuilder.select(SelectResult.all()).from(mainDS).limit(null));

        assertThrows(
            IllegalArgumentException.class,
            () -> QueryBuilder.select(SelectResult.all()).from(mainDS).limit(null, null));
    }

    //https://github.com/couchbase/couchbase-lite-android/issues/1785
    @Test
    public void testResultToMapWithBoolean() throws Exception {

        MutableDocument exam1 = new MutableDocument("exam1");
        exam1.setString("exam type", "final");
        exam1.setString("question", "There are 45 states in the US.");
        exam1.setBoolean("answer", false);
        MutableDocument exam2 = new MutableDocument("exam2");
        exam2.setString("exam type", "final");
        exam2.setString("question", "There are 100 senators in the US.");
        exam2.setBoolean("answer", true);

        db.save(exam1);
        db.save(exam2);

        Query query = QueryBuilder.select(SelectResult.all())
            .from(DataSource.database(db))
            .where(Expression.property("exam type").equalTo(Expression.string("final"))
                .and(Expression.property("answer").equalTo(Expression.booleanValue(true))));

        verifyQuery(query, (n, result) -> {
            Map<String, Object> maps = result.toMap();
            Map<String, Object> map = (Map<String, Object>) maps.get("testdb");
            if (map.get("question").equals("There are 45 states in the US.")) {
                assertFalse((Boolean) map.get("answer"));
            }
            if (map.get("question").equals("There are 100 senators in the US.")) {
                assertTrue((Boolean) map.get("answer"));
            }
        });
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1385
    @Test
    public void testQueryDeletedDocument() throws Exception {
        // STEP 1: Insert two documents
        Document task1 = createTaskDocument("Task 1", false);
        Document task2 = createTaskDocument("Task 2", false);
        assertEquals(2, db.getCount());

        // STEP 2: query documents before deletion
        Query query = QueryBuilder.select(SR_DOCID, SR_ALL)
            .from(DataSource.database(this.db))
            .where(Expression.property("type").equalTo(Expression.string("task")));

        int rows = verifyQuery(query, (n, result) -> { });
        assertEquals(2, rows);

        // STEP 3: delete task 1
        db.delete(task1);
        assertEquals(1, db.getCount());
        assertNull(db.getDocument(task1.getId()));

        // STEP 4: query documents again after deletion
        rows = verifyQuery(query, (n, result) -> assertEquals(task2.getId(), result.getString(0)));
        assertEquals(1, rows);
    }

    private Document createTaskDocument(String title, boolean complete) throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument();
        doc.setString("type", "task");
        doc.setString("title", title);
        doc.setBoolean("complete", complete);
        return save(doc);
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1389
    @Test
    public void testQueryWhereBooleanExpresion() throws Exception {
        // STEP 1: Insert two documents
        Document task1 = createTaskDocument("Task 1", false);
        Document task2 = createTaskDocument("Task 2", true);
        Document task3 = createTaskDocument("Task 3", true);
        assertEquals(3, db.getCount());

        Expression exprType = Expression.property("type");
        Expression exprComplete = Expression.property("complete");
        SelectResult srCount = SelectResult.expression(Function.count(Expression.intValue(1)));

        // regular query - true
        Query query = QueryBuilder.select(SR_ALL)
            .from(DataSource.database(db))
            .where(exprType.equalTo(Expression.string("task"))
                .and(exprComplete.equalTo(Expression.booleanValue(true))));

        int numRows = verifyQuery(query, false, (n, result) -> {
            Dictionary dict = result.getDictionary(db.getName());
            assertTrue(dict.getBoolean("complete"));
            assertEquals("task", dict.getString("type"));
            assertTrue(dict.getString("title").startsWith("Task "));
        });
        assertEquals(2, numRows);

        // regular query - false
        query = QueryBuilder.select(SR_ALL)
            .from(DataSource.database(db))
            .where(exprType.equalTo(Expression.string("task"))
                .and(exprComplete.equalTo(Expression.booleanValue(false))));

        numRows = verifyQuery(query, false, (n, result) -> {
            Dictionary dict = result.getDictionary(db.getName());
            assertFalse(dict.getBoolean("complete"));
            assertEquals("task", dict.getString("type"));
            assertTrue(dict.getString("title").startsWith("Task "));
        });
        assertEquals(1, numRows);

        // aggregation query - true
        query = QueryBuilder.select(srCount)
            .from(DataSource.database(db))
            .where(exprType.equalTo(Expression.string("task"))
                .and(exprComplete.equalTo(Expression.booleanValue(true))));

        numRows = verifyQuery(query, false, (n, result) -> assertEquals(2, result.getInt(0)));
        assertEquals(1, numRows);

        // aggregation query - false
        query = QueryBuilder.select(srCount)
            .from(DataSource.database(db))
            .where(exprType.equalTo(Expression.string("task"))
                .and(exprComplete.equalTo(Expression.booleanValue(false))));

        numRows = verifyQuery(query, false, (n, result) -> assertEquals(1, result.getInt(0)));
        assertEquals(1, numRows);
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1413
    @Test
    public void testJoinAll() throws Exception {
        loadNumberedDocs(100);

        final MutableDocument doc1 = new MutableDocument("joinme");
        doc1.setValue("theone", 42);
        save(doc1);

        DataSource mainDS = DataSource.database(this.db).as("main");
        DataSource secondaryDS = DataSource.database(this.db).as("secondary");

        Expression mainPropExpr = Expression.property("number1").from("main");
        Expression secondaryExpr = Expression.property("theone").from("secondary");
        Expression joinExpr = mainPropExpr.equalTo(secondaryExpr);
        Join join = Join.join(secondaryDS).on(joinExpr);

        SelectResult MAIN_ALL = SelectResult.all().from("main");
        SelectResult SECOND_ALL = SelectResult.all().from("secondary");

        Query query = QueryBuilder.select(MAIN_ALL, SECOND_ALL).from(mainDS).join(join);

        int numRows = verifyQuery(query, (n, result) -> {
            Dictionary mainAll1 = result.getDictionary(0);
            Dictionary mainAll2 = result.getDictionary("main");
            Dictionary secondAll1 = result.getDictionary(1);
            Dictionary secondAll2 = result.getDictionary("secondary");
            assertEquals(42, mainAll1.getInt("number1"));
            assertEquals(42, mainAll2.getInt("number1"));
            assertEquals(58, mainAll1.getInt("number2"));
            assertEquals(58, mainAll2.getInt("number2"));
            assertEquals(42, secondAll1.getInt("theone"));
            assertEquals(42, secondAll2.getInt("theone"));
        });
        assertEquals(1, numRows);
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1413
    @Test
    public void testJoinByDocID() throws Exception {
        loadNumberedDocs(100);

        final MutableDocument doc1 = new MutableDocument("joinme");
        doc1.setValue("theone", 42);
        doc1.setString("numberID", "doc1"); // document ID of number documents.
        save(doc1);

        DataSource mainDS = DataSource.database(this.db).as("main");
        DataSource secondaryDS = DataSource.database(this.db).as("secondary");

        Expression mainPropExpr = Meta.id.from("main");
        Expression secondaryExpr = Expression.property("numberID").from("secondary");
        Expression joinExpr = mainPropExpr.equalTo(secondaryExpr);
        Join join = Join.join(secondaryDS).on(joinExpr);

        SelectResult MAIN_DOC_ID = SelectResult.expression(Meta.id.from("main")).as("mainDocID");
        SelectResult SECONDARY_DOC_ID = SelectResult.expression(Meta.id.from("secondary")).as("secondaryDocID");
        SelectResult SECONDARY_THEONE = SelectResult.expression(Expression.property("theone").from("secondary"));

        Query query = QueryBuilder.select(MAIN_DOC_ID, SECONDARY_DOC_ID, SECONDARY_THEONE).from(mainDS).join(join);

        int numRows = verifyQuery(query, (n, result) -> {
            assertEquals(1, n);
            String docID = result.getString("mainDocID");
            Document doc = db.getDocument(docID);
            assertEquals(1, doc.getInt("number1"));
            assertEquals(99, doc.getInt("number2"));

            // data from secondary
            assertEquals("joinme", result.getString("secondaryDocID"));
            assertEquals(42, result.getInt("theone"));
        });
        assertEquals(1, numRows);
    }

    @Test
    public void testGenerateJSONCollation() {
        Collation[] collations = {
            Collation.ascii().ignoreCase(false),
            Collation.ascii().ignoreCase(true),
            Collation.unicode().locale(null).ignoreCase(false).ignoreAccents(false),
            Collation.unicode().locale(null).ignoreCase(true).ignoreAccents(false),
            Collation.unicode().locale(null).ignoreCase(true).ignoreAccents(true),
            Collation.unicode().locale("en").ignoreCase(false).ignoreAccents(false),
            Collation.unicode().locale("en").ignoreCase(true).ignoreAccents(false),
            Collation.unicode().locale("en").ignoreCase(true).ignoreAccents(true)
        };

        List<Map<String, Object>> expected = new ArrayList<>();
        Map<String, Object> json1 = new HashMap<>();
        json1.put("UNICODE", false);
        json1.put("LOCALE", null);
        json1.put("CASE", true);
        json1.put("DIAC", true);
        expected.add(json1);
        Map<String, Object> json2 = new HashMap<>();
        json2.put("UNICODE", false);
        json2.put("LOCALE", null);
        json2.put("CASE", false);
        json2.put("DIAC", true);
        expected.add(json2);
        Map<String, Object> json3 = new HashMap<>();
        json3.put("UNICODE", true);
        json3.put("LOCALE", null);
        json3.put("CASE", true);
        json3.put("DIAC", true);
        expected.add(json3);
        Map<String, Object> json4 = new HashMap<>();
        json4.put("UNICODE", true);
        json4.put("LOCALE", null);
        json4.put("CASE", false);
        json4.put("DIAC", true);
        expected.add(json4);
        Map<String, Object> json5 = new HashMap<>();
        json5.put("UNICODE", true);
        json5.put("LOCALE", null);
        json5.put("CASE", false);
        json5.put("DIAC", false);
        expected.add(json5);
        Map<String, Object> json6 = new HashMap<>();
        json6.put("UNICODE", true);
        json6.put("LOCALE", "en");
        json6.put("CASE", true);
        json6.put("DIAC", true);
        expected.add(json6);
        Map<String, Object> json7 = new HashMap<>();
        json7.put("UNICODE", true);
        json7.put("LOCALE", "en");
        json7.put("CASE", false);
        json7.put("DIAC", true);
        expected.add(json7);
        Map<String, Object> json8 = new HashMap<>();
        json8.put("UNICODE", true);
        json8.put("LOCALE", "en");
        json8.put("CASE", false);
        json8.put("DIAC", false);
        expected.add(json8);

        for (int i = 0; i < collations.length; i++) { assertEquals(expected.get(i), collations[i].asJSON()); }
    }

    @Test
    public void testAllComparison() throws Exception {
        String[] values = {"Apple", "Aardvark", "Ångström", "Zebra", "äpple"};
        for (String value : values) {
            MutableDocument doc = new MutableDocument();
            doc.setString("hey", value);
            save(doc);
        }
        List<List<Object>> testData = new ArrayList<>();
        testData.add(Arrays.asList("BINARY collation", Collation.ascii(),
            Arrays.asList("Aardvark", "Apple", "Zebra", "Ångström", "äpple")));
        testData.add(Arrays.asList("NOCASE collation", Collation.ascii().ignoreCase(true),
            Arrays.asList("Aardvark", "Apple", "Zebra", "Ångström", "äpple")));
        testData.add(Arrays.asList(
            "Unicode case-sensitive, diacritic-sensitive collation",
            Collation.unicode(),
            Arrays.asList("Aardvark", "Ångström", "Apple", "äpple", "Zebra")));
        testData.add(Arrays.asList(
            "Unicode case-INsensitive, diacritic-sensitive collation",
            Collation.unicode().ignoreCase(true),
            Arrays.asList("Aardvark", "Ångström", "Apple", "äpple", "Zebra")));
        testData.add(Arrays.asList(
            "Unicode case-sensitive, diacritic-INsensitive collation",
            Collation.unicode().ignoreAccents(true),
            Arrays.asList("Aardvark", "Ångström", "äpple", "Apple", "Zebra")));
        testData.add(Arrays.asList(
            "Unicode case-INsensitive, diacritic-INsensitive collation",
            Collation.unicode().ignoreAccents(true).ignoreCase(true),
            Arrays.asList("Aardvark", "Ångström", "Apple", "äpple", "Zebra")));

        Expression property = Expression.property("hey");
        for (List<Object> data : testData) {
            Query query = QueryBuilder.select(SelectResult.property("hey"))
                .from(DataSource.database(db))
                .orderBy(Ordering.expression(property.collate((Collation) data.get(1))));

            final List<String> list = new ArrayList<>();
            verifyQuery(query, false, (n, result) -> list.add(result.getString(0)));
            assertEquals(data.get(2), list);
        }
    }

    @Test
    public void testCloseDatabaseWithActiveLiveQuery() throws CouchbaseLiteException, InterruptedException {
        final CountDownLatch latch1 = new CountDownLatch(1);
        Query query = QueryBuilder.select(SelectResult.expression(Meta.id))
            .from(DataSource.database(db));

        ListenerToken token = query.addChangeListener(executor, change -> latch1.countDown());
        assertTrue(latch1.await(2, TimeUnit.SECONDS));

        try {
            db.close();
            fail();
        }
        catch (CouchbaseLiteException e) {
            assertEquals(CBLError.Domain.CBLITE, e.getDomain());
            assertEquals(CBLError.Code.BUSY, e.getCode());
        }
        finally {
            query.removeChangeListener(token);
        }
    }

    @Test
    public void testFunctionCount() throws Exception {
        loadNumberedDocs(100);

        final MutableDocument doc = new MutableDocument();
        doc.setValue("string", "STRING");
        doc.setValue("date", null);
        save(doc);

        DataSource ds = DataSource.database(this.db);
        Expression cntNum1 = Function.count(EXPR_NUMBER1);
        Expression cntInt1 = Function.count(Expression.intValue(1));
        Expression cntAstr = Function.count(Expression.string("*"));
        Expression cntAll = Function.count(Expression.all());
        Expression cntStr = Function.count(Expression.property("string"));
        Expression cntDate = Function.count(Expression.property("date"));
        Expression cntNotExist = Function.count(Expression.property("notExist"));

        SelectResult rsCntNum1 = SelectResult.expression(cntNum1);
        SelectResult rsCntInt1 = SelectResult.expression(cntInt1);
        SelectResult rsCntAstr = SelectResult.expression(cntAstr);
        SelectResult rsCntAll = SelectResult.expression(cntAll);
        SelectResult rsCntStr = SelectResult.expression(cntStr);
        SelectResult rsCntDate = SelectResult.expression(cntDate);
        SelectResult rsCntNotExist = SelectResult.expression(cntNotExist);

        Query query = QueryBuilder
            .select(rsCntNum1, rsCntInt1, rsCntAstr, rsCntAll, rsCntStr, rsCntDate, rsCntNotExist)
            .from(ds);

        int numRows = verifyQuery(query, (n, result) -> {
            assertEquals(100L, (long) result.getValue(0));
            assertEquals(101L, (long) result.getValue(1));
            assertEquals(101L, (long) result.getValue(2));
            assertEquals(101L, (long) result.getValue(3));
            assertEquals(1L, (long) result.getValue(4));
            assertEquals(1L, (long) result.getValue(5));
            assertEquals(0L, (long) result.getValue(6));
        });
        assertEquals(1, numRows);
    }

    @Test
    public void testFunctionCountAll() throws Exception {
        loadNumberedDocs(100);

        DataSource.As ds = DataSource.database(db);

        Expression countAll = Function.count(Expression.all());
        Expression countAllFrom = Function.count(Expression.all().from("testdb"));
        SelectResult srCountAll = SelectResult.expression(countAll);
        SelectResult srCountAllFrom = SelectResult.expression(countAllFrom);

        // SELECT count(*)
        Query query = QueryBuilder.select(srCountAll).from(ds);
        int numRows = verifyQuery(query, (n, result) -> {
            assertEquals(1, result.count());
            assertEquals(100L, (long) result.getValue(0));
        });
        assertEquals(1, numRows);

        // SELECT count(testdb.*)
        query = QueryBuilder.select(srCountAllFrom).from(ds.as("testdb"));
        numRows = verifyQuery(query, (n, result) -> {
            assertEquals(1, result.count());
            assertEquals(100L, (long) result.getValue(0));
        });
        assertEquals(1, numRows);
    }

    @Test
    public void testResultSetEnumeration() throws Exception {
        loadNumberedDocs(5);

        Query query = QueryBuilder.select(SelectResult.expression(Meta.id))
            .from(DataSource.database(db))
            .orderBy(Ordering.property("number1"));

        // Type 1: Enumeration by ResultSet.next()
        int i = 0;
        Result result;
        ResultSet rs = query.execute();
        while ((result = rs.next()) != null) {
            assertEquals(String.format(Locale.ENGLISH, "doc%d", i + 1), result.getString(0));
            i++;
        }
        assertEquals(5, i);
        assertNull(rs.next());
        assertEquals(0, rs.allResults().size());

        // Type 2: Enumeration by ResultSet.iterator()
        i = 0;
        rs = query.execute();
        for (Result r : rs) {
            assertEquals(String.format(Locale.ENGLISH, "doc%d", i + 1), r.getString(0));
            i++;
        }
        assertEquals(5, i);
        assertNull(rs.next());
        assertEquals(0, rs.allResults().size());

        // Type 3: Enumeration by ResultSet.allResults().get(int index)
        i = 0;
        rs = query.execute();
        List<Result> list = rs.allResults();
        for (int j = 0; j < list.size(); j++) {
            Result r = list.get(j);
            assertEquals(String.format(Locale.ENGLISH, "doc%d", i + 1), r.getString(0));
            i++;
        }
        assertEquals(5, i);
        assertNull(rs.next());
        assertEquals(0, rs.allResults().size());

        // Type 4: Enumeration by ResultSet.allResults().iterator()
        i = 0;
        rs = query.execute();
        Iterator<Result> itr = rs.allResults().iterator();
        while (itr.hasNext()) {
            Result r = itr.next();
            assertEquals(String.format(Locale.ENGLISH, "doc%d", i + 1), r.getString(0));
            i++;
        }
        assertEquals(5, i);
        assertNull(rs.next());
        assertEquals(0, rs.allResults().size());
    }

    @Test
    public void testGetAllResults() throws Exception {
        loadNumberedDocs(5);

        Query query = QueryBuilder.select(SelectResult.expression(Meta.id))
            .from(DataSource.database(db))
            .orderBy(Ordering.property("number1"));

        // Get all results by get(int)
        int i = 0;
        ResultSet rs = query.execute();
        List<Result> results = rs.allResults();
        for (int j = 0; j < results.size(); j++) {
            Result r = results.get(j);
            assertEquals(String.format(Locale.ENGLISH, "doc%d", i + 1), r.getString(0));
            i++;
        }
        assertEquals(5, results.size());
        assertEquals(5, i);
        assertNull(rs.next());
        assertEquals(0, rs.allResults().size());

        // Get all results by iterator
        i = 0;
        rs = query.execute();
        results = rs.allResults();
        for (Result r : results) {
            assertEquals(String.format(Locale.ENGLISH, "doc%d", i + 1), r.getString(0));
            i++;
        }
        assertEquals(5, results.size());
        assertEquals(5, i);
        assertNull(rs.next());
        assertEquals(0, rs.allResults().size());

        // Partial enumerating then get all results:
        rs = query.execute();
        assertNotNull(rs.next());
        assertNotNull(rs.next());
        results = rs.allResults();
        i = 2;
        for (Result r : results) {
            assertEquals(String.format(Locale.ENGLISH, "doc%d", i + 1), r.getString(0));
            i++;
        }
        assertEquals(3, results.size());
        assertEquals(5, i);
        assertNull(rs.next());
        assertEquals(0, rs.allResults().size());
    }

    @Test
    public void testResultSetEnumerationZeroResults() throws Exception {
        loadNumberedDocs(5);

        Query query = QueryBuilder.select(SelectResult.expression(Meta.id))
            .from(DataSource.database(db))
            .where(Expression.property("number1").is(Expression.intValue(100)))
            .orderBy(Ordering.property("number1"));

        // Type 1: Enumeration by ResultSet.next()
        int i = 0;
        Result result;
        ResultSet rs = query.execute();
        while ((result = rs.next()) != null) {
            i++;
        }
        assertEquals(0, i);
        assertNull(rs.next());
        assertEquals(0, rs.allResults().size());

        // Type 2: Enumeration by ResultSet.iterator()
        i = 0;
        rs = query.execute();
        for (Result r : rs) {
            i++;
        }
        assertEquals(0, i);
        assertNull(rs.next());
        assertEquals(0, rs.allResults().size());

        // Type 3: Enumeration by ResultSet.allResults().get(int index)
        i = 0;
        rs = query.execute();
        List<Result> list = rs.allResults();
        for (int j = 0; j < list.size(); j++) {
            Result r = list.get(j);
            i++;
        }
        assertEquals(0, i);
        assertNull(rs.next());
        assertEquals(0, rs.allResults().size());

        // Type 4: Enumeration by ResultSet.allResults().iterator()
        i = 0;
        rs = query.execute();
        Iterator<Result> itr = rs.allResults().iterator();
        while (itr.hasNext()) {
            Result r = itr.next();
            i++;
        }
        assertEquals(0, i);
        assertNull(rs.next());
        assertEquals(0, rs.allResults().size());
    }

    @Test
    public void testMissingValue() throws Exception {
        MutableDocument doc1 = new MutableDocument("doc1");
        doc1.setValue("name", "Scott");
        doc1.setValue("address", null);
        save(doc1);

        Query query = QueryBuilder.select(
            SelectResult.property("name"),
            SelectResult.property("address"),
            SelectResult.property("age"))
            .from(DataSource.database(db));

        // Array:
        verifyQuery(query, (n, result) -> {
            assertEquals(3, result.count());
            assertEquals("Scott", result.getString(0));
            assertNull(result.getValue(1));
            assertNull(result.getValue(2));
            assertEquals(Arrays.asList("Scott", null, null), result.toList());
        });

        // Dictionary:
        verifyQuery(query, (n, result) -> {
            assertEquals("Scott", result.getString("name"));
            assertNull(result.getString("address"));
            assertTrue(result.contains("address"));
            assertNull(result.getString("age"));
            assertFalse(result.contains("age"));
            Map<String, Object> expected = new HashMap<>();
            expected.put("name", "Scott");
            expected.put("address", null);
            assertEquals(expected, result.toMap());
        });
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1603
    @Test
    public void testExpressionNot() throws Exception {
        loadNumberedDocs(10);

        Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id), SelectResult.property("number1"))
            .from(DataSource.database(db))
            .where(Expression.not(Expression.property("number1")
                .between(Expression.intValue(3), Expression.intValue(5))))
            .orderBy(Ordering.expression(Expression.property("number1")).ascending());

        int numRows = verifyQuery(query, (n, result) -> {
            if (n < 3) { assertEquals(n, result.getInt("number1")); }
            else { assertEquals(n + 3, result.getInt("number1")); }
        });
        assertEquals(7, numRows);
    }

    @Test
    public void testLimitValueIsLargerThanResult() throws Exception {
        final int N = 4;
        loadNumberedDocs(N);

        Query query = QueryBuilder
            .select(SelectResult.all())
            .from(DataSource.database(db))
            .limit(Expression.intValue(10));

        int numRows = verifyQuery(query, (n, result) -> { });
        assertEquals(N, numRows);
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1614
    @Test
    public void testFTSStemming() throws Exception {
        MutableDocument mDoc0 = new MutableDocument("doc0");
        mDoc0.setString("content", "hello");
        save(mDoc0);

        MutableDocument mDoc1 = new MutableDocument("doc1");
        mDoc1.setString("content", "beauty");
        save(mDoc1);

        MutableDocument mDoc2 = new MutableDocument("doc2");
        mDoc2.setString("content", "beautifully");
        save(mDoc2);

        MutableDocument mDoc3 = new MutableDocument("doc3");
        mDoc3.setString("content", "beautiful");
        save(mDoc3);

        MutableDocument mDoc4 = new MutableDocument("doc4");
        mDoc4.setString("content", "pretty");
        save(mDoc4);

        FullTextIndex ftsIndex = IndexBuilder.fullTextIndex(FullTextIndexItem.property("content"));
        db.createIndex("ftsIndex", ftsIndex);

        String[] expectedIDs = {"doc1", "doc2", "doc3"};
        String[] expectedContents = {"beauty", "beautifully", "beautiful"};

        Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id), SelectResult.property("content"))
            .from(DataSource.database(db))
            .where(FullTextExpression.index("ftsIndex").match("beautiful"))
            .orderBy(Ordering.expression(Meta.id));

        int numRows = verifyQuery(query, (n, result) -> {
            assertEquals(expectedIDs[n - 1], result.getString("id"));
            assertEquals(expectedContents[n - 1], result.getString("content"));
        });
        assertEquals(3, numRows);
    }

    // https://github.com/couchbase/couchbase-lite-net/blob/master/src/Couchbase.Lite.Tests.Shared/QueryTest.cs#L1721
    @Test
    public void testFTSStemming2() throws Exception {
        db.createIndex(
            "passageIndex",
            IndexBuilder.fullTextIndex(FullTextIndexItem.property("passage")).setLanguage("en"));
        db.createIndex(
            "passageIndexStemless",
            IndexBuilder.fullTextIndex(FullTextIndexItem.property("passage")).setLanguage(null));

        MutableDocument mDoc1 = new MutableDocument("doc1");
        mDoc1.setString("passage", "The boy said to the child, 'Mommy, I want a cat.'");
        save(mDoc1);

        MutableDocument mDoc2 = new MutableDocument("doc2");
        mDoc2.setString("passage", "The mother replied 'No, you already have too many cats.'");
        save(mDoc2);

        Query query = QueryBuilder.select(SelectResult.expression(Meta.id))
            .from(DataSource.database(db))
            .where(FullTextExpression.index("passageIndex").match("cat"));

        int numRows = verifyQuery(query, (n, result) -> {
            String id = String.format(Locale.ENGLISH, "doc%d", n);
            assertEquals(id, result.getString(0));
        });
        assertEquals(2, numRows);

        query = QueryBuilder.select(SelectResult.expression(Meta.id))
            .from(DataSource.database(db))
            .where(FullTextExpression.index("passageIndexStemless").match("cat"));

        numRows = verifyQuery(query, (n, result) -> {
            String id = String.format(Locale.ENGLISH, "doc%d", n);
            assertEquals(id, result.getString(0));
        });
        assertEquals(1, numRows);
    }

    // 3.1. Set Operations Using The Enhanced Query Syntax
    // https://www.sqlite.org/fts3.html#_set_operations_using_the_enhanced_query_syntax
    // https://github.com/couchbase/couchbase-lite-android/issues/1620
    @Test
    public void testFTSSetOperations() throws Exception {
        MutableDocument mDoc1 = new MutableDocument("doc1");
        mDoc1.setString("content", "a database is a software system");
        save(mDoc1);

        MutableDocument mDoc2 = new MutableDocument("doc2");
        mDoc2.setString("content", "sqlite is a software system");
        save(mDoc2);

        MutableDocument mDoc3 = new MutableDocument("doc3");
        mDoc3.setString("content", "sqlite is a database");
        save(mDoc3);

        FullTextIndex ftsIndex = IndexBuilder.fullTextIndex(FullTextIndexItem.property("content"));
        db.createIndex("ftsIndex", ftsIndex);

        // The enhanced query syntax
        // https://www.sqlite.org/fts3.html#_set_operations_using_the_enhanced_query_syntax

        // AND binary set operator
        Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id), SelectResult.property("content"))
            .from(DataSource.database(db))
            .where(FullTextExpression.index("ftsIndex").match("sqlite AND database"))
            .orderBy(Ordering.expression(Meta.id));

        final String[] expectedIDs = {"doc3"};
        int numRows = verifyQuery(query, (n, result) -> assertEquals(expectedIDs[n - 1], result.getString("id")));
        assertEquals(expectedIDs.length, numRows);

        // implicit AND operator
        query = QueryBuilder
            .select(SelectResult.expression(Meta.id), SelectResult.property("content"))
            .from(DataSource.database(db))
            .where(FullTextExpression.index("ftsIndex").match("sqlite database"))
            .orderBy(Ordering.expression(Meta.id));

        final String[] expectedIDs2 = {"doc3"};
        numRows = verifyQuery(query, (n, result) -> assertEquals(expectedIDs2[n - 1], result.getString("id")));
        assertEquals(expectedIDs2.length, numRows);

        // OR operator
        query = QueryBuilder
            .select(SelectResult.expression(Meta.id), SelectResult.property("content"))
            .from(DataSource.database(db))
            .where(FullTextExpression.index("ftsIndex").match("sqlite OR database"))
            .orderBy(Ordering.expression(Meta.id));

        String[] expectedIDs3 = {"doc1", "doc2", "doc3"};
        numRows = verifyQuery(query, (n, result) -> assertEquals(expectedIDs3[n - 1], result.getString("id")));
        assertEquals(expectedIDs3.length, numRows);

        // NOT operator
        query = QueryBuilder.select(SelectResult.expression(Meta.id), SelectResult.property("content"))
            .from(DataSource.database(db))
            .where(FullTextExpression.index("ftsIndex").match("database NOT sqlite"))
            .orderBy(Ordering.expression(Meta.id));

        String[] expectedIDs4 = {"doc1"};
        numRows = verifyQuery(query, (n, result) -> assertEquals(expectedIDs4[n - 1], result.getString("id")));
        assertEquals(expectedIDs4.length, numRows);
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1621
    @Test
    public void testFTSMixedOperators() throws Exception {
        MutableDocument mDoc1 = new MutableDocument("doc1");
        mDoc1.setString("content", "a database is a software system");
        save(mDoc1);

        MutableDocument mDoc2 = new MutableDocument("doc2");
        mDoc2.setString("content", "sqlite is a software system");
        save(mDoc2);

        MutableDocument mDoc3 = new MutableDocument("doc3");
        mDoc3.setString("content", "sqlite is a database");
        save(mDoc3);

        FullTextIndex ftsIndex = IndexBuilder.fullTextIndex(FullTextIndexItem.property("content"));
        db.createIndex("ftsIndex", ftsIndex);

        // The enhanced query syntax
        // https://www.sqlite.org/fts3.html#_set_operations_using_the_enhanced_query_syntax

        // A AND B AND C
        Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id), SelectResult.property("content"))
            .from(DataSource.database(db))
            .where(FullTextExpression.index("ftsIndex").match("sqlite AND software AND system"))
            .orderBy(Ordering.expression(Meta.id));

        String[] expectedIDs = {"doc2"};
        int numRows = verifyQuery(query, (n, result) -> assertEquals(expectedIDs[n - 1], result.getString("id")));
        assertEquals(expectedIDs.length, numRows);

        // (A AND B) OR C
        query = QueryBuilder.select(SelectResult.expression(Meta.id), SelectResult.property("content"))
            .from(DataSource.database(db))
            .where(FullTextExpression.index("ftsIndex").match("(sqlite AND software) OR database"))
            .orderBy(Ordering.expression(Meta.id));

        String[] expectedIDs2 = {"doc1", "doc2", "doc3"};
        numRows = verifyQuery(query, (n, result) -> assertEquals(expectedIDs2[n - 1], result.getString("id")));
        assertEquals(expectedIDs2.length, numRows);

        query = QueryBuilder.select(SelectResult.expression(Meta.id), SelectResult.property("content"))
            .from(DataSource.database(db))
            .where(FullTextExpression.index("ftsIndex").match("(sqlite AND software) OR system"))
            .orderBy(Ordering.expression(Meta.id));

        String[] expectedIDs3 = {"doc1", "doc2"};
        numRows = verifyQuery(query, (n, result) -> assertEquals(expectedIDs3[n - 1], result.getString("id")));
        assertEquals(expectedIDs3.length, numRows);

        // (A OR B) AND C
        query = QueryBuilder.select(SelectResult.expression(Meta.id), SelectResult.property("content"))
            .from(DataSource.database(db))
            .where(FullTextExpression.index("ftsIndex").match("(sqlite OR software) AND database"))
            .orderBy(Ordering.expression(Meta.id));

        String[] expectedIDs4 = {"doc1", "doc3"};
        numRows = verifyQuery(query, (n, result) -> assertEquals(expectedIDs4[n - 1], result.getString("id")));
        assertEquals(expectedIDs4.length, numRows);

        query = QueryBuilder.select(SelectResult.expression(Meta.id), SelectResult.property("content"))
            .from(DataSource.database(db))
            .where(FullTextExpression.index("ftsIndex").match("(sqlite OR software) AND system"))
            .orderBy(Ordering.expression(Meta.id));

        String[] expectedIDs5 = {"doc1", "doc2"};
        numRows = verifyQuery(query, (n, result) -> assertEquals(expectedIDs5[n - 1], result.getString("id")));
        assertEquals(expectedIDs5.length, numRows);

        // A OR B OR C
        query = QueryBuilder.select(SelectResult.expression(Meta.id), SelectResult.property("content"))
            .from(DataSource.database(db))
            .where(FullTextExpression.index("ftsIndex").match("database OR software OR system"))
            .orderBy(Ordering.expression(Meta.id));

        String[] expectedIDs6 = {"doc1", "doc2", "doc3"};
        numRows = verifyQuery(query, (n, result) -> assertEquals(expectedIDs6[n - 1], result.getString("id")));
        assertEquals(expectedIDs6.length, numRows);
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1628
    @Test
    public void testLiveQueryResultsCount() throws Exception {
        loadNumberedDocs(50);

        Query query = QueryBuilder
            .select()
            .from(DataSource.database(db))
            .where(EXPR_NUMBER1.greaterThan(Expression.intValue(25)))
            .orderBy(Ordering.property("number1").ascending());

        final CountDownLatch latch = new CountDownLatch(1);
        QueryChangeListener listener = change -> {
            int count = 0;
            ResultSet rs = change.getResults();
            while (rs.next() != null) { count++; }
            if (count == 75) { latch.countDown(); } // 26-100
        };
        ListenerToken token = query.addChangeListener(executor, listener);

        try {
            // create one doc
            final CountDownLatch latchAdd = new CountDownLatch(1);
            executeAsync(500, () -> {
                try {
                    loadNumberedDocs(51, 100);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
                latchAdd.countDown();
            });
            assertTrue(latchAdd.await(20, TimeUnit.SECONDS));
            assertTrue(latch.await(20, TimeUnit.SECONDS));
        }
        finally {
            query.removeChangeListener(token);
        }
    }

    // https://forums.couchbase.com/t/
    //     how-to-be-notifed-that-docuemnt-is-changed-but-livequerys-query-isnt-catching-it-anymore/16199/9
    @Test
    public void testLiveQueryNotification() throws CouchbaseLiteException, InterruptedException {
        // save doc1 with number1 -> 5
        MutableDocument doc = new MutableDocument("doc1");
        doc.setInt("number1", 5);
        db.save(doc);

        Query query = QueryBuilder.select(SelectResult.expression(Meta.id), SelectResult.property("number1"))
            .from(DataSource.database(db))
            .where(Expression.property("number1").lessThan(Expression.intValue(10)))
            .orderBy(Ordering.property("number1"));

        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);
        ListenerToken token = query.addChangeListener(executor, change -> {
            int matchs = 0;
            ResultSet rs = change.getResults();
            for (Result r : rs) {
                matchs++;
            }
            // match doc1 with number1 -> 5 which is less than 10
            if (matchs == 1) { latch1.countDown(); }
            // Not match with doc1 because number1 -> 15 which does not quarify the query criteria
            else { latch2.countDown(); }
        });

        try {
            assertTrue(latch1.await(5, TimeUnit.SECONDS));

            doc = db.getDocument("doc1").toMutable();
            doc.setInt("number1", 15);
            db.save(doc);

            assertTrue(latch2.await(5, TimeUnit.SECONDS));
        }
        finally {
            query.removeChangeListener(token);
        }
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1689
    @Test
    public void testQueryAndNLikeOperators() throws Exception {
        MutableDocument mDoc1 = new MutableDocument("doc1");
        mDoc1.setString("name", "food");
        mDoc1.setString("description", "bar");
        save(mDoc1);

        MutableDocument mDoc2 = new MutableDocument("doc2");
        mDoc2.setString("name", "foo");
        mDoc2.setString("description", "unknown");
        save(mDoc2);

        MutableDocument mDoc3 = new MutableDocument("doc3");
        mDoc3.setString("name", "water");
        mDoc3.setString("description", "drink");
        save(mDoc3);

        MutableDocument mDoc4 = new MutableDocument("doc4");
        mDoc4.setString("name", "chocolate");
        mDoc4.setString("description", "bar");
        save(mDoc4);

        // LIKE operator only
        Query query = QueryBuilder.select(SelectResult.expression(Meta.id))
            .from(DataSource.database(db))
            .where(Expression.property("name").like(Expression.string("%foo%")))
            .orderBy(Ordering.expression(Meta.id));

        int numRows = verifyQuery(query, (n, result) -> {
            assertEquals(1, result.count());
            if (n == 1) { assertEquals("doc1", result.getString(0)); }
            else { assertEquals("doc2", result.getString(0)); }

        });
        assertEquals(2, numRows);

        // EQUAL operator only
        query = QueryBuilder.select(SelectResult.expression(Meta.id))
            .from(DataSource.database(db))
            .where(Expression.property("description").equalTo(Expression.string("bar")))
            .orderBy(Ordering.expression(Meta.id));

        numRows = verifyQuery(query, (n, result) -> {
            assertEquals(1, result.count());
            if (n == 1) { assertEquals("doc1", result.getString(0)); }
            else { assertEquals("doc4", result.getString(0)); }

        });
        assertEquals(2, numRows);

        // AND and LIKE operators
        query = QueryBuilder.select(SelectResult.expression(Meta.id))
            .from(DataSource.database(db))
            .where(Expression.property("name").like(Expression.string("%foo%"))
                .and(Expression.property("description").equalTo(Expression.string("bar"))))
            .orderBy(Ordering.expression(Meta.id));

        numRows = verifyQuery(query, (n, result) -> {
            assertEquals(1, result.count());
            assertEquals("doc1", result.getString(0));
        });
        assertEquals(1, numRows);
    }

    // https://forums.couchbase.com/t/
    //     how-to-implement-an-index-join-clause-in-couchbase-lite-2-0-using-objective-c-api/16246
    // https://github.com/couchbase/couchbase-lite-core/issues/497
    @Test
    public void testQueryJoinAndSelectAll() throws Exception {
        loadNumberedDocs(100);

        final MutableDocument joinme = new MutableDocument("joinme");
        joinme.setValue("theone", 42);
        save(joinme);

        DataSource mainDS = DataSource.database(this.db).as("main");
        DataSource secondaryDS = DataSource.database(this.db).as("secondary");

        Expression mainPropExpr = Expression.property("number1").from("main");
        Expression secondaryExpr = Expression.property("theone").from("secondary");
        Expression joinExpr = mainPropExpr.equalTo(secondaryExpr);
        Join join = Join.leftJoin(secondaryDS).on(joinExpr);

        SelectResult sr1 = SelectResult.all().from("main");
        SelectResult sr2 = SelectResult.all().from("secondary");

        Query query = QueryBuilder.select(sr1, sr2).from(mainDS).join(join);

        int numRows = verifyQuery(query, (n, result) -> {
            if (n == 41) {
                assertEquals(59, result.getDictionary("main").getInt("number2"));
                assertNull(result.getDictionary("secondary"));
            }
            if (n == 42) {
                assertEquals(58, result.getDictionary("main").getInt("number2"));
                assertEquals(42, result.getDictionary("secondary").getInt("theone"));
            }
        });
        assertEquals(101, numRows);
    }

    @Test
    public void testResultSetAllResults() throws Exception {
        MutableDocument doc1a = new MutableDocument("doc1");
        doc1a.setInt("answer", 42);
        doc1a.setString("a", "string");
        db.save(doc1a);

        Query query = QueryBuilder.select(SR_DOCID, SR_DELETED)
            .from(DataSource.database(db))
            .where(Meta.id.equalTo(Expression.string("doc1")));

        ResultSet rs = query.execute();
        List<Result> results = rs.allResults();
        assertEquals(1, results.size());

        results = rs.allResults();
        assertEquals(0, results.size());
    }

    @Test
    public void testAggregateFunctionEmptyArgs() {
        assertThrows(IllegalArgumentException.class, () -> Function.avg(null));

        assertThrows(IllegalArgumentException.class, () -> Function.count(null));

        assertThrows(IllegalArgumentException.class, () -> Function.min(null));

        assertThrows(IllegalArgumentException.class, () -> Function.max(null));

        assertThrows(IllegalArgumentException.class, () -> Function.sum(null));
    }

    @Test
    public void testMathFunctionEmptyArgs() {
        assertThrows(IllegalArgumentException.class, () -> Function.abs(null));

        assertThrows(IllegalArgumentException.class, () -> Function.acos(null));

        assertThrows(IllegalArgumentException.class, () -> Function.asin(null));

        assertThrows(IllegalArgumentException.class, () -> Function.atan(null));

        assertThrows(IllegalArgumentException.class, () -> Function.atan2(null, Expression.doubleValue(0.7)));

        assertThrows(IllegalArgumentException.class, () -> Function.atan2(Expression.doubleValue(0.7), null));

        assertThrows(IllegalArgumentException.class, () -> Function.ceil(null));

        assertThrows(IllegalArgumentException.class, () -> Function.cos(null));

        assertThrows(IllegalArgumentException.class, () -> Function.degrees(null));

        assertThrows(IllegalArgumentException.class, () -> Function.exp(null));

        assertThrows(IllegalArgumentException.class, () -> Function.floor(null));

        assertThrows(IllegalArgumentException.class, () -> Function.ln(null));

        assertThrows(IllegalArgumentException.class, () -> Function.log(null));

        assertThrows(IllegalArgumentException.class, () -> Function.power(null, Expression.intValue(2)));

        assertThrows(IllegalArgumentException.class, () -> Function.power(Expression.intValue(2), null));

        assertThrows(IllegalArgumentException.class, () -> Function.radians(null));

        assertThrows(IllegalArgumentException.class, () -> Function.round(null));

        assertThrows(IllegalArgumentException.class, () -> Function.round(null, Expression.intValue(2)));

        assertThrows(IllegalArgumentException.class, () -> Function.round(Expression.doubleValue(0.567), null));

        assertThrows(IllegalArgumentException.class, () -> Function.sign(null));

        assertThrows(IllegalArgumentException.class, () -> Function.sin(null));

        assertThrows(IllegalArgumentException.class, () -> Function.sqrt(null));

        assertThrows(IllegalArgumentException.class, () -> Function.tan(null));

        assertThrows(IllegalArgumentException.class, () -> Function.trunc(null));

        assertThrows(IllegalArgumentException.class, () -> Function.trunc(null, Expression.intValue(1)));

        assertThrows(IllegalArgumentException.class, () -> Function.trunc(Expression.doubleValue(79.15), null));
    }

    @Test
    public void testStringFunctionEmptyArgs() {
        assertThrows(IllegalArgumentException.class, () -> Function.contains(null, Expression.string("someSubString")));

        assertThrows(IllegalArgumentException.class, () -> Function.contains(Expression.string("somestring"), null));

        assertThrows(IllegalArgumentException.class, () -> Function.length(null));

        assertThrows(IllegalArgumentException.class, () -> Function.lower(null));

        assertThrows(IllegalArgumentException.class, () -> Function.ltrim(null));

        assertThrows(IllegalArgumentException.class, () -> Function.rtrim(null));

        assertThrows(IllegalArgumentException.class, () -> Function.trim(null));

        assertThrows(IllegalArgumentException.class, () -> Function.upper(null));
    }

    @Test
    public void testStringToMillis() throws Exception {
        createDateDocs();

        SelectResult[] selections = new SelectResult[6];
        selections[0] = (SelectResult.expression(Function.stringToMillis(Expression.property("local"))));
        selections[1] = (SelectResult.expression(Function.stringToMillis(Expression.property("JST"))));
        selections[2] = (SelectResult.expression(Function.stringToMillis(Expression.property("JST2"))));
        selections[3] = (SelectResult.expression(Function.stringToMillis(Expression.property("PST"))));
        selections[4] = (SelectResult.expression(Function.stringToMillis(Expression.property("PST2"))));
        selections[5] = (SelectResult.expression(Function.stringToMillis(Expression.property("UTC"))));

        ArrayList<Number> expectedJST = new ArrayList<>();
        expectedJST.add(null);
        expectedJST.add(499105260000L);
        expectedJST.add(499105290000L);
        expectedJST.add(499105290500L);
        expectedJST.add(499105290550L);
        expectedJST.add(499105290555L);

        ArrayList<Number> expectedPST = new ArrayList<>();
        expectedPST.add(null);
        expectedPST.add(499166460000L);
        expectedPST.add(499166490000L);
        expectedPST.add(499166490500L);
        expectedPST.add(499166490550L);
        expectedPST.add(499166490555L);

        ArrayList<Number> expectedUTC = new ArrayList<>();
        expectedUTC.add(null);
        expectedUTC.add(499137660000L);
        expectedUTC.add(499137690000L);
        expectedUTC.add(499137690500L);
        expectedUTC.add(499137690550L);
        expectedUTC.add(499137690555L);

        Calendar calendar = new GregorianCalendar();
        TimeZone timeZone = calendar.getTimeZone();
        long offset = timeZone.getOffset(499132800000L);
        ArrayList<Number> expectedLocal = new ArrayList<>();
        expectedLocal.add(499132800000L - offset);

        boolean first = true;
        for (Number entry : expectedUTC) {
            if (first) {
                first = false;
                continue;
            }
            expectedLocal.add((long) entry - offset);
        }

        Query query = QueryBuilder.select(selections)
            .from(DataSource.database(db))
            .orderBy(Ordering.property("local").ascending());

        verifyQuery(query, (n, result) -> {
            assertEquals(expectedLocal.get(n - 1), result.getNumber(0));
            assertEquals(expectedJST.get(n - 1), result.getNumber(1));
            assertEquals(expectedJST.get(n - 1), result.getNumber(2));
            assertEquals(expectedPST.get(n - 1), result.getNumber(3));
            assertEquals(expectedPST.get(n - 1), result.getNumber(4));
            assertEquals(expectedUTC.get(n - 1), result.getNumber(5));
        });
    }

    @Test
    public void testStringToUTC() throws Exception {
        createDateDocs();

        SelectResult[] selections = new SelectResult[6];
        selections[0] = (SelectResult.expression(Function.stringToUTC(Expression.property("local"))));
        selections[1] = (SelectResult.expression(Function.stringToUTC(Expression.property("JST"))));
        selections[2] = (SelectResult.expression(Function.stringToUTC(Expression.property("JST2"))));
        selections[3] = (SelectResult.expression(Function.stringToUTC(Expression.property("PST"))));
        selections[4] = (SelectResult.expression(Function.stringToUTC(Expression.property("PST2"))));
        selections[5] = (SelectResult.expression(Function.stringToUTC(Expression.property("UTC"))));

        ArrayList<String> expectedJST = new ArrayList<>();
        expectedJST.add(null);
        expectedJST.add("1985-10-25T16:21:00Z");
        expectedJST.add("1985-10-25T16:21:30Z");
        expectedJST.add("1985-10-25T16:21:30.500Z");
        expectedJST.add("1985-10-25T16:21:30.550Z");
        expectedJST.add("1985-10-25T16:21:30.555Z");

        ArrayList<String> expectedPST = new ArrayList<>();
        expectedPST.add(null);
        expectedPST.add("1985-10-26T09:21:00Z");
        expectedPST.add("1985-10-26T09:21:30Z");
        expectedPST.add("1985-10-26T09:21:30.500Z");
        expectedPST.add("1985-10-26T09:21:30.550Z");
        expectedPST.add("1985-10-26T09:21:30.555Z");

        ArrayList<String> expectedUTC = new ArrayList<>();
        expectedUTC.add(null);
        expectedUTC.add("1985-10-26T01:21:00Z");
        expectedUTC.add("1985-10-26T01:21:30Z");
        expectedUTC.add("1985-10-26T01:21:30.500Z");
        expectedUTC.add("1985-10-26T01:21:30.550Z");
        expectedUTC.add("1985-10-26T01:21:30.555Z");

        ArrayList<String> expectedLocal = new ArrayList<>();
        expectedLocal.add(localToUTC("yyyy-MM-dd", "1985-10-26"));
        expectedLocal.add(localToUTC("yyyy-MM-dd HH:mm", "1985-10-26 01:21"));
        expectedLocal.add(localToUTC("yyyy-MM-dd HH:mm:ss", "1985-10-26 01:21:30"));
        expectedLocal.add(localToUTC("yyyy-MM-dd HH:mm:ss.SSS", "1985-10-26 01:21:30.500"));
        expectedLocal.add(localToUTC("yyyy-MM-dd HH:mm:ss.SSS", "1985-10-26 01:21:30.550"));
        expectedLocal.add(localToUTC("yyyy-MM-dd HH:mm:ss.SSS", "1985-10-26 01:21:30.555"));

        Query query = QueryBuilder.select(selections)
            .from(DataSource.database(db))
            .orderBy(Ordering.property("local").ascending());

        verifyQuery(query, (n, result) -> {
            assertEquals(expectedLocal.get(n - 1), result.getString(0));
            assertEquals(expectedJST.get(n - 1), result.getString(1));
            assertEquals(expectedJST.get(n - 1), result.getString(2));
            assertEquals(expectedPST.get(n - 1), result.getString(3));
            assertEquals(expectedPST.get(n - 1), result.getString(4));
            assertEquals(expectedUTC.get(n - 1), result.getString(5));
        });
    }

    @Test
    public void testMillisConversion() throws Exception {
        ArrayList<Number> millisToUse = new ArrayList<>();
        millisToUse.add(499132800000L);
        millisToUse.add(499137660000L);
        millisToUse.add(499137690000L);
        millisToUse.add(499137690500L);
        millisToUse.add(499137690550L);
        millisToUse.add(499137690555L);

        ArrayList<String> expectedLocal = new ArrayList<>();
        for (Number millis : millisToUse) {
            MutableDocument doc = new MutableDocument();
            doc.setNumber("timestamp", millis);
            db.save(doc);
            expectedLocal.add(toLocal((long) millis));
        }

        ArrayList<String> expectedUTC = new ArrayList<>();
        expectedUTC.add("1985-10-26T00:00:00Z");
        expectedUTC.add("1985-10-26T01:21:00Z");
        expectedUTC.add("1985-10-26T01:21:30Z");
        expectedUTC.add("1985-10-26T01:21:30.500Z");
        expectedUTC.add("1985-10-26T01:21:30.550Z");
        expectedUTC.add("1985-10-26T01:21:30.555Z");

        SelectResult selections[] = new SelectResult[2];
        selections[0] = SelectResult.expression(Function.millisToString(Expression.property("timestamp")));
        selections[1] = SelectResult.expression(Function.millisToUTC(Expression.property("timestamp")));

        Query query = QueryBuilder.select(selections)
            .from(DataSource.database(db))
            .orderBy(Ordering.property("timestamp").ascending());

        verifyQuery(query, (n, result) -> {
            assertEquals(expectedLocal.get(n - 1), result.getString(0));
            assertEquals(expectedUTC.get(n - 1), result.getString(1));
        });
    }

    private void runTestWithNumbers(List<Map<String, Object>> numbers, Object[][] cases)
        throws Exception {
        for (Object[] c : cases) {
            Expression w = (Expression) c[0];
            String[] documentIDs = (String[]) c[1];
            final List<String> docIDList = new ArrayList<String>(Arrays.asList(documentIDs));
            Query q = QueryBuilder.select(SR_DOCID).from(DataSource.database(db)).where(w);
            int numRows = verifyQuery(q, (n, result) -> {
                String docID = result.getString(0);
                if (docIDList.contains(docID)) { docIDList.remove(docID); }
            });
            assertEquals(0, docIDList.size());
            assertEquals(documentIDs.length, numRows);
        }
    }

    private String[] docids(int... numbers) {
        String[] documentIDs = new String[numbers.length];
        for (int i = 0; i < numbers.length; i++) { documentIDs[i] = "doc" + numbers[i]; }
        return documentIDs;
    }

    private void createDateDocs() throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument();
        doc.setString("local", "1985-10-26");
        db.save(doc);

        ArrayList<String> dateTimeFormats = new ArrayList<>();
        dateTimeFormats.add("1985-10-26 01:21");
        dateTimeFormats.add("1985-10-26 01:21:30");
        dateTimeFormats.add("1985-10-26 01:21:30.5");
        dateTimeFormats.add("1985-10-26 01:21:30.55");
        dateTimeFormats.add("1985-10-26 01:21:30.555");

        for (String format : dateTimeFormats) {
            doc = new MutableDocument();
            doc.setString("local", format);
            doc.setString("JST", format + "+09:00");
            doc.setString("JST2", format + "+0900");
            doc.setString("PST", format + "-08:00");
            doc.setString("PST2", format + "-0800");
            doc.setString("UTC", format + "Z");
            db.save(doc);
        }
    }

    private String localToUTC(String format, String dateStr) throws ParseException {
        TimeZone tz = TimeZone.getDefault();
        SimpleDateFormat df = new SimpleDateFormat(format);
        df.setTimeZone(tz);
        Date date = df.parse(dateStr);
        df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        return df.format(date).replace(".000", "");
    }

    private String toLocal(long timestamp) throws ParseException {
        TimeZone tz = TimeZone.getDefault();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        df.setTimeZone(tz);
        return df.format(new Date(timestamp)).replace(".000", "");
    }
}
