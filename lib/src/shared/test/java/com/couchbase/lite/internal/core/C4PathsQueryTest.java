package com.couchbase.lite.internal.core;

import java.io.IOException;
import java.util.Arrays;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.CBLStatus;

import static org.junit.Assert.assertEquals;


public class C4PathsQueryTest extends C4QueryBaseTest {
    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    @Before
    @Override
    public void setUp() throws CouchbaseLiteException {
        super.setUp();

        importJSONLinesSafely("paths.json");
    }

    // - DB Query ANY w/paths
    @Test
    public void testDBQueryANYwPaths() throws LiteCoreException {
        // For https://github.com/couchbase/couchbase-lite-core/issues/238
        compile(json5("['ANY','path',['.paths'],['=',['?path','city'],'San Jose']]"));
        assertEquals(Arrays.asList("0000001"), run());

        compile(json5("['ANY','path',['.paths'],['=',['?path.city'],'San Jose']]"));
        assertEquals(Arrays.asList("0000001"), run());

        compile(json5("['ANY','path',['.paths'],['=',['?path','city'],'Palo Alto']]"));
        assertEquals(Arrays.asList("0000001", "0000002"), run());
    }
}
