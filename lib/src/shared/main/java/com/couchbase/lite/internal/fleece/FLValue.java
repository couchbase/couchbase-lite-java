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

import java.util.List;
import java.util.Map;

import com.couchbase.lite.LiteCoreException;


public class FLValue {

    //-------------------------------------------------------------------------
    // public static methods
    //-------------------------------------------------------------------------

    public static FLValue fromData(AllocSlice slice) {
        if (slice == null) { return null; }
        final long value = fromData(slice.getHandle());
        return value != 0 ? new FLValue(value) : null;
    }

    public static FLValue fromData(byte[] data) { return new FLValue(fromTrustedData(data)); }

    public static Object toObject(FLValue flValue) { return flValue.asObject(); }

    public static String json5ToJson(String json5) throws LiteCoreException { return JSON5ToJSON(json5); }

    //-------------------------------------------------------------------------
    // Member Variables
    //-------------------------------------------------------------------------

    private long handle; // pointer to FLValue

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    public FLValue(long handle) {
        if (handle == 0L) { throw new IllegalArgumentException("handle is 0L."); }
        this.handle = handle;
    }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    public int getType() { return getType(handle); }

    public boolean isNumber() { return getType() == FLConstants.ValueType.NUMBER; }

    public boolean isInteger() { return isInteger(handle); }

    public boolean isDouble() { return isDouble(handle); }

    public boolean isUnsigned() { return isUnsigned(handle); }

    public String toStr() { return toString(handle); }

    public String toJSON() { return toJSON(handle); }

    public String toJSON5() { return toJSON5(handle); }

    public boolean asBool() { return asBool(handle); }

    public long asUnsigned() { return asUnsigned(handle); }

    public long asInt() { return asInt(handle); }

    public byte[] asData() { return asData(handle); }

    public float asFloat() { return asFloat(handle); }

    public double asDouble() { return asDouble(handle); }

    // ??? If we are out of memory or the string cannot be decoded, we just lose it
    public String asString() {
        try { return asString(handle); }
        catch (LiteCoreException ignore) {}
        return null;
    }

    public FLDict asFLDict() { return new FLDict(asDict(handle)); }

    public FLArray asFLArray() { return new FLArray(asArray(handle)); }

    public Map<String, Object> asDict() { return asFLDict().asDict(); }

    public List<Object> asArray() { return asFLArray().asArray(); }

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
    long getHandle() { return handle; }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    /**
     * Returns a pointer to the root value in the encoded data
     *
     * @param data FLSlice (same with slice)
     * @return long (FLValue - const struct _FLValue*)
     */
    static native long fromTrustedData(byte[] data);

    /**
     * Returns the data type of an arbitrary Value.
     *
     * @param value FLValue
     * @return int (FLValueType)
     */
    static native int getType(long value);

    /**
     * Is this value an integer?
     *
     * @param value FLValue
     * @return boolean
     */
    static native boolean isInteger(long value);

    /**
     * Is this a 64-bit floating-point value?
     *
     * @param value FLValue
     * @return boolean
     */
    static native boolean isDouble(long value);

    /**
     * Returns true if the value is non-nullptr and represents an _unsigned_ integer that can only
     * be represented natively as a `uint64_t`.
     *
     * @param value FLValue
     * @return boolean
     */
    static native boolean isUnsigned(long value);

    /**
     * Returns a value coerced to boolean.
     *
     * @param value FLValue
     * @return boolean
     */
    static native boolean asBool(long value);

    /**
     * Returns a value coerced to an unsigned integer.
     *
     * @param value FLValue
     * @return long
     */
    static native long asUnsigned(long value);

    /**
     * Returns a value coerced to an integer.
     * NOTE: litecore treats integer with 2^64. So this JNI method returns long value
     *
     * @param value FLValue
     * @return long
     */
    static native long asInt(long value);

    /**
     * Returns a value coerced to a 32-bit floating point number.
     *
     * @param value FLValue
     * @return float
     */
    static native float asFloat(long value);

    /**
     * Returns a value coerced to a 64-bit floating point number.
     *
     * @param value FLValue
     * @return double
     */
    static native double asDouble(long value);

    /**
     * Returns the exact contents of a string value, or null for all other types.
     *
     * @param value FLValue
     * @return String
     */
    static native String asString(long value) throws LiteCoreException;

    /**
     * Returns the exact contents of a data value, or null for all other types.
     *
     * @param value FLValue
     * @return byte[]
     */
    static native byte[] asData(long value);

    /**
     * If a FLValue represents an array, returns it cast to FLArray, else nullptr.
     *
     * @param value FLValue
     * @return long (FLArray)
     */
    static native long asArray(long value);

    /**
     * If a FLValue represents an array, returns it cast to FLDict, else nullptr.
     *
     * @param value FLValue
     * @return long (FLDict)
     */
    static native long asDict(long value);

    /**
     * Converts valid JSON5 to JSON.
     *
     * @param json5 String
     * @return JSON String
     * @throws LiteCoreException
     */
    @SuppressWarnings({"MethodName", "PMD.MethodNamingConventions"})
    static native String JSON5ToJSON(String json5) throws LiteCoreException;

    static native long fromData(long slice);

    static native String toString(long handle);

    static native String toJSON(long handle);

    static native String toJSON5(long handle);
}

