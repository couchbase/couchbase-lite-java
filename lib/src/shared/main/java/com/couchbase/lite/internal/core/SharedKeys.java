//
// SharedKeys.java
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

import com.couchbase.lite.internal.fleece.FLSharedKeys;


public final class SharedKeys {

    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private final FLSharedKeys flSharedKeys;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------
    public SharedKeys(final C4Database c4db) {
        flSharedKeys = c4db.getFLSharedKeys();
    }

    public SharedKeys(final FLSharedKeys flSharedKeys) {
        this.flSharedKeys = flSharedKeys;
    }

    //---------------------------------------------
    // Public level methods
    //---------------------------------------------
    public FLSharedKeys getFLSharedKeys() {
        return flSharedKeys;
    }
}
