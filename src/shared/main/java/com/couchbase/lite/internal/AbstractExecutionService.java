//
// AbstractExecutionService.java
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
import android.support.annotation.VisibleForTesting;

import java.util.ArrayDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Base ExecutionService that provides the default implementation of serial and concurrent
 * executor.
 */
public abstract class AbstractExecutionService implements ExecutionService {
    private static class ConcurrentExecutor implements CloseableExecutor {
        private final Executor executor;
        private CountDownLatch stopLatch;
        private int running;

        private ConcurrentExecutor(Executor executor) { this.executor = executor; }

        @Override
        public synchronized void execute(@NonNull Runnable task) {
            if (stopLatch != null) { throw new RejectedExecutionException("Executor has been stopped"); }

            running++;

            executor.execute(() -> {
                try { task.run(); }
                finally { finishTask(); }
            });
        }

        @Override
        public boolean stop(long timeout, @NonNull TimeUnit unit) {
            final CountDownLatch latch;
            synchronized (this) {
                if (stopLatch == null) { stopLatch = new CountDownLatch(running); }
                if (running <= 0) { return true; }
                latch = stopLatch;
            }

            try { return latch.await(timeout, unit); }
            catch (InterruptedException ignore) { }

            return false;
        }

        private void finishTask() {
            final CountDownLatch latch;
            synchronized (this) {
                running--;
                latch = stopLatch;
            }

            if (latch != null) { latch.countDown(); }
        }

        @VisibleForTesting
        private void restart() {
            synchronized (this) {
                if (stopLatch == null || running > 0) {
                    throw new IllegalStateException("Executor hasn't been stopped yet.");
                }

                stopLatch = null;
                running = 0;
            }
        }
    }

    // Patterned after AsyncTask's executor
    private static class SerialExecutor implements CloseableExecutor {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        private final Executor executor;
        private CountDownLatch stopLatch;

        private Runnable running;

        private SerialExecutor(Executor executor) { this.executor = executor; }

        public synchronized void execute(@NonNull Runnable task) {
            if (stopLatch != null) { throw new RejectedExecutionException("Executor has been stopped"); }

            tasks.offer(() -> {
                try { task.run(); }
                finally { scheduleNext(); }
            });

            if (running == null) { scheduleNext(); }
        }

        @Override
        public boolean stop(long timeout, @NonNull TimeUnit unit) {
            final CountDownLatch latch;
            final int n;
            synchronized (this) {
                n = (running == null) ? 0 : tasks.size() + 1;

                if (stopLatch == null) { stopLatch = new CountDownLatch(n); }

                if (n <= 0) { return true; }

                latch = stopLatch;
            }

            try { return latch.await(timeout, unit); }
            catch (InterruptedException ignore) { }

            return false;
        }

        private synchronized void scheduleNext() {
            if (stopLatch != null) { stopLatch.countDown(); }

            running = tasks.poll();

            if (running != null) { executor.execute(running); }
        }
    }

    private final CloseableExecutor concurrentExecutor;

    public AbstractExecutionService() {
        concurrentExecutor = new ConcurrentExecutor(getThreadPoolExecutor());
    }

    @NonNull
    public abstract Executor getThreadPoolExecutor();

    @NonNull
    public abstract Executor getMainExecutor();

    @Override
    public abstract Cancellable postDelayedOnExecutor(long delayMs, @NonNull Executor executor, @NonNull Runnable task);

    @Override
    public abstract void cancelDelayedTask(@NonNull Cancellable cancellableTask);

    @NonNull
    @Override
    public CloseableExecutor getSerialExecutor() {
        return new SerialExecutor(getThreadPoolExecutor());
    }

    @NonNull
    @Override
    public CloseableExecutor getConcurrentExecutor() {
        return concurrentExecutor;
    }

    @VisibleForTesting
    void restartConcurrentExecutor() {
        ((ConcurrentExecutor) concurrentExecutor).restart();
    }
}
