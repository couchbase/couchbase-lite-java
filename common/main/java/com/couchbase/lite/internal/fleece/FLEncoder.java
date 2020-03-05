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
import com.couchbase.lite.internal.core.C4NativePeer;


@SuppressWarnings("PMD.TooManyMethods")
public class FLEncoder extends C4NativePeer {
    //-------------------------------------------------------------------------
    // Member variables
    //-------------------------------------------------------------------------

    private final boolean isMemoryManaged;

    private Object extraInfo;

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    public FLEncoder() { this(init()); }

    public FLEncoder(long handle) { this(handle, false); }

    /*
     * Allow the FLEncoder in managed mode. In the managed mode, the IllegalStateException will be
     * thrown when the free() method is called and the finalize() will not throw the
     * IllegalStateException as the free() method is not called. Use this method when the
     * FLEncoder will be freed by the native code.
     *
     * ??? Why are these things *ever* not memory managed?
     */
    public FLEncoder(long handle, boolean managed) {
        super(handle);
        this.isMemoryManaged = managed;
    }

    public void free() {
        if (isMemoryManaged) { throw new IllegalStateException("Attempt to free a managed FLEncoder"); }

        final long handle = getPeerAndClear();

        if (handle != 0) { free(handle); }
    }

    public boolean writeString(String value) { return writeString(getPeer(), value); }

    public boolean writeData(byte[] value) { return writeData(getPeer(), value); }

    public boolean beginDict(long reserve) { return beginDict(getPeer(), reserve); }

    public boolean endDict() { return endDict(getPeer()); }

    public boolean beginArray(long reserve) { return beginArray(getPeer(), reserve); }

    public boolean endArray() { return endArray(getPeer()); }

    public boolean writeKey(String slice) { return writeKey(getPeer(), slice); }

    @SuppressWarnings({"unchecked", "PMD.NPathComplexity"})
    public boolean writeValue(Object value) {
        final long peer = getPeer();
        // null
        if (value == null) { return writeNull(peer); }

        // boolean
        if (value instanceof Boolean) { return writeBool(peer, (Boolean) value); }

        // Number
        if (value instanceof Number) {
            // Integer
            if (value instanceof Integer) { return writeInt(peer, ((Integer) value).longValue()); }

            // Long
            if (value instanceof Long) { return writeInt(peer, (Long) value); }

            // Short
            if (value instanceof Short) { return writeInt(peer, ((Short) value).longValue()); }

            // Double
            if (value instanceof Double) { return writeDouble(peer, (Double) value); }

            // Float
            return writeFloat(peer, (Float) value);
        }

        // String
        if (value instanceof String) { return writeString(peer, (String) value); }

        // byte[]
        if (value instanceof byte[]) { return writeData(peer, (byte[]) value); }

        // List
        if (value instanceof List) { return write((List<?>) value); }

        // Map
        if (value instanceof Map) { return write((Map<String, Object>) value); }

        // FLValue
        if (value instanceof FLValue) {
            return ((FLValue) value).withContent(hdl -> (writeValue(peer, hdl)));
        }

        // FLDict
        if (value instanceof FLDict) {
            return ((FLDict) value).withContent(hdl -> (writeValue(peer, hdl)));
        }

        // FLArray
        if (value instanceof FLArray) {
            return ((FLArray) value).withContent(hdl -> (writeValue(peer, hdl)));
        }

        // FLEncodable
        if (value instanceof FLEncodable) {
            ((FLEncodable) value).encodeTo(this);
            return true;
        }

        return false;
    }

    public boolean writeNull() { return writeNull(getPeer()); }

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

    public boolean write(List<?> list) {
        if (list == null) { beginArray(0); }
        else {
            beginArray(list.size());
            for (Object item : list) { writeValue(item); }
        }
        return endArray();
    }

    public byte[] finish() throws LiteCoreException { return finish(getPeer()); }

    public FLSliceResult finish2() throws LiteCoreException {
        return new FLSliceResult(finish2(getPeer()));
    }

    public FLSliceResult managedFinish2() throws LiteCoreException {
        return new FLSliceResult(finish2(getPeer()), true);
    }

    public Object getExtraInfo() { return extraInfo; }

    public void setExtraInfo(Object info) { extraInfo = info; }

    public void reset() { reset(getPeer()); }

    //-------------------------------------------------------------------------
    // protected methods
    //-------------------------------------------------------------------------

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        if ((!isMemoryManaged) && (get() != 0L)) {
            throw new IllegalStateException("FLEncoder finalized without being freed: " + this);
        }
        super.finalize();
    }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    private static native long init(); // FLEncoder FLEncoder_New(void);

    private static native void free(long encoder);

    private static native boolean writeNull(long encoder);

    private static native boolean writeBool(long encoder, boolean value);

    private static native boolean writeInt(long encoder, long value); // 64bit

    private static native boolean writeFloat(long encoder, float value);

    private static native boolean writeDouble(long encoder, double value);

    private static native boolean writeString(long encoder, String value);

    private static native boolean writeData(long encoder, byte[] value);

    private static native boolean writeValue(long encoder, long value /*FLValue*/);

    private static native boolean beginArray(long encoder, long reserve);

    private static native boolean endArray(long encoder);

    private static native boolean beginDict(long encoder, long reserve);

    private static native boolean endDict(long encoder);

    private static native boolean writeKey(long encoder, String slice);

    private static native byte[] finish(long encoder) throws LiteCoreException;

    private static native long finish2(long encoder) throws LiteCoreException;

    private static native void reset(long encoder);
}
