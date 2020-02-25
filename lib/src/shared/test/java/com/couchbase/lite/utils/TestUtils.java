//
// Copyright (c) 2019 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.utils;

import java.util.Locale;
import java.util.Random;

import com.couchbase.lite.CouchbaseLiteException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;


public final class TestUtils {

    private TestUtils() {}

    public static final String ALPHA = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    public static final String ALPHANUMERIC = "0123456789" + ALPHA + ALPHA.toLowerCase(Locale.ROOT);

    private static final char[] CHARS = ALPHANUMERIC.toCharArray();
    private static final Random RANDOM = new Random(System.currentTimeMillis());

    public static String randomString(int len) {
        final char[] buf = new char[len];
        for (int idx = 0; idx < buf.length; ++idx) { buf[idx] = CHARS[RANDOM.nextInt(CHARS.length)]; }
        return new String(buf);
    }

    public static <T extends Exception> void assertThrows(Class<T> ex, Fn.TaskThrows<Exception> test) {
        try {
            test.run();
            fail("Expecting exception: " + ex);
        }
        catch (Throwable e) {
            try { ex.cast(e); }
            catch (ClassCastException e1) { fail("Expecting exception: " + ex + " but got " + e); }
        }
    }
    public static void assertThrowsCBL(String domain, int code, Fn.TaskThrows<CouchbaseLiteException> task) {
        try {
            task.run();
            fail();
        }
        catch (CouchbaseLiteException e) {
            assertEquals(code, e.getCode());
            assertEquals(domain, e.getDomain());
        }
    }
}

