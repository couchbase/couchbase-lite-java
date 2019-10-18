//
// ChangeNotifier.java
//
// Copyright (c) 2018 Couchbase, Inc All rights reserved.
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

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;

import com.couchbase.lite.internal.utils.Preconditions;


class ChangeNotifier<T> {
    private final Object lock = new Object();
    private final Set<ChangeListenerToken<T>> listenerTokens;

    ChangeNotifier() { listenerTokens = new HashSet<>(); }

    @NonNull
    ChangeListenerToken addChangeListener(
        @Nullable Executor executor,
        @NonNull ChangeListener<T> listener) {
        Preconditions.checkArgNotNull(listener, "listener");

        synchronized (lock) {
            final ChangeListenerToken<T> token = new ChangeListenerToken<>(executor, listener);
            listenerTokens.add(token);
            return token;
        }
    }

    @SuppressWarnings("SuspiciousMethodCalls")
    int removeChangeListener(@NonNull ListenerToken token) {
        Preconditions.checkArgNotNull(token, "token");

        synchronized (lock) {
            listenerTokens.remove(token);
            return listenerTokens.size();
        }
    }

    void postChange(T change) {
        if (change == null) { throw new IllegalArgumentException("change is null"); }

        synchronized (lock) {
            for (ChangeListenerToken<T> token : listenerTokens) { token.postChange(change); }
        }
    }
}
