//
// FLEncoder.java
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


public class FLEncoder {
    //-------------------------------------------------------------------------
    // Member variables
    //-------------------------------------------------------------------------

    private long handle;

    private boolean isMemoryManaged;

    private Object extraInfo;

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    public FLEncoder() { this(init()); }

    public FLEncoder(long handle) { this.handle = handle; }

    /*
     * Allow the FLEncoder in managed mode. In the managed mode, the IllegalStateException will be
     * thrown when the free() method is called and the finalize() will not throw the
     * IllegalStateException as the free() method is not called. Use this method when the
     * FLEncoder will be freed by the native code.
     */
    public FLEncoder managed() {
        isMemoryManaged = true;
        return this;
    }

    public void free() {
        if (isMemoryManaged) {
            throw new IllegalStateException("FLEncoder was marked as native memory managed.");
        }

        if (handle != 0) {
            free(handle);
            handle = 0L;
        }
    }

    public boolean writeString(String value) { return writeString(handle, value); }

    public boolean writeData(byte[] value) { return writeData(handle, value); }

    public boolean beginDict(long reserve) { return beginDict(handle, reserve); }

    public boolean endDict() { return endDict(handle); }

    public boolean beginArray(long reserve) { return beginArray(handle, reserve); }

    public boolean endArray() { return endArray(handle); }

    public boolean writeKey(String slice) { return writeKey(handle, slice); }

    @SuppressWarnings("unchecked") /* Allow Unchecked Cast */
    public boolean writeValue(Object value) {
        // null
        if (value == null) { return writeNull(handle); }

        // boolean
        else if (value instanceof Boolean) { return writeBool(handle, (Boolean) value); }

        // Number
        else if (value instanceof Number) {
            // Integer
            if (value instanceof Integer) { return writeInt(handle, ((Integer) value).longValue()); }

            // Long
            else if (value instanceof Long) { return writeInt(handle, (Long) value); }

            // Short
            else if (value instanceof Short) { return writeInt(handle, ((Short) value).longValue()); }

            // Double
            else if (value instanceof Double) { return writeDouble(handle,  (Double) value); }

            // Float
            else { return writeFloat(handle, (Float) value); }
        }

        // String
        else if (value instanceof String) { return writeString(handle, (String) value); }

        // byte[]
        else if (value instanceof byte[]) { return writeData(handle, (byte[]) value); }

        // List
        else if (value instanceof List) { return write((List) value); }

        // Map
        else if (value instanceof Map) { return write((Map<String, Object>) value); }

        // FLValue
        else if (value instanceof FLValue) { return writeValue(handle, ((FLValue) value).getHandle()); }

        // FLDict
        else if (value instanceof FLDict) { return writeValue(handle, ((FLDict) value).getHandle()); }

        // FLArray
        else if (value instanceof FLArray) { return writeValue(handle, ((FLArray) value).getHandle()); }

        // FLEncodable
        else if (value instanceof FLEncodable) {
            ((FLEncodable) value).encodeTo(this);
            return true;
        }

        return false;
    }

    public boolean writeNull() { return writeNull(handle); }

    public boolean write(Map<String, Object> map) {
        if (map == null) { beginDict(0); }
        else {
            beginDict(map.size());
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                writeKey(entry.getKey());
                writeValue(entry.getValue());
            }
        }
        return endDict();
    }

    public boolean write(List list) {
        if (list == null) { beginArray(0); }
        else {
            beginArray(list.size());
            for (Object item : list) { writeValue(item); }
        }
        return endArray();
    }

    public byte[] finish() throws LiteCoreException { return finish(handle); }

    public FLSliceResult finish2() throws LiteCoreException { return new FLSliceResult(finish2(handle)); }

    public Object getExtraInfo() { return extraInfo; }

    public void setExtraInfo(Object info) { extraInfo = info; }

    public void reset() { reset(handle); }

    //-------------------------------------------------------------------------
    // protected methods
    //-------------------------------------------------------------------------

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        if (handle != 0L && !isMemoryManaged) {
            throw new IllegalStateException("FLEncoder was finalized before freeing.");
        }
        super.finalize();
    }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    static native long init(); // FLEncoder FLEncoder_New(void);

    static native void free(long encoder);

    static native boolean writeNull(long encoder);

    static native boolean writeBool(long encoder, boolean value);

    static native boolean writeInt(long encoder, long value); // 64bit

    static native boolean writeFloat(long encoder, float value);

    static native boolean writeDouble(long encoder, double value);

    static native boolean writeString(long encoder, String value);

    static native boolean writeData(long encoder, byte[] value);

    static native boolean writeValue(long encoder, long value /*FLValue*/);

    static native boolean beginArray(long encoder, long reserve);

    static native boolean endArray(long encoder);

    static native boolean beginDict(long encoder, long reserve);

    static native boolean endDict(long encoder);

    static native boolean writeKey(long encoder, String slice);

    static native byte[] finish(long encoder) throws LiteCoreException;

    static native long finish2(long encoder) throws LiteCoreException;

    static native void reset(long encoder);
}
