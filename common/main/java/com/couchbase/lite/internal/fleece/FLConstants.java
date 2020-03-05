//
// FLConstants.java
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

@SuppressWarnings("ConstantName")
public final class FLConstants {
    private FLConstants() {}

    // Types of Fleece values. Basically JSON, with the addition of Data (raw blob).
    public static final class ValueType {
        private ValueType() {}

        public static final int UNDEFINED = -1; // Type of a nullptr FLValue (i.e. no such value)
        public static final int NULL = 0;
        public static final int BOOLEAN = 1;
        public static final int NUMBER = 2;
        public static final int STRING = 3;
        public static final int DATA = 4;
        public static final int ARRAY = 5;
        public static final int DICT = 6;
    }

    public static final class Error {
        private Error() {}

        public static final int NO_ERROR = 0;
        public static final int MEMORY_ERROR = 1;   // Out of memory, or allocation failed
        public static final int OUT_OF_RANGE = 2;   // Array index or iterator out of range
        public static final int INVALID_DATA = 3;   // Bad input data (NaN, non-string key, etc.)
        public static final int ENCODE_ERROR = 4;   // Structural error encoding (missing value, too many ends, etc.)
        public static final int JSON_ERROR = 5;     // Error parsing JSON
        public static final int UNKNOWN_VALUE = 6;  // Unparsable data in a Value (corrupt or from a distant future?)
        public static final int INTERNAL_ERROR = 7; // Something that shouldn't happen
        public static final int NOT_FOUND = 8;
    }
}
