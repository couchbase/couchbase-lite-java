//
// Dictionary.java
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

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.couchbase.lite.internal.fleece.FLEncodable;
import com.couchbase.lite.internal.fleece.FLEncoder;
import com.couchbase.lite.internal.fleece.MCollection;
import com.couchbase.lite.internal.fleece.MContext;
import com.couchbase.lite.internal.fleece.MDict;
import com.couchbase.lite.internal.fleece.MValue;
import com.couchbase.lite.internal.utils.DateUtils;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * Dictionary provides readonly access to dictionary data.
 */
public class Dictionary implements DictionaryInterface, FLEncodable, Iterable<String> {
    //-------------------------------------------------------------------------
    // member variables
    //-------------------------------------------------------------------------
    @NonNull
    protected final Object lock;
    @NonNull
    protected final MDict internalDict;

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    Dictionary() {
        internalDict = new MDict();
        lock = getSharedLock();
    }

    Dictionary(MValue mv, MCollection parent) {
        internalDict = new MDict();
        internalDict.initInSlot(mv, parent);
        lock = getSharedLock();
    }

    Dictionary(MDict mDict, boolean isMutable) {
        internalDict = new MDict();
        internalDict.initAsCopyOf(mDict, isMutable);
        lock = getSharedLock();
    }

    //-------------------------------------------------------------------------
    // API - public methods
    //-------------------------------------------------------------------------

    //-------------------------------------------------------------------------
    // Implementation of ReadOnlyDictionaryInterface
    //-------------------------------------------------------------------------

    /**
     * Gets a number of the entries in the dictionary.
     *
     * @return the size of the dictionary
     */
    @Override
    public int count() {
        synchronized (lock) { return (int) internalDict.count(); }
    }

    @NonNull
    @Override
    public List<String> getKeys() {
        synchronized (lock) { return internalDict.getKeys(); }
    }

    /**
     * Gets a property's value as an object. The object types are Blob, Array,
     * Dictionary, Number, or String based on the underlying data type; or nil if the
     * property value is null or the property doesn't exist.
     *
     * @param key the key.
     * @return the object value or null.
     */
    @Nullable
    @Override
    public Object getValue(@NonNull String key) {
        Preconditions.assertNotNull(key, "key");
        synchronized (lock) { return getMValue(internalDict, key).asNative(internalDict); }
    }

    /**
     * Gets a property's value as a String. Returns null if the value doesn't exist, or its value is not a String.
     *
     * @param key the key
     * @return the String or null.
     */
    @Nullable
    @Override
    public String getString(@NonNull String key) {
        Preconditions.assertNotNull(key, "key");
        synchronized (lock) {
            final Object obj = getMValue(internalDict, key).asNative(internalDict);
            return obj instanceof String ? (String) obj : null;
        }
    }

    /**
     * Gets a property's value as a Number. Returns null if the value doesn't exist, or its value is not a Number.
     *
     * @param key the key
     * @return the Number or nil.
     */
    @Nullable
    @Override
    public Number getNumber(@NonNull String key) {
        Preconditions.assertNotNull(key, "key");
        synchronized (lock) { return CBLConverter.asNumber(getMValue(internalDict, key).asNative(internalDict)); }
    }

    /**
     * Gets a property's value as an int.
     * Floating point values will be rounded. The value `true` is returned as 1, `false` as 0.
     * Returns 0 if the value doesn't exist or does not have a numeric value.
     *
     * @param key the key
     * @return the int value.
     */
    @Override
    public int getInt(@NonNull String key) {
        Preconditions.assertNotNull(key, "key");
        synchronized (lock) { return CBLConverter.asInteger(getMValue(internalDict, key), internalDict); }
    }

    /**
     * Gets a property's value as an long.
     * Floating point values will be rounded. The value `true` is returned as 1, `false` as 0.
     * Returns 0 if the value doesn't exist or does not have a numeric value.
     *
     * @param key the key
     * @return the long value.
     */
    @Override
    public long getLong(@NonNull String key) {
        Preconditions.assertNotNull(key, "key");
        synchronized (lock) { return CBLConverter.asLong(getMValue(internalDict, key), internalDict); }
    }

    /**
     * Gets a property's value as an float.
     * Integers will be converted to float. The value `true` is returned as 1.0, `false` as 0.0.
     * Returns 0.0 if the value doesn't exist or does not have a numeric value.
     *
     * @param key the key
     * @return the float value.
     */
    @Override
    public float getFloat(@NonNull String key) {
        Preconditions.assertNotNull(key, "key");
        synchronized (lock) { return CBLConverter.asFloat(getMValue(internalDict, key), internalDict); }
    }

    /**
     * Gets a property's value as an double.
     * Integers will be converted to double. The value `true` is returned as 1.0, `false` as 0.0.
     * Returns 0.0 if the property doesn't exist or does not have a numeric value.
     *
     * @param key the key
     * @return the double value.
     */
    @Override
    public double getDouble(@NonNull String key) {
        Preconditions.assertNotNull(key, "key");
        synchronized (lock) { return CBLConverter.asDouble(getMValue(internalDict, key), internalDict); }
    }

    /**
     * Gets a property's value as a boolean. Returns true if the value exists, and is either `true`
     * or a nonzero number.
     *
     * @param key the key
     * @return the boolean value.
     */
    @Override
    public boolean getBoolean(@NonNull String key) {
        Preconditions.assertNotNull(key, "key");
        synchronized (lock) { return CBLConverter.asBoolean(getMValue(internalDict, key).asNative(internalDict)); }
    }

