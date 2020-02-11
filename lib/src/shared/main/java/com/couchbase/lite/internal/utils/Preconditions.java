//
// Copyright (c) 2019 Couchbase, Inc All rights reserved.
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
import android.support.annotation.Nullable;

import com.couchbase.lite.utils.Fn;


public final class Preconditions {
    private Preconditions() {}

    @NonNull
    public static <T> T assertNotNull(@Nullable T obj, @NonNull String name) {
        if (obj == null) { throw new IllegalArgumentException(name + " cannot be null"); }
        return obj;
    }

    public static void assertNotZero(long n, @NonNull String name) {
        if (n == 0) { throw new IllegalArgumentException(name + " cannot be 0"); }
    }

    public static void assertNotEmpty(String str, @NonNull String name) {
        if (StringUtils.isEmpty(str)) { throw new IllegalArgumentException(name + " cannot be empty"); }
    }

    public static <T> void assertThat(T obj, @NonNull String msg, @NonNull Fn.Predicate<T> predicate) {
        if (!predicate.test(obj)) { throw new IllegalArgumentException(msg); }
    }
}
