//
// MDict.java
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.couchbase.lite.internal.utils.Preconditions;


public class MDict extends MCollection implements Iterable<String> {
    private final List<String> newKey = new ArrayList<>();
    private Map<String, MValue> valueMap = new HashMap<>();
    private FLDict flDict;
    private long valCount;


    public void initInSlot(MValue mv, MCollection parent) {
        initInSlot(mv, parent, parent != null && parent.hasMutableChildren());
    }

    public void initAsCopyOf(MDict d, boolean isMutable) {
        super.initAsCopyOf(d, isMutable);
        flDict = d.flDict;
        valueMap = new HashMap<>(d.valueMap);
        valCount = d.valCount;
    }

    //---------------------------------------------
    // Public methods
    //---------------------------------------------

    /* Properties */
    public long count() { return valCount; }

    /* Iterable */
    @NonNull
    @Override
    public Iterator<String> iterator() { return getKeys().iterator(); }

    /* Encodable */

    @Override
    public void encodeTo(FLEncoder enc) {
        if (!isMutated()) {
            if (flDict != null) { enc.writeValue(flDict); }
            else {
                enc.beginDict(0);
                enc.endDict();
            }
        }
        else {
            enc.beginDict(valCount);
            for (Map.Entry<String, MValue> entry : valueMap.entrySet()) {
                final MValue value = entry.getValue();
                if (!value.isEmpty()) {
                    enc.writeKey(entry.getKey());
                    value.encodeTo(enc);
                }
            }

            if ((flDict != null) && (flDict.count() > 0)) {
                final FLDictIterator itr = new FLDictIterator();
                try {
                    itr.begin(flDict);
                    String key;
                    while ((key = itr.getKeyString()) != null) {
                        if (!valueMap.containsKey(key)) {
                            enc.writeKey(key);
                            enc.writeValue(itr.getValue());
                        }
                        itr.next();
                    }
                }
                finally {
                    itr.free();
                }
            }
            enc.endDict();
        }
    }

    public boolean clear() {
        Preconditions.assertThat(this, "Cannot call set on a non-mutable MDict", MCollection::isMutable);

        if (valCount == 0) { return true; }

        mutate();
        valueMap.clear();

        if ((flDict != null) && (flDict.count() > 0)) {
            final FLDictIterator itr = new FLDictIterator();
            try {
                itr.begin(flDict);
                String key;
                while ((key = itr.getKeyString()) != null) {
                    valueMap.put(key, MValue.EMPTY);
                    itr.next();
                }
            }
            finally {
                itr.free();
            }
        }

        valCount = 0;
        return true;
    }

    public boolean contains(String key) {
        Preconditions.assertNotNull(key, "key");
        final MValue v = valueMap.get(key);
        return (v != null) ? !v.isEmpty() : ((flDict != null) && (flDict.get(key) != null));
    }

    public List<String> getKeys() {
        final List<String> keys = new ArrayList<>();
        for (Map.Entry<String, MValue> entry : valueMap.entrySet()) {
            if (!entry.getValue().isEmpty()) { keys.add(entry.getKey()); }
        }

        if ((flDict != null) && (flDict.count() > 0)) {
            final FLDictIterator itr = new FLDictIterator();
            try {
                itr.begin(flDict);
                String key;
                while ((key = itr.getKeyString()) != null) {
                    if (!valueMap.containsKey(key)) { keys.add(key); }
                    itr.next();
                }
            }
            finally {
                itr.free();
            }
        }

        return keys;
    }

    public boolean remove(String key) { return set(key, MValue.EMPTY); }

    @NonNull
    public MValue get(String key) {
        Preconditions.assertNotNull(key, "key");

        final MValue v = valueMap.get(key);
        if (v != null) { return v; }

        final FLValue value = flDict != null ? flDict.get(key) : null;
        return (value == null) ? MValue.EMPTY : setInMap(key, new MValue(value));
    }

    public boolean set(String key, MValue value) {
        Preconditions.assertNotNull(key, "key");
        Preconditions.assertThat(this, "Cannot call set on a non-mutable MDict", MCollection::isMutable);

        final MValue oValue = valueMap.get(key);
        if (oValue != null) {
            // Found in valueMap; update value:
            if (value.isEmpty() && oValue.isEmpty()) { return true; }
            mutate();
            valCount += (value.isEmpty() ? 0 : 1) - (oValue.isEmpty() ? 0 : 1);
            valueMap.put(key, value);
        }
        else {
            // Not found; check flDict:
            if (flDict != null && flDict.get(key) != null) {
                if (value.isEmpty()) { valCount--; }
            }
            else {
                if (value.isEmpty()) { return true; }
                else { valCount++; }
            }

            mutate();
            setInMap(key, value);
        }
        return true;
    }

    //---------------------------------------------
    // Protected methods
    //---------------------------------------------

    @Override
    protected void initInSlot(MValue mv, MCollection parent, boolean isMutable) {
        super.initInSlot(mv, parent, isMutable);
        if (flDict != null) { throw new IllegalStateException("flDict is not null"); }

        final FLValue value = mv.getValue();
        if (value != null) {
            flDict = value.asFLDict();
            valCount = flDict.count();
        }
        else {
            flDict = null;
            valCount = 0;
        }
    }

    //---------------------------------------------
    // Private (in class only)
    //---------------------------------------------

    private MValue setInMap(String key, MValue value) {
        newKey.add(key);
        valueMap.put(key, value);
        return value;
    }
}