    /**
     * Gets a property's value as a Blob.
     * Returns null if the value doesn't exist, or its value is not a Blob.
     *
     * @param key the key
     * @return the Blob value or null.
     */
    @Nullable
    @Override
    public Blob getBlob(@NonNull String key) {
        Preconditions.assertNotNull(key, "key");
        synchronized (lock) {
            final Object obj = getMValue(internalDict, key).asNative(internalDict);
            return obj instanceof Blob ? (Blob) obj : null;
        }
    }

    /**
     * Gets a property's value as a Date.
     * JSON does not directly support dates, so the actual property value must be a string, which is
     * then parsed according to the ISO-8601 date format (the default used in JSON.)
     * Returns null if the value doesn't exist, is not a string, or is not parsable as a date.
     * NOTE: This is not a generic date parser! It only recognizes the ISO-8601 format, with or
     * without milliseconds.
     *
     * @param key the key
     * @return the Date value or null.
     */
    @Nullable
    @Override
    public Date getDate(@NonNull String key) {
        Preconditions.assertNotNull(key, "key");
        return DateUtils.fromJson(getString(key));
    }

    /**
     * Get a property's value as a Array, which is a mapping object of an array value.
     * Returns null if the property doesn't exists, or its value is not an array.
     *
     * @param key the key.
     * @return the Array object.
     */
    @Nullable
    @Override
    public Array getArray(@NonNull String key) {
        Preconditions.assertNotNull(key, "key");
        synchronized (lock) {
            final Object obj = getMValue(internalDict, key).asNative(internalDict);
            return obj instanceof Array ? (Array) obj : null;
        }
    }

    /**
     * Get a property's value as a Dictionary, which is a mapping object of an dictionary value.
     * Returns null if the property doesn't exists, or its value is not an dictionary.
     *
     * @param key the key.
     * @return the Dictionary object or null if the key doesn't exist.
     */
    @Nullable
    @Override
    public Dictionary getDictionary(@NonNull String key) {
        Preconditions.assertNotNull(key, "key");
        synchronized (lock) {
            final Object obj = getMValue(internalDict, key).asNative(internalDict);
            return obj instanceof Dictionary ? (Dictionary) obj : null;
        }
    }

    /**
     * Gets content of the current object as an Map. The values contained in the returned
     * Map object are all JSON based values.
     *
     * @return the Map object representing the content of the current object in the JSON format.
     */
    @NonNull
    @Override
    public Map<String, Object> toMap() {
        final Map<String, Object> result = new HashMap<>();
        synchronized (lock) {
            for (String key : internalDict) {
                result.put(key, Fleece.toObject(getMValue(internalDict, key).asNative(internalDict)));
            }
        }
        return result;
    }

    /**
     * Tests whether a property exists or not.
     * This can be less expensive than getValue(String), because it does not have to allocate an Object for the
     * property value.
     *
     * @param key the key
     * @return the boolean value representing whether a property exists or not.
     */
    @Override
    public boolean contains(@NonNull String key) {
        Preconditions.assertNotNull(key, "key");
        synchronized (lock) { return !getMValue(internalDict, key).isEmpty(); }
    }

    /**
     * Return a mutable copy of the dictionary
     *
     * @return the MutableDictionary instance
     */
    @NonNull
    public MutableDictionary toMutable() {
        synchronized (lock) { return new MutableDictionary(internalDict, true); }
    }

    //-------------------------------------------------------------------------
    // Implementation of FLEncodable
    //-------------------------------------------------------------------------

    /**
     * encodeTo(FlEncoder) is internal method. Please don't use this method.
     */
    @Override
    public void encodeTo(FLEncoder enc) { internalDict.encodeTo(enc); }

    //---------------------------------------------
    // Iterable implementation
    //---------------------------------------------

    @NonNull
    @Override
    public Iterator<String> iterator() { return getKeys().iterator(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (!(o instanceof Dictionary)) { return false; }

        final Dictionary m = (Dictionary) o;

        if (m.count() != count()) { return false; }
        for (String key : this) {
            final Object value = getValue(key);
            if (value != null) {
                if (!value.equals(m.getValue(key))) { return false; }
            }
            else {
                if (!(m.getValue(key) == null && m.contains(key))) { return false; }
            }
        }
        return true;
    }

    //---------------------------------------------
    // Override
    //---------------------------------------------

    @Override
    public int hashCode() {
        int h = 0;
        for (String key : this) { h += hashCode(key, getValue(key)); }
        return h;
    }

    @SuppressWarnings("PMD.ConsecutiveLiteralAppends")
    @NonNull
    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder("Dictionary")
            .append((internalDict.isMutable()) ? '+' : '.')
            .append((internalDict.isMutated()) ? '!' : '.')
            .append('{');
        boolean first = true;
        for (String key : getKeys()) {
            if (first) { first = false; }
            else { buf.append(','); }
            buf.append(key).append("=>").append(getValue(key));
        }
        return buf.append("}").toString();
    }

    //---------------------------------------------
    // protected level access
    //---------------------------------------------

    protected boolean isEmpty() { return count() == 0; }

    //---------------------------------------------
    // package level access
    //---------------------------------------------

    MCollection toMCollection() { return internalDict; }

    //---------------------------------------------
    // private
    //---------------------------------------------

    @NonNull
    private Object getSharedLock() {
        final MContext context = internalDict.getContext();
        return ((context == null) || (context == MContext.NULL))
            ? new Object()
            : ((DocContext) context).getDatabase().getLock();
    }

    // hashCode for pair of key and value
    private int hashCode(String key, Object value) {
        return (key == null ? 0 : key.hashCode()) ^ (value == null ? 0 : value.hashCode());
    }

    @NonNull
    private MValue getMValue(MDict dict, String key) { return dict.get(key); }
}
