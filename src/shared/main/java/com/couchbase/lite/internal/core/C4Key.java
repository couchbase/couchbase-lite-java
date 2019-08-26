//
// C4Key.java
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
package com.couchbase.lite.internal.core;


import android.support.annotation.NonNull;

import java.nio.charset.StandardCharsets;

import com.couchbase.lite.CBLError;
import com.couchbase.lite.CouchbaseLiteException;


public class C4Key {
    private static final String DEFAULT_PBKDF2_KEY_SALT = "Salty McNaCl";
    private static final int DEFAULT_PBKDF2_KEY_ROUNDS = 64000; // Same as what SQLCipher uses


    @NonNull
    public static byte[] getPbkdf2Key(@NonNull String password) throws CouchbaseLiteException {
        final byte[] key = C4Key.pbkdf2(
            password,
            DEFAULT_PBKDF2_KEY_SALT.getBytes(StandardCharsets.UTF_8),
            DEFAULT_PBKDF2_KEY_ROUNDS,
            C4Constants.EncryptionKeySize.AES256);
        if (key != null) { return key; }

        throw new CouchbaseLiteException("Could not generate key", CBLError.Domain.CBLITE, CBLError.Code.CRYPTO);
    }

    @NonNull
    public static byte[] getCoreKey(@NonNull String password) throws CouchbaseLiteException {
        final byte[] key = C4Key.deriveKeyFromPassword(password, C4Constants.EncryptionAlgorithm.AES256);
        if (key != null) { return key; }

        throw new CouchbaseLiteException("Could not generate key", CBLError.Domain.CBLITE, CBLError.Code.CRYPTO);
    }

    static native byte[] pbkdf2(String password, byte[] salt, int rounds, int keysize);

    static native byte[] deriveKeyFromPassword(String password, int alg);
}
