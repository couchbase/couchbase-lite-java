//
// RefCounted.java
//
// Copyright (c) 2018 Couchbase, Inc All rights reserved.
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

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.support.Log;


abstract class RefCounted {
    private int refCount;

    abstract void free();

    public synchronized void retain() { refCount++; }

    public synchronized void release() {
        if (--refCount <= 0) { free(); }
        if (refCount < 0) {
            Log.w(LogDomain.DATABASE, "Ref count for " + getClass().getCanonicalName() + " is " + refCount);
        }
    }
}
