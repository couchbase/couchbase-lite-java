package com.couchbase.lite.util;

public class JsonCollator {
    public static int compareStringsUnicode(String a, String b) {
        java.text.Collator c = java.text.Collator.getInstance();
        int res = c.compare(a, b);
        return res;
    }
}
