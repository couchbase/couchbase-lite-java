package com.couchbase.lite;

import org.junit.Test;

import static com.couchbase.lite.utils.TestUtils.assertThrows;
import static org.junit.Assert.assertEquals;


public class AuthenticatorTest extends BaseTest {

    @Test
    public void testBasicAuthenticatorInstance() throws CouchbaseLiteException {
        String username = "someUsername";
        String password = "somePassword";
        BasicAuthenticator auth = new BasicAuthenticator(username, password);
        assertEquals(auth.getUsername(), username);
        assertEquals(auth.getPassword(), password);
    }

    @Test
    public void testBasicAuthenticatorWithEmptyArgs() {
        String username = "someUsername";
        String password = "somePassword";

        assertThrows(IllegalArgumentException.class, () -> new BasicAuthenticator(null, password));

        assertThrows(IllegalArgumentException.class, () -> new BasicAuthenticator(username, null));
    }

    @Test
    public void testSessionAuthenticatorWithSessionID() throws CouchbaseLiteException {
        String sessionID = "someSessionID";
        SessionAuthenticator auth = new SessionAuthenticator(sessionID);
        assertEquals(auth.getSessionID(), sessionID);
        assertEquals(auth.getCookieName(), "SyncGatewaySession");
    }

    @Test
    public void testSessionAuthenticatorWithSessionIDAndCookie() throws CouchbaseLiteException {
        String sessionID = "someSessionID";
        String cookie = "someCookie";
        SessionAuthenticator auth = new SessionAuthenticator(sessionID, cookie);
        assertEquals(auth.getSessionID(), sessionID);
        assertEquals(auth.getCookieName(), cookie);
    }

    @Test
    public void testSessionAuthenticatorEmptySessionID() throws CouchbaseLiteException {
        assertThrows(IllegalArgumentException.class, () -> new SessionAuthenticator(null, null));
    }

    @Test
    public void testSessionAuthenticatorEmptyCookie() throws CouchbaseLiteException {
        String sessionID = "someSessionID";
        SessionAuthenticator auth = new SessionAuthenticator(sessionID, null);
        assertEquals(auth.getSessionID(), sessionID);
        assertEquals(auth.getCookieName(), "SyncGatewaySession");
    }
}
