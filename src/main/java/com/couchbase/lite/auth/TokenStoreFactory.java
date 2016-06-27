package com.couchbase.lite.auth;

import com.couchbase.lite.Context;

/**
 * Created by hideki on 6/27/16.
 */
public class TokenStoreFactory {
    public static TokenStore build(Context context) {
        return new MemTokenStore();
    }
}
