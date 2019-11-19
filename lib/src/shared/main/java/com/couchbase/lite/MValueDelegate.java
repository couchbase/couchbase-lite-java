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
package com.couchbase.lite;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

import com.couchbase.lite.internal.fleece.FLConstants;
import com.couchbase.lite.internal.fleece.FLDict;
import com.couchbase.lite.internal.fleece.FLEncoder;
import com.couchbase.lite.internal.fleece.FLValue;
import com.couchbase.lite.internal.fleece.MCollection;
import com.couchbase.lite.internal.fleece.MValue;


/**
 * Internal delegate class for MValue - Mutable Fleece Value
 */
final class MValueDelegate implements MValue.Delegate {

    //-------------------------------------------------------------------------
    // Public methods
    //-------------------------------------------------------------------------
    @Nullable
    @Override
    public Object toNative(@NonNull MValue mv, @Nullable MCollection parent, @NonNull AtomicBoolean cacheIt) {
        final FLValue value = mv.getValue();
        switch (value.getType()) {
            case FLConstants.ValueType.ARRAY:
                cacheIt.set(true);
                return mValueToArray(mv, parent);
            case FLConstants.ValueType.DICT:
                cacheIt.set(true);
                return mValueToDictionary(mv, parent);
            case FLConstants.ValueType.DATA:
                return new Blob("application/octet-stream", value.asData());
            default:
                return value.asObject();
        }
    }

    @Nullable
    @Override
    public MCollection collectionFromNative(@Nullable Object object) {
        if (object instanceof Dictionary) { return ((Dictionary) object).toMCollection(); }
        else if (object instanceof Array) { return ((Array) object).toMCollection(); }
        else { return null; }
    }

    @Override
    public void encodeNative(@NonNull FLEncoder enc, @Nullable Object object) {
        if (object == null) { enc.writeNull(); }
        else { enc.writeValue(object); }
    }

    //-------------------------------------------------------------------------
    // Private methods
    //-------------------------------------------------------------------------
    @NonNull
    private Object mValueToArray(@NonNull MValue mv, @Nullable MCollection parent) {
        return ((parent == null) || !parent.hasMutableChildren())
            ? new Array(mv, parent)
            : new MutableArray(mv, parent);
    }

    @NonNull
    private Object mValueToDictionary(@NonNull MValue mv, @NonNull MCollection parent) {
        final FLDict flDict = mv.getValue().asFLDict();
        final DocContext context = (DocContext) parent.getContext();
        final FLValue flType = flDict.get(Blob.META_PROP_TYPE);
        final String type = (flType == null) ? null : flType.asString();
        if (type == null) {
            if (isOldAttachment(flDict)) { return createBlob(flDict, context); }
        }
        else {
            final Object obj = createSpecialObjectOfType(type, flDict, context);
            if (obj != null) { return obj; }
        }

        if (parent.hasMutableChildren()) { return new MutableDictionary(mv, parent); }
        else { return new Dictionary(mv, parent); }
    }

    private boolean isOldAttachment(@NonNull FLDict flDict) {
        return (flDict.get("digest") != null)
            && (flDict.get("length") != null)
            && (flDict.get("stub") != null)
            && (flDict.get("revpos") != null);
    }

    @Nullable
    private Object createSpecialObjectOfType(
        @Nullable String type,
        @NonNull FLDict properties,
        @NonNull DocContext context) {
        return (!Blob.TYPE_BLOB.equals(type)) ? null : createBlob(properties, context);
    }

    @NonNull
    private Object createBlob(@NonNull FLDict properties, @NonNull DocContext context) {
        return new Blob(context.getDatabase(), properties.asDict());
    }
}
