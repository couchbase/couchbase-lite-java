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
import java.util.ArrayList;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.support.Log;


/**
 * Base ExecutionService that provides the default implementation of serial and concurrent
 * executor.
 */
public abstract class AbstractExecutionService implements ExecutionService {
    private static final LogDomain DOMAIN = LogDomain.DATABASE;
    private static final Object DUMP_LOCK = new Object();
    private static long lastDump;

    public static class InstrumentedTask implements Runnable {
        private final Exception origin = null; // useful but extremely expensive: = new Exception();
        private final Runnable task;
        private final Runnable onComplete;

        private final long createdAt = System.currentTimeMillis();
        private long startedAt;
        private long finishedAt;
        private long completedAt;

        public InstrumentedTask(Runnable task, Runnable onComplete) {
            this.task = task;
            this.onComplete = onComplete;
        }

        public void run() {
            try {
                startedAt = System.currentTimeMillis();
                task.run();
                finishedAt = System.currentTimeMillis();
            }
            finally {
                onComplete.run();
            }
            completedAt = System.currentTimeMillis();
        }

        public String toString() {
            return "task[" + createdAt + "," + startedAt + "," + finishedAt + "," + completedAt + " @" + task + "]";
        }
    }

    // thin wrapper around the AsyncTask's THREAD_POOL_EXECUTOR
    private static class ConcurrentExecutor implements CloseableExecutor {
        private final Executor executor;
        private CountDownLatch stopLatch;
        private int running;

        private ConcurrentExecutor(Executor executor) { this.executor = executor; }

        @Override
        public synchronized void execute(@NonNull Runnable task) {
            if (stopLatch != null) { throw new ExecutorClosedExeception("Executor has been stopped"); }

            try {
                executor.execute(new InstrumentedTask(task, this::finishTask));
                running++;
            }
            catch (RejectedExecutionException e) {
                dumpServiceState(executor, e, "size: " + running);
                throw e;
            }
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
        private final Deque<InstrumentedTask> tasks = new ArrayDeque<>();
        private final Executor executor;

        // If non-null, this executor has been stopped.
        private CountDownLatch stopLatch;

        private InstrumentedTask running;

        private SerialExecutor(Executor executor) { this.executor = executor; }

        @Override
        public synchronized void execute(@NonNull Runnable task) {
            if (stopLatch != null) { throw new ExecutorClosedExeception("Executor has been stopped"); }

            tasks.offerLast(new InstrumentedTask(task, this::scheduleNext));

            if (running == null) { scheduleNext(); }
        }

        @Override
        public boolean stop(long timeout, @NonNull TimeUnit unit) {
            final CountDownLatch latch;
            synchronized (this) {
                if (stopLatch == null) {
                    stopLatch = new CountDownLatch((running == null) ? 0 : tasks.size() + 1);
                }

                latch = stopLatch;
            }

            if (latch.getCount() <= 0) { return true; }

            try { return latch.await(timeout, unit); }
            catch (InterruptedException ignore) { }

            return false;
        }

        private void scheduleNext() {
            synchronized (this) {
                if (stopLatch != null) { stopLatch.countDown(); }

                final InstrumentedTask prev = running;
                running = tasks.poll();
                if (running == null) { return; }

                try { executor.execute(running); }
                catch (RejectedExecutionException e) {
                    dumpExecutorState(e, prev, running, tasks);
                    running = null;
                }
            }
        }

        private void dumpExecutorState(
            RejectedExecutionException ex,
            InstrumentedTask prev,
            InstrumentedTask current,
            Deque<InstrumentedTask> tasks) {
            if (throttled()) { return; }

            dumpServiceState(executor, ex, "size: " + tasks.size());

            Log.d(DOMAIN, "==== Serial Executor status: " + this);

            if (prev != null) { Log.d(DOMAIN, "== Previous task: " + prev, prev.origin); }

            if (current != null) { Log.d(DOMAIN, "== Current task: " + current, current.origin); }

            final ArrayList<InstrumentedTask> waiting = new ArrayList<>(tasks);
            Log.d(DOMAIN, "== Waiting tasks: " + waiting.size());
            int n = 0;
            for (InstrumentedTask t : waiting) { Log.d(DOMAIN, "@" + (++n) + ": " + t, t.origin); }
        }
    }

    private final ThreadPoolExecutor baseExecutor;
    private final ConcurrentExecutor concurrentExecutor;

    public AbstractExecutionService(@NonNull ThreadPoolExecutor baseExecutor) {
        this.baseExecutor = baseExecutor;
        concurrentExecutor = new ConcurrentExecutor(baseExecutor);
    }

    @NonNull
    @Override
    public CloseableExecutor getSerialExecutor() { return new SerialExecutor(baseExecutor); }

    @NonNull
    @Override
    public CloseableExecutor getConcurrentExecutor() { return concurrentExecutor; }

    @VisibleForTesting
    public ThreadPoolExecutor getBaseExecutor() { return baseExecutor; }

    @VisibleForTesting
    void restartConcurrentExecutor() { concurrentExecutor.restart(); }

    static void dumpServiceState(Executor ex, Exception e, String msg) { dumpServiceState(ex, e, null, msg); }

    private static void dumpServiceState(Executor ex, Exception e, Exception origin, String msg) {
        if (throttled()) { return; }

        Log.d(LogDomain.DATABASE, "!!!! Catastrophic failure on executor " + ex + ": " + msg, e);
        if (origin != null) { Log.d(DOMAIN, "!! Origin: ", origin); }

        final Map<Thread, StackTraceElement[]> stackTraces = Thread.getAllStackTraces();
        Log.d(DOMAIN, "==== Threads: " + stackTraces.size());
        for (Map.Entry<Thread, StackTraceElement[]> stack : stackTraces.entrySet()) {
            Log.d(DOMAIN, "== Thread: " + stack.getKey());
            for (StackTraceElement frame : stack.getValue()) { Log.d(DOMAIN, "      at " + frame); }
        }

        if (!(ex instanceof ThreadPoolExecutor)) { return; }

        final ArrayList<Runnable> waiting = new ArrayList<>(((ThreadPoolExecutor) ex).getQueue());
        Log.d(DOMAIN, "==== Executor queue: " + waiting.size());
        int n = 0;
        for (Runnable r : waiting) {
            final Exception orig = (!(r instanceof InstrumentedTask)) ? null : ((InstrumentedTask) r).origin;
            Log.d(DOMAIN, "@" + (n++) + ": " + r, orig);
        }
    }

    private static boolean throttled() {
        final long now = System.currentTimeMillis();
        synchronized (DUMP_LOCK) {
            // don't dump any more than once a second
            if ((now - lastDump) < 1000) { return true; }
            lastDump = now;
        }
        return false;
    }
}

