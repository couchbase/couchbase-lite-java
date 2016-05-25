/**
 * Copyright (c) 2016 Couchbase, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package com.couchbase.test.lite;

import com.couchbase.lite.Context;
import com.couchbase.lite.JavaContext;
import junit.framework.*;

import java.io.File;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class is changed depending on if we are in Android or Java. Amongst other things it lets us
 * change the base class for tests between TestCase (Java) and AndroidTestCase (Android).
 *
 * Reference Issue: https://github.com/couchbase/couchbase-lite-android/issues/285
 */
public class LiteTestCaseBase extends TestCase {
    public Context getTestContext(String dirName) {
        return new JavaContext(dirName);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        //We have to tell the log handlers to output everything or any logging events below info won't be logged
        //Taken from http://www.onjava.com/pub/a/onjava/2002/06/19/log.html?page=2
        Handler[] handlers = Logger.getLogger("").getHandlers();
        for(Handler handler : handlers) {
            handler.setLevel(Level.ALL);
        }
    }

    private class JavaTestContext extends JavaContext {
        @Override
        public File getRootDirectory() {
            File rootDirectory = new File("couchbaselitetest");
            if (!rootDirectory.exists()) {
                rootDirectory.mkdir();
            }
            return rootDirectory;
        }
    }

    public void testLiteTestCaseSetupProperly() {
        // Avoid No tests found Assertion Failed Error.
    }
}
