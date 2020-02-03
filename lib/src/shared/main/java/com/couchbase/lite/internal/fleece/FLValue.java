//
// FLValue.java
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

import java.util.List;
import java.util.Map;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.utils.Preconditions;
import com.couchbase.lite.utils.Fn;


public class FLValue {

    //-------------------------------------------------------------------------
    // public static methods
    //-------------------------------------------------------------------------

    @Nullable
    public static FLValue fromData(AllocSlice slice) {
        if (slice == null) { return null; }
        final long value = fromData(slice.getHandle());
        return value == 0 ? null : new FLValue(value);
    }

    /**
     * Converts valid JSON5 to JSON.
     *
     * @param json5 String
     * @return JSON String
     * @throws LiteCoreException on parse failure
     */
    @Nullable
    public static String json5ToJson(String json5) throws LiteCoreException { return JSON5ToJSON(json5); }

    @NonNull
    public static FLValue fromData(byte[] data) { return new FLValue(fromTrustedData(data)); }

    @Nullable
    public static Object toObject(FLValue flValue) { return flValue.asObject(); }


    //-------------------------------------------------------------------------
    // Member Variables
    //-------------------------------------------------------------------------

    private final long handle; // pointer to FLValue

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    public FLValue(long handle) {
        Preconditions.checkArgNotZero(handle, "handle");
        this.handle = handle;
    }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    /**
     * Returns the data type of an arbitrary Value.
     *
     * @return int (FLValueType)
     */
    public int getType() { return getType(handle); }

    /**
     * Is this value a number?
     *
     * @return true if value is a number
     */
    public boolean isNumber() { return getType() == FLConstants.ValueType.NUMBER; }

    /**
     * Is this value an integer?
     *
     * @return true if value is a number
     */
    public boolean isInteger() { return isInteger(handle); }

    /**
     * Returns true if the value is non-nullptr and represents an _unsigned_ integer that can only
     * be represented natively as a `uint64_t`.
     *
     * @return boolean
     */
    public boolean isUnsigned() { return isUnsigned(handle); }

    /**
     * Is this a 64-bit floating-point value?
     *
     * @return true if value is a double
     */
    public boolean isDouble() { return isDouble(handle); }

    /**
     * Returns the string representation.
     *
     * @return string rep
     */
    public String toStr() { return toString(handle); }

    /**
     * Returns the json representation.
     *
     * @return json rep
     */
    public String toJSON() { return toJSON(handle); }

    /**
     * Returns the string representation.
     *
     * @return json5 rep
     */
    public String toJSON5() { return toJSON5(handle); }

    /**
     * Returns the exact contents of a data value, or null for all other types.
     *
     * @return byte[]
     */
    public byte[] asData() { return asData(handle); }

    /**
     * Returns a value coerced to boolean.
     *
     * @return boolean
     */
    public boolean asBool() { return asBool(handle); }

    /**
     * Returns a value coerced to an integer.
     * NOTE: litecore treats integer with 2^64. So this JNI method returns long value
     *
     * @return long
     */
    public long asInt() { return asInt(handle); }

    /**
     * Returns a value coerced to an unsigned integer.
     *
     * @return long
     */
    public long asUnsigned() { return asUnsigned(handle); }

    /**
     * Returns a value coerced to a 32-bit floating point number.
     *
     * @return float
     */
    public float asFloat() { return asFloat(handle); }

    /**
     * Returns a value coerced to a 64-bit floating point number.
     *
     * @return double
     */
    public double asDouble() { return asDouble(handle); }

    /**
     * Returns the exact contents of a string value, or null for all other types.
     * ??? If we are out of memory or the string cannot be decoded, we just lose it
     *
     * @return String
     */
    public String asString() {
        try { return asString(handle); }
        catch (LiteCoreException ignore) {}
        return null;
    }

    public List<Object> asArray() { return asFLArray().asArray(); }

    public <T> List<T> asTypedArray() { return asFLArray().asTypedArray(); }

    /**
     * Returns the contents as a dictionary.
     *
     * @return String
     */
    public FLDict asFLDict() { return new FLDict(asDict(handle)); }

    /**
     * If a FLValue represents an array, returns it cast to FLDict, else nullptr.
     *
     * @return long (FLDict)
     */
    public Map<String, Object> asDict() { return asFLDict().asDict(); }

    /**
     * Return an object of the appropriate type.
     *
     * @return Object
     */
    public Object asObject() {
        switch (getType(handle)) {
            case FLConstants.ValueType.NULL:
                return null;
            case FLConstants.ValueType.BOOLEAN:
                return Boolean.valueOf(asBool());
            case FLConstants.ValueType.NUMBER:
                if (isInteger()) { return (isUnsigned()) ? Long.valueOf(asUnsigned()) : Long.valueOf(asInt()); }
                if (isDouble()) { return Double.valueOf(asDouble()); }
                return Float.valueOf(asFloat());
            case FLConstants.ValueType.STRING:
                return asString();
            case FLConstants.ValueType.DATA:
                return asData();
            case FLConstants.ValueType.ARRAY:
                return asArray();
            case FLConstants.ValueType.DICT:
                return asDict();
            default:
                return null;
        }
    }

    //-------------------------------------------------------------------------
    // package level access
    //-------------------------------------------------------------------------

    <T> T withContent(Fn.Function<Long, T> fn) { return fn.apply(handle); }

    FLArray asFLArray() { return new FLArray(asArray(handle)); }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    /**
     * Returns a pointer to the root value in the encoded data
     *
     * @param data FLSlice (same with slice)
     * @return long (FLValue - const struct _FLValue*)
     */
    private static native long fromTrustedData(byte[] data);

    private static native long fromData(long slice);

    private static native int getType(long value);

    private static native boolean isInteger(long value);

    private static native boolean isUnsigned(long value);

    private static native boolean isDouble(long value);

    private static native String toString(long handle);

    private static native String toJSON(long handle);

    private static native String toJSON5(long handle);

    private static native byte[] asData(long value);

    private static native boolean asBool(long value);

    private static native long asUnsigned(long value);

    private static native long asInt(long value);

    private static native float asFloat(long value);

    private static native double asDouble(long value);

    private static native String asString(long value) throws LiteCoreException;

    private static native long asArray(long value);

    private static native long asDict(long value);

    @SuppressWarnings({"MethodName", "PMD.MethodNamingConventions"})
    private static native String JSON5ToJSON(String json5) throws LiteCoreException;
}

