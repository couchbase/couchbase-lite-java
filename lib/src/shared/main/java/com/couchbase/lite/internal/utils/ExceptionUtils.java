package com.couchbase.lite.internal.utils;

import java.io.PrintWriter;
import java.io.StringWriter;


public class ExceptionUtils {
    public static String getStackTrace(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        pw.flush();
        sw.flush();
        return sw.toString();
    }
}
