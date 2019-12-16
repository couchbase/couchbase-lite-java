//
// URLEndpoint.java
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

import android.support.annotation.NonNull;

import java.net.URI;

import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * URL based replication target endpoint
 */
public final class URLEndpoint implements Endpoint {
    //---------------------------------------------
    // Constants
    //---------------------------------------------
    static final String SCHEME_STD = "ws";
    static final String SCHEME_TLS = "wss";

    //---------------------------------------------
    // Member variables
    //---------------------------------------------
    @NonNull
    private final URI url;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    /**
     * Constructor with the url. The supported URL schemes
     * are ws and wss for transferring data over a secure channel.
     *
     * @param url The url.
     */
    public URLEndpoint(@NonNull URI url) {
        Preconditions.assertNotNull(url, "url");

        final String scheme = url.getScheme();
        if (!(SCHEME_STD.equals(scheme) || SCHEME_TLS.equals(scheme))) {
            throw new IllegalArgumentException(Log.formatStandardMessage("InvalidSchemeURLEndpoint", scheme));
        }

        final String userInfo = url.getUserInfo();
        if (userInfo != null && userInfo.split(":").length == 2) {
            throw new IllegalArgumentException(Log.lookupStandardMessage("InvalidEmbeddedCredentialsInURL"));
        }

        this.url = url;
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    @NonNull
    @Override
    public String toString() { return "URLEndpoint{url=" + url + '}'; }

    /**
     * Returns the url.
     */
    @NonNull
    public URI getURL() { return url; }
}
