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

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.couchbase.lite.internal.utils.Preconditions;


/**
 * ExecutionService for Java.
 */
public class JavaExecutionService extends AbstractExecutionService {

    //---------------------------------------------
    // Constants
    //---------------------------------------------
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();

    private static final ThreadFactory THREAD_FACTORY = new ThreadFactory() {
        private final AtomicInteger threadCount = new AtomicInteger(1);

        public Thread newThread(@NonNull Runnable r) { return new Thread(r, "CBL#" + threadCount.getAndIncrement()); }
    };

    private static final ThreadPoolExecutor THREAD_POOL_EXECUTOR = new ThreadPoolExecutor(
        2, CPU_COUNT * 2 + 1, // pool varies between two threads and 2xCPUs
        30, TimeUnit.SECONDS, // unused threads die after 30 sec
        new LinkedBlockingQueue<>(), // unbounded queue
        THREAD_FACTORY);  // nice recognizable names for our threads.

    //---------------------------------------------
    // Types
    //---------------------------------------------
    private static class CancellableTask implements Cancellable {
        private final Future future;

        private CancellableTask(@NonNull Future future) {
            Preconditions.assertNotNull(future, "future");
            this.future = future;
        }

        @Override
        public void cancel() { future.cancel(false); }
    }


    //---------------------------------------------
    // Instance variables
    //---------------------------------------------
    private final Executor mainExecutor;
    private final ScheduledExecutorService scheduler;

    //---------------------------------------------
    // Constructor
    //---------------------------------------------
    public JavaExecutionService() {
        super(THREAD_POOL_EXECUTOR);
        mainExecutor = Executors.newSingleThreadExecutor();
        scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    //---------------------------------------------
    // Public methods
    //---------------------------------------------
    @NonNull
    @Override
    public Executor getMainExecutor() { return mainExecutor; }

    @NonNull
    @Override
    public Cancellable postDelayedOnExecutor(long delayMs, @NonNull Executor executor, @NonNull Runnable task) {
        Preconditions.assertNotNull(executor, "executor");
        Preconditions.assertNotNull(task, "task");
        final Runnable delayedTask = () -> {
            try { executor.execute(task); }
            catch (RejectedExecutionException ignore) { }
        };

        final Future future = scheduler.schedule(delayedTask, delayMs, TimeUnit.MILLISECONDS);
        return new CancellableTask(future);
    }

    @Override
    public void cancelDelayedTask(@NonNull Cancellable cancellableTask) {
        Preconditions.assertNotNull(cancellableTask, "future");
        cancellableTask.cancel();
    }
}
