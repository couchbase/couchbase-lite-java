//
// MValueDelegate.java
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


@VisibleForTesting
public class MValueDelegate implements MValue.Delegate {

    //-------------------------------------------------------------------------
    // Public methods
    //-------------------------------------------------------------------------
    @Nullable
    @Override
    public Object toNative(@NonNull MValue mv, @Nullable MCollection parent, @NonNull AtomicBoolean cacheIt) {
        FLValue value = mv.getValue();
        int type = value.getType();
        switch (type) {
            case FLConstants.ValueType.ARRAY:
                cacheIt.set(true);
                return new FleeceArray(mv, parent);
            case FLConstants.ValueType.DICT:
                cacheIt.set(true);
                return new FleeceDict(mv, parent);
            default:
                return value.asObject();
        }
    }

    @Nullable
    @Override
    public MCollection collectionFromNative(@Nullable Object object) {
        if (object instanceof FleeceDict) { return ((FleeceDict) object).toMCollection(); }
        else if (object instanceof FleeceArray) { return ((FleeceArray) object).toMCollection(); }
        else { return null; }
    }

    @Override
    public void encodeNative(@NonNull FLEncoder enc, @Nullable Object object) {
        if (object == null) { enc.writeNull(); }
        else { enc.writeValue(object); }
    }
}
