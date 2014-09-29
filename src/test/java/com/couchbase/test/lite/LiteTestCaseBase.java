package com.couchbase.test.lite;

import junit.framework.*;

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

    public void testLiteTestCaseSetupProperly() {
        // Avoid No tests found Assertion Failed Error.
    }
}
