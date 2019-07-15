//
// JavaExecutionService.java
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
package com.couchbase.lite.internal;

import android.support.annotation.NonNull;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ExecutionService for Java.
 */
public class JavaExecutionService extends AbstractExecutionService {
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_SIZE = Math.max(2, Math.min(CPU_COUNT - 1, 4));
    private static final int MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1;
    private static final int KEEP_ALIVE_SECONDS = 30;

    private static final ThreadFactory THREAD_FACTORY = new ThreadFactory() {
        private final AtomicInteger mCount = new AtomicInteger(1);
        public Thread newThread(@NonNull Runnable r) { return new Thread(r, "CBL#" + mCount.getAndIncrement()); }
    };

    private static final Executor THREAD_POOL_EXECUTOR;
    static {
        THREAD_POOL_EXECUTOR = new ThreadPoolExecutor(
                CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                new SynchronousQueue<Runnable>(), THREAD_FACTORY);
    }

    private final Executor mainExecutor;

    private final ScheduledExecutorService scheduler;

    public JavaExecutionService() {
        mainExecutor = getSerialExecutor();
        scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    @NonNull
    @Override
    public Executor getThreadPoolExecutor() {
        return THREAD_POOL_EXECUTOR;
    }

    @NonNull
    @Override
    public Executor getMainExecutor() {
        return mainExecutor;
    }

    @Override
    public Object postDelayedOnExecutor(long delayMs, @NonNull Executor executor, @NonNull Runnable task) {
        if (null == task) { throw new IllegalArgumentException("Task may not be null"); }
        if (null == executor) { throw new IllegalArgumentException("Executor may not be null"); }

        Runnable delayedTask = () -> {
            try {
                executor.execute(task);
            }
            catch (RejectedExecutionException ignored) { }
        };
        return scheduler.schedule(delayedTask, delayMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void cancelDelayedTask(@NonNull Object task) {
        if (null == task) { throw new IllegalArgumentException("Task may not be null"); }
        if (!(task instanceof Future)) { throw new IllegalArgumentException("Task is not Future"); }
        ((Future) task).cancel(false);
    }
}
