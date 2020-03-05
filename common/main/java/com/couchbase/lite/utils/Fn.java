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
package com.couchbase.lite.utils;


public interface Fn {
    @FunctionalInterface
    interface FunctionThrows<T, R, E extends Throwable> { R apply(T x) throws E; }
    @FunctionalInterface
    interface Function<T, R> { R apply(T x); }
    @FunctionalInterface
    interface Predicate<T> { boolean test(T x); }
    @FunctionalInterface
    interface Provider<T> { T get(); }
    @FunctionalInterface
    interface ConsumerThrows<T, E extends Throwable> { void accept(T x) throws E; }
    @FunctionalInterface
    interface Consumer<T> { void accept(T x); }
    @FunctionalInterface
    interface TaskThrows<E extends Throwable> { void run() throws E; }
}
