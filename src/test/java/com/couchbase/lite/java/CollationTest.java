package com.couchbase.lite.java;

import com.couchbase.lite.storage.JavaSQLiteStorageEngine;
import com.couchbase.lite.util.Log;
import junit.framework.TestCase;
import org.junit.Assert;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * com.couchbase.lite.java.CollationTest is ported from
 * https://github.com/couchbase/couchbase-lite-android/blob/master/src/instrumentTest/java/com/couchbase/lite/com.couchbase.lite.java.CollationTest.java
 */
public class CollationTest extends TestCase {
    public static String TAG = "Collation";

    private static final int kTDCollateJSON_Unicode = 0;
    private static final int kTDCollateJSON_Raw = 1;
    private static final int kTDCollateJSON_ASCII = 2;

    /*
    // create the same JSON encoding used by TouchDB
    // this lets us test comparisons as they would be encoded
    public String encode(NodeCursor.Object obj) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            byte[] bytes = mapper.writeValueAsBytes(obj);
            String result = new String(bytes);
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Error encoding JSON", e);
            return null;
        }
    }
    */
    public String encode(Object obj) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            byte[] bytes = mapper.writeValueAsBytes(obj);
            String result = new String(bytes);
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Error encoding JSON", e);
            return null;
        }
    }

    public void testCollateScalars() {
        int mode = kTDCollateJSON_Unicode;
        Assert.assertEquals(1, JavaSQLiteStorageEngine.testCollateJSON(mode, "true", "false"));
        Assert.assertEquals(-1, JavaSQLiteStorageEngine.testCollateJSON(mode, "false", "true"));
        Assert.assertEquals(-1, JavaSQLiteStorageEngine.testCollateJSON(mode, "null", "17"));
        Assert.assertEquals(1, JavaSQLiteStorageEngine.testCollateJSON(mode, "123", "1"));
        Assert.assertEquals(0, JavaSQLiteStorageEngine.testCollateJSON(mode, "123", "0123.0"));
        Assert.assertEquals(-1, JavaSQLiteStorageEngine.testCollateJSON(mode, "123", "\"123\""));
        Assert.assertEquals(1, JavaSQLiteStorageEngine.testCollateJSON(mode, "\"1234\"", "\"123\""));
        Assert.assertEquals(-1, JavaSQLiteStorageEngine.testCollateJSON(mode, "\"1234\"", "\"1235\""));
        Assert.assertEquals(0, JavaSQLiteStorageEngine.testCollateJSON(mode, "\"1234\"", "\"1234\""));
        Assert.assertEquals(0, JavaSQLiteStorageEngine.testCollateJSON(mode, "\"12\\/34\"", "\"12/34\""));
        Assert.assertEquals(0, JavaSQLiteStorageEngine.testCollateJSON(mode, "\"\\/1234\"", "\"/1234\""));
        Assert.assertEquals(0, JavaSQLiteStorageEngine.testCollateJSON(mode, "\"1234\\/\"", "\"1234/\""));
        Assert.assertEquals(-1, JavaSQLiteStorageEngine.testCollateJSON(mode, "\"a\"", "\"A\""));
        Assert.assertEquals(-1, JavaSQLiteStorageEngine.testCollateJSON(mode, "\"A\"", "\"aa\""));
        Assert.assertEquals(1, JavaSQLiteStorageEngine.testCollateJSON(mode, "\"B\"", "\"aa\""));
    }

    public void testCollateASCII() {
        int mode = kTDCollateJSON_ASCII;
        Assert.assertEquals(1, JavaSQLiteStorageEngine.testCollateJSON(mode, "true", "false"));
        Assert.assertEquals(-1, JavaSQLiteStorageEngine.testCollateJSON(mode, "false", "true"));
        Assert.assertEquals(-1, JavaSQLiteStorageEngine.testCollateJSON(mode, "null", "17"));
        Assert.assertEquals(1, JavaSQLiteStorageEngine.testCollateJSON(mode, "123", "1"));
        Assert.assertEquals(0, JavaSQLiteStorageEngine.testCollateJSON(mode, "123", "0123.0"));
        Assert.assertEquals(-1, JavaSQLiteStorageEngine.testCollateJSON(mode, "123", "\"123\""));
        Assert.assertEquals(1, JavaSQLiteStorageEngine.testCollateJSON(mode, "\"1234\"", "\"123\""));
        Assert.assertEquals(-1, JavaSQLiteStorageEngine.testCollateJSON(mode, "\"1234\"", "\"1235\""));
        Assert.assertEquals(0, JavaSQLiteStorageEngine.testCollateJSON(mode, "\"1234\"", "\"1234\""));
        Assert.assertEquals(0, JavaSQLiteStorageEngine.testCollateJSON(mode, "\"12\\/34\"", "\"12/34\""));
        Assert.assertEquals(0, JavaSQLiteStorageEngine.testCollateJSON(mode, "\"\\/1234\"", "\"/1234\""));
        Assert.assertEquals(0, JavaSQLiteStorageEngine.testCollateJSON(mode, "\"1234\\/\"", "\"1234/\""));
        Assert.assertEquals(-1, JavaSQLiteStorageEngine.testCollateJSON(mode, "\"A\"", "\"a\""));
        Assert.assertEquals(-1, JavaSQLiteStorageEngine.testCollateJSON(mode, "\"B\"", "\"a\""));
    }

    public void testCollateRaw() {
        int mode = kTDCollateJSON_Raw;
        Assert.assertEquals(1, JavaSQLiteStorageEngine.testCollateJSON(mode, "false", "17"));
        Assert.assertEquals(-1, JavaSQLiteStorageEngine.testCollateJSON(mode, "false", "true"));
        Assert.assertEquals(-1, JavaSQLiteStorageEngine.testCollateJSON(mode, "null", "true"));
        Assert.assertEquals(-1, JavaSQLiteStorageEngine.testCollateJSON(mode, "[\"A\"]", "\"A\""));
        Assert.assertEquals(-1, JavaSQLiteStorageEngine.testCollateJSON(mode, "\"A\"", "\"a\""));
        Assert.assertEquals(-1, JavaSQLiteStorageEngine.testCollateJSON(mode, "[\"b\"]", "[\"b\",\"c\",\"a\"]"));
    }

    public void testCollateArrays() {
        int mode = kTDCollateJSON_Unicode;
        Assert.assertEquals(1, JavaSQLiteStorageEngine.testCollateJSON(mode, "[]", "\"foo\""));
        Assert.assertEquals(0, JavaSQLiteStorageEngine.testCollateJSON(mode, "[]", "[]"));
        Assert.assertEquals(0, JavaSQLiteStorageEngine.testCollateJSON(mode, "[true]", "[true]"));
        Assert.assertEquals(1, JavaSQLiteStorageEngine.testCollateJSON(mode, "[false]", "[null]"));
        Assert.assertEquals(-1, JavaSQLiteStorageEngine.testCollateJSON(mode, "[]", "[null]"));
        Assert.assertEquals(1, JavaSQLiteStorageEngine.testCollateJSON(mode, "[123]", "[45]"));
        Assert.assertEquals(1, JavaSQLiteStorageEngine.testCollateJSON(mode, "[123]", "[45,67]"));
        Assert.assertEquals(1, JavaSQLiteStorageEngine.testCollateJSON(mode, "[123.4,\"wow\"]", "[123.40,789]"));
    }

    public void testCollateNestedArray() {
        int mode = kTDCollateJSON_Unicode;
        Assert.assertEquals(1, JavaSQLiteStorageEngine.testCollateJSON(mode, "[[]]", "[]"));
        Assert.assertEquals(-1, JavaSQLiteStorageEngine.testCollateJSON(mode, "[1,[2,3],4]", "[1,[2,3.1],4,5,6]"));
    }

    public void testCollateUnicodeStrings() {
        int mode = kTDCollateJSON_Unicode;
        Assert.assertEquals(0, JavaSQLiteStorageEngine.testCollateJSON(mode, encode("fr�d"), encode("fr�d")));
        // Note:
        // Oracle Java and Android Java have different implementation of java.text.Collator. Android uses ICU libraries while
        // Oracle Java is using its own implementation. As a result comparing between tab and space character yields
        // different result when using Oracle Java and Android Java.
        //
        //Assert.assertEquals(-1, JavaSQLiteStorageEngine.testCollateJSON(mode, encode("\t"), encode(" ")));
        //
        Assert.assertEquals(-1, JavaSQLiteStorageEngine.testCollateJSON(mode, encode("\001"), encode(" ")));
    }

    public void testCollateRevIds() {
        Assert.assertEquals(JavaSQLiteStorageEngine.testCollateRevIds("1-foo", "1-foo"), 0);
        Assert.assertEquals(JavaSQLiteStorageEngine.testCollateRevIds("2-bar", "1-foo"), 1);
        Assert.assertEquals(JavaSQLiteStorageEngine.testCollateRevIds("1-foo", "2-bar"), -1);

        // Multi-digit:
        Assert.assertEquals(JavaSQLiteStorageEngine.testCollateRevIds("123-bar", "456-foo"), -1);
        Assert.assertEquals(JavaSQLiteStorageEngine.testCollateRevIds("456-foo", "123-bar"), 1);
        Assert.assertEquals(JavaSQLiteStorageEngine.testCollateRevIds("456-foo", "456-foo"), 0);
        Assert.assertEquals(JavaSQLiteStorageEngine.testCollateRevIds("456-foo", "456-foofoo"), -1);
        // Different numbers of digits:
        Assert.assertEquals(JavaSQLiteStorageEngine.testCollateRevIds("89-foo", "123-bar"), -1);
        Assert.assertEquals(JavaSQLiteStorageEngine.testCollateRevIds("123-bar", "89-foo"), 1);
        // Edge cases:
        Assert.assertEquals(JavaSQLiteStorageEngine.testCollateRevIds("123-", "89-"), 1);
        Assert.assertEquals(JavaSQLiteStorageEngine.testCollateRevIds("123-a", "123-a"), 0);
        // Invalid rev IDs:
        Assert.assertEquals(JavaSQLiteStorageEngine.testCollateRevIds("-a", "-b"), -1);
        Assert.assertEquals(JavaSQLiteStorageEngine.testCollateRevIds("-", "-"), 0);
        Assert.assertEquals(JavaSQLiteStorageEngine.testCollateRevIds("", ""), 0);
        Assert.assertEquals(JavaSQLiteStorageEngine.testCollateRevIds("", "-b"), -1);
        Assert.assertEquals(JavaSQLiteStorageEngine.testCollateRevIds("bogus", "yo"), -1);
        Assert.assertEquals(JavaSQLiteStorageEngine.testCollateRevIds("bogus-x", "yo-y"), -1);
    }
}
