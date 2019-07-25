//
// ReplicatorWithSyncGatewayTest.java
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

import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.couchbase.lite.utils.Config;
import com.couchbase.lite.utils.ReplicatorIntegrationTest;


/**
 * Note: https://github.com/couchbase/couchbase-lite-android/tree/master/test/replicator
 */
public class ReplicatorWithSyncGatewayTest extends BaseReplicatorTest {
    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    @Before
    public void setUp() throws Exception {
        config = new Config(getAsset(Config.TEST_PROPERTIES_FILE));
        super.setUp();
    }

    @After
    public void tearDown() { super.tearDown(); }

    @Test
    @ReplicatorIntegrationTest
    public void testEmptyPullFromRemoteDB() throws Exception {
        Endpoint target = getRemoteEndpoint("scratch", false);
        ReplicatorConfiguration config = makeConfig(false, true, false, target);
        run(config, 0, null);
    }

    @Test
    @ReplicatorIntegrationTest
    public void testAuthenticationFailure() throws Exception {
        Endpoint target = getRemoteEndpoint("seekrit", false);
        ReplicatorConfiguration config = makeConfig(false, true, false, target);
        run(config, CBLError.Code.HTTP_AUTH_REQUIRED, CBLError.Domain.CBLITE);
    }

    @Test
    @ReplicatorIntegrationTest
    public void testAuthenticatedPullWithIncorrectPassword() throws Exception {
        Endpoint target = getRemoteEndpoint("seekrit", false);
        ReplicatorConfiguration config = makeConfig(false, true, false, target);
        config.setAuthenticator(new BasicAuthenticator("pupshaw", "frank!"));
        run(config, CBLError.Code.HTTP_AUTH_REQUIRED, CBLError.Domain.CBLITE); // Retry 3 times then fails with 401
    }

    @Test
    @ReplicatorIntegrationTest
    public void testAuthenticatedPull() throws Exception {
        Endpoint target = getRemoteEndpoint("seekrit", false);
        ReplicatorConfiguration config = makeConfig(false, true, false, target);
        config.setAuthenticator(new BasicAuthenticator("pupshaw", "frank"));
        run(config, 0, null);
    }

    @Test
    @ReplicatorIntegrationTest
    public void testSessionAuthenticatorPull() throws Exception {
        // Obtain Sync-Gateway Session ID
        SessionAuthenticator auth = getSessionAuthenticatorFromSG();
        Endpoint target = getRemoteEndpoint("seekrit", false);
        ReplicatorConfiguration config = makeConfig(false, true, false, target);
        config.setAuthenticator(auth);
        run(config, 0, null);
    }

    private SessionAuthenticator getSessionAuthenticatorFromSG() throws Exception {
        // Obtain Sync-Gateway Session ID
        final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        OkHttpClient client = new OkHttpClient();
        String url = String.format(Locale.ENGLISH, "http://%s:4985/seekrit/_session", config.remoteHost());
        RequestBody body = RequestBody.create(JSON, "{\"name\": \"pupshaw\"}");
        Request request = new Request.Builder().url(url).post(body).build();
        Response response = client.newCall(request).execute();
        String respBody = response.body().string();
        JSONObject json = new JSONObject(respBody);
        return new SessionAuthenticator(
            json.getString("session_id"),
            json.getString("cookie_name"));
    }
}
