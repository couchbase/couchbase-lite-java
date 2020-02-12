//
// MValue.java
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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;

import java.util.concurrent.atomic.AtomicBoolean;

import com.couchbase.lite.internal.utils.Preconditions;


public class MValue implements Encodable {

    //-------------------------------------------------------------------------
    // Constants
    //-------------------------------------------------------------------------

    static final MValue EMPTY = new MValue(null, null) {
        public boolean isEmpty() { return true; }
    };

    //-------------------------------------------------------------------------
    // Types
    //-------------------------------------------------------------------------

    public interface Delegate {
        @Nullable
        Object toNative(@NonNull MValue mv, @Nullable MCollection parent, @NonNull AtomicBoolean cacheIt);

        @Nullable
        MCollection collectionFromNative(@Nullable Object object);

        void encodeNative(@NonNull FLEncoder encoder, @Nullable Object object);
    }

    //-------------------------------------------------------------------------
    // Static members
    //-------------------------------------------------------------------------

    private static Delegate delegate;

    //-------------------------------------------------------------------------
    // Public static methods
    //-------------------------------------------------------------------------

    public static void registerDelegate(@NonNull Delegate delegate) {
        Preconditions.assertNotNull(delegate, "delegate");
        MValue.delegate = delegate;
    }

    @VisibleForTesting
    public static Delegate getRegisteredDelegate() { return delegate; }

    //-------------------------------------------------------------------------
    // Instance members
    //-------------------------------------------------------------------------

    @Nullable
    private FLValue value;
    @Nullable
    private Object nativeObject;

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    public MValue(@Nullable Object obj) { this(obj, null); }

    public MValue(@Nullable FLValue val) { this(null, val); }

    private MValue(@Nullable Object obj, @Nullable FLValue val) {
        nativeObject = obj;
        this.value = val;
    }

    //-------------------------------------------------------------------------
    // Public methods
    //-------------------------------------------------------------------------

    public boolean isEmpty() { return false; }

    public boolean isMutated() { return value == null; }

    @Nullable
    public FLValue getValue() { return value; }

    public void mutate() {
        Preconditions.assertNotNull(nativeObject, "Native object");
        value = null;
    }

    @Nullable
    public Object asNative(@Nullable MCollection parent) {
        if ((nativeObject != null) || (value == null)) { return nativeObject; }

        final AtomicBoolean cacheIt = new AtomicBoolean(false);
        final Object obj = toNative(this, parent, cacheIt);
        if (cacheIt.get()) { nativeObject = obj; }
        return obj;
    }

    @Override
    public void encodeTo(@NonNull FLEncoder enc) {
        if (isEmpty()) { throw new IllegalStateException("MValue is empty."); }

        if (value != null) { enc.writeValue(value); }
        else { encodeNative(enc, nativeObject); }
    }

    //-------------------------------------------------------------------------
    // Private methods
    //-------------------------------------------------------------------------
    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        nativeChangeSlot(null); // ??? WAT?
        super.finalize();
    }

    //-------------------------------------------------------------------------
    // Private methods
    //-------------------------------------------------------------------------

    @Nullable
    private Object toNative(@NonNull MValue mv, @Nullable MCollection parent, @NonNull AtomicBoolean cacheIt) {
        Preconditions.assertNotNull(MValue.delegate, "delegate");
        return delegate.toNative(mv, parent, cacheIt);
    }

    @Nullable
    private MCollection collectionFromNative(@Nullable Object obj) {
        Preconditions.assertNotNull(MValue.delegate, "delegate");
        return delegate.collectionFromNative(obj);
    }

    private void nativeChangeSlot(@Nullable MValue newSlot) {
        final MCollection collection = collectionFromNative(newSlot);
        if (collection != null) { collection.setSlot(newSlot, this); }
    }

    private void encodeNative(@NonNull FLEncoder encoder, @Nullable Object object) {
        Preconditions.assertNotNull(MValue.delegate, "delegate");
        delegate.encodeNative(encoder, object);
    }
}
