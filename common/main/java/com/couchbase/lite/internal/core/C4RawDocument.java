//
// C4RawDocument.java
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

import com.couchbase.lite.LiteCoreException;


public class C4RawDocument extends C4NativePeer {

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    C4RawDocument(long handle) { super(handle); }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    public String key() { return key(getPeer()); }

    public String meta() { return meta(getPeer()); }

    public byte[] body() { return body(getPeer()); }

    public void free() throws LiteCoreException {
        final long handle = getPeerAndClear();
        if (handle == 0L) { return; }
        C4Database.rawFreeDocument(handle);
    }

    //-------------------------------------------------------------------------
    // protected methods
    //-------------------------------------------------------------------------

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        free();
        super.finalize();
    }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    static native String key(long rawDoc);

    static native String meta(long rawDoc);

    static native byte[] body(long rawDoc);
}
