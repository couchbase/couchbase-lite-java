//
// Parameters.java
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
import java.util.Map;

import com.couchbase.lite.internal.fleece.AllocSlice;
import com.couchbase.lite.internal.fleece.FLEncoder;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * A Parameters object used for setting values to the query parameters defined in the query.
 */
public final class Parameters {

    //---------------------------------------------
    // member variables
    //---------------------------------------------
    @NonNull
    private final Map<String, Object> map;
    private final boolean readonly;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------
    public Parameters() { this(null); }

    public Parameters(@Nullable Parameters parameters) { this(parameters, false); }

    private Parameters(@Nullable Parameters parameters, boolean readonly) {
        map = (parameters == null) ? new HashMap<>() : new HashMap<>(parameters.map);
        this.readonly = readonly;
    }

    //---------------------------------------------
    // public API
    //---------------------------------------------

    /**
     * Gets a parameter's value.
     *
     * @param name The parameter name.
     * @return The parameter value.
     */
    @Nullable
    public Object getValue(@NonNull String name) {
        Preconditions.checkArgNotNull(name, "name");
        return map.get(name);
    }

    /**
     * Set an String value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     *
     * @param name  The parameter name.
     * @param value The String value.
     * @return The self object.
     */
    @NonNull
    public Parameters setString(@NonNull String name, @Nullable String value) { return setValue(name, value); }

    /**
     * Set an Number value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     *
     * @param name  The parameter name.
     * @param value The Number value.
     * @return The self object.
     */
    @NonNull
    public Parameters setNumber(@NonNull String name, @Nullable Number value) { return setValue(name, value); }

    /**
     * Set an int value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     *
     * @param name  The parameter name.
     * @param value The int value.
     * @return The self object.
     */
    @NonNull
    public Parameters setInt(@NonNull String name, int value) { return setValue(name, value); }

    /**
     * Set an long value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     *
     * @param name  The parameter name.
     * @param value The long value.
     * @return The self object.
     */
    @NonNull
    public Parameters setLong(@NonNull String name, long value) { return setValue(name, value); }

    /**
     * Set a float value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     *
     * @param name  The parameter name.
     * @param value The float value.
     * @return The self object.
     */
    @NonNull
    public Parameters setFloat(@NonNull String name, float value) { return setValue(name, value); }

    /**
     * Set a double value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     *
     * @param name  The parameter name.
     * @param value The double value.
     * @return The self object.
     */
    @NonNull
    public Parameters setDouble(@NonNull String name, double value) { return setValue(name, value); }

    /**
     * Set a boolean value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     *
     * @param name  The parameter name.
     * @param value The boolean value.
     * @return The self object.
     */
    @NonNull
    public Parameters setBoolean(@NonNull String name, boolean value) { return setValue(name, value); }

    /**
     * Set a date value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     *
     * @param name  The parameter name.
     * @param value The date value.
     * @return The self object.
     */
    @NonNull
    public Parameters setDate(@NonNull String name, @Nullable Date value) { return setValue(name, value); }

    /**
     * Set the Blob value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     *
     * @param name  The parameter name.
     * @param value The Blob value.
     * @return The self object.
     */
    @NonNull
    public Parameters setBlob(@NonNull String name, @Nullable Blob value) { return setValue(name, value); }

    /**
     * Set the Dictionary value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     *
     * @param name  The parameter name.
     * @param value The Dictionary value.
     * @return The self object.
     */
    @NonNull
    public Parameters setDictionary(@NonNull String name, @Nullable Dictionary value) { return setValue(name, value); }

    /**
     * Set the Array value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     *
     * @param name  The parameter name.
     * @param value The Array value.
     * @return The self object.
     */
    @NonNull
    public Parameters setArray(@NonNull String name, @Nullable Array value) { return setValue(name, value); }

    /**
     * Set a value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     *
     * @param name  The parameter name.
     * @param value The value.
     * @return The self object.
     */
    @NonNull
    public Parameters setValue(@NonNull String name, @Nullable Object value) {
        Preconditions.checkArgNotNull(name, "name");
        if (readonly) { throw new IllegalStateException("Parameters is readonly mode."); }
        map.put(name, value);
        return this;
    }

    //---------------------------------------------
    // package level access
    //---------------------------------------------
    Parameters readonlyCopy() { return new Parameters(this, true); }

    AllocSlice encode() throws LiteCoreException {
        final FLEncoder encoder = new FLEncoder();
        try {
            encoder.write(map);
            return encoder.finish2();
        }
        finally { encoder.free(); }
    }
}
