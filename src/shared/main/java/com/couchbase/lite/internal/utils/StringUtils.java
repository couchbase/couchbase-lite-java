//
// StringUtils.java
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
package com.couchbase.lite.internal.utils;


import android.support.annotation.NonNull;


public final class StringUtils {
    private StringUtils() { }

    public static boolean isEmpty(String str) { return (str == null) || str.isEmpty(); }

    @NonNull
    public static String join(@NonNull CharSequence delimiter, @NonNull Iterable tokens) {
        final StringBuilder sb = new StringBuilder();
        boolean firstTime = true;
        for (Object token: tokens) {
            if (firstTime) { firstTime = false; }
            else { sb.append(delimiter); }
            sb.append(token);
        }
        return sb.toString();
    }
}
