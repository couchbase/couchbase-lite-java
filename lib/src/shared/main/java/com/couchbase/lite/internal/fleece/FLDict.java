//
// FLDict.java
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
package com.couchbase.lite.internal.fleece;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;


public class FLDict {
    private final long handle; // hold pointer to FLDict

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    public FLDict(long handle) {
        if (handle == 0L) { throw new IllegalStateException("handle is 0L"); }
        this.handle = handle;
    }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    public FLValue toFLValue() { return new FLValue(handle); }

    public long count() {
        if (handle == 0L) { throw new IllegalStateException("handle is 0L"); }
        return count(handle);
    }

    public FLValue get(String key) {
        if (key == null) { return null; }

        final long hValue = get(handle, key.getBytes(StandardCharsets.UTF_8));

        return hValue != 0L ? new FLValue(hValue) : null;
    }

    public Map<String, Object> asDict() {
        final Map<String, Object> results = new HashMap<>();
        final FLDictIterator itr = new FLDictIterator();
        try {
            itr.begin(this);
            String key;
            while ((key = itr.getKeyString()) != null) {
                results.put(key, itr.getValue().asObject());
                itr.next();
            }
        }
        finally {
            itr.free();
        }
        return results;
    }

    //-------------------------------------------------------------------------
    // protected methods
    //-------------------------------------------------------------------------

    long getHandle() { return handle; }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    /**
     * Returns the number of items in a dictionary, or 0 if the pointer is nullptr.
     *
     * @param dict FLDict
     * @return uint32_t
     */
    static native long count(long dict);

    /**
     * Looks up a key in a _sorted_ dictionary, using a shared-keys mapping.
     *
     * @param dict      FLDict
     * @param keyString FLSlice
     * @return FLValue
     */
    static native long get(long dict, byte[] keyString);
}
