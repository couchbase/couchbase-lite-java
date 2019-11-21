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

import android.support.annotation.GuardedBy;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * Base ExecutionService that provides the default implementation of serial and concurrent
 * executor.
 */
public abstract class AbstractExecutionService implements ExecutionService {
    //---------------------------------------------
    // Constants
    //---------------------------------------------
    private static final LogDomain DOMAIN = LogDomain.DATABASE;
    private static final int DUMP_INTERVAL_MS = 2000; // 2 seconds

    @VisibleForTesting
    public static final int MIN_CAPACITY = 64;


    private static final Object DUMP_LOCK = new Object();

    //---------------------------------------------
    // Class members
    //---------------------------------------------
    private static long lastDump;

    //---------------------------------------------
    // Types
    //---------------------------------------------

    @VisibleForTesting
    static class InstrumentedTask implements Runnable {
        private final Runnable task;
        private final Runnable onComplete;

        // Putting a `new Exception()` here is useful but extremely expensive
        final Exception origin = null;

        private final long createdAt = System.currentTimeMillis();
        private long startedAt;
        private long finishedAt;
        private long completedAt;

        InstrumentedTask(Runnable task, Runnable onComplete) {
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

    /**
     * This executor schedules tasks on an underlying thread pool executor
     * (probably some application-wide executor: the Async Task's on Android).
     * If the underlying executor is low on resources, this executor reverts
     * to serial execution, using an unbounded pending queue.
     * If the executor is stopped with unscheduled pending tasks
     * (in the pendingTask queue), all of those tasks are simply discarded.
     * If the pendingTask queue is non-empty, the head task is scheduled.
     * There is probably no need to dump the pendingTask queue, on failure:
     * the problem is, almost certainly, tasks that are already scheduled
     * in the underlying executor.
     */
    private static class ConcurrentExecutor implements CloseableExecutor {
        @NonNull
        private final ThreadPoolExecutor executor;

        @GuardedBy("this")
        @NonNull
        private final Queue<InstrumentedTask> pendingTasks = new LinkedList<>();

        // a non-null stop latch is the flag that this executor has been stopped
        @GuardedBy("this")
        @Nullable
        private CountDownLatch stopLatch;

        @GuardedBy("this")
        private int running;

        ConcurrentExecutor(@NonNull ThreadPoolExecutor executor) {
            Preconditions.checkArgNotNull(executor, "executor");
            this.executor = executor;
        }

        /**
         * Schedule a task for concurrent execution.
         * If there is insufficient room to schedule the task, safely, on the underlying
         * executor, the task is added to the pendingTask queue and executed when spaces is available.
         * There are absolutely no guarantees about execution order, on this executor,
         * particularly once it fails back to using the pending task queue.
         *
         * @param task a task for concurrent execution.
         * @throws ExecutorClosedException    if the executor has been stopped
         * @throws RejectedExecutionException if the underlying executor rejects the task
         */
        @Override
        public void execute(@NonNull Runnable task) {
            Preconditions.checkArgNotNull(task, "task");

            final int n;
            synchronized (this) {
                if (stopLatch != null) { throw new ExecutorClosedException("Executor has been stopped"); }

                final InstrumentedTask newTask;
                if (spaceAvailable()) { newTask = new InstrumentedTask(task, this::finishTask); }
                else {
                    newTask = new InstrumentedTask(task, this::scheduleNext);
                    pendingTasks.add(newTask);
                }

                n = pendingTasks.size();
                if (n <= 1) { executeTask(newTask); }
            }

            if (n > 0) { Log.w(DOMAIN, "Parallel executor overflow: " + n); }
        }

        /**
         * Stop the executor.
         * If there are pending (unscheduled) tasks, they are abandoned.
         * If this call returns false, the executor has *not* yet stopped: tasks it scheduled are still running.
         *
         * @param timeout time to wait for shutdown
         * @param unit    time unit for shutdown wait
         * @return true if all currently scheduled tasks have completed
         */
        @Override
        public boolean stop(long timeout, @NonNull TimeUnit unit) {
            Preconditions.testArg(timeout, "timeout must be >= 0", x -> x >= 0);
            Preconditions.checkArgNotNull(unit, "time unit");

            final CountDownLatch latch;
            synchronized (this) {
                if (stopLatch == null) {
                    pendingTasks.clear();
                    stopLatch = new CountDownLatch(1);
                }
                if (running <= 0) { return true; }
                latch = stopLatch;
            }

            try { return latch.await(timeout, unit); }
            catch (InterruptedException ignore) { }

            return false;
        }

        void finishTask() {
            final CountDownLatch latch;
            synchronized (this) {
                if (--running > 0) { return; }
                latch = stopLatch;
            }

            if (latch != null) { latch.countDown(); }
        }

        // Called on completion of the task at the head of the pending queue.
        void scheduleNext() {
            synchronized (this) {
                // the executor has been stopped
                if (pendingTasks.size() <= 0) { return; }

                do {
                    pendingTasks.remove();
                    final InstrumentedTask task = pendingTasks.peek();
                    if (task == null) { return; }
                    executeTask(task);
                }
                while (spaceAvailable());
            }
        }

        @GuardedBy("this")
        private void executeTask(@NonNull InstrumentedTask newTask) {
            try {
                executor.execute(newTask);
                running++;
            }
            catch (RejectedExecutionException e) {
                dumpServiceState(executor, "size: " + running, e);
                throw e;
            }
        }

        // note that this is only accurate at the moment it is called...
        private boolean spaceAvailable() { return executor.getQueue().remainingCapacity() > MIN_CAPACITY; }
    }

    /**
     * Serial execution, patterned after AsyncTask's executor.
     * Tasks are queued on an unbounded queue and executed one at a time
     * on an underlying executor: the head of the queue is the currently running task.
     * Since this executor can have at most two tasks scheduled on the underlying
     * executor, ensuring space on that executor makes it unlikely that
     * a serial executor will refuse a task for execution.
     */
    private static class SerialExecutor implements CloseableExecutor {
        @NonNull
        private final ThreadPoolExecutor executor;

        @GuardedBy("this")
        @NonNull
        private final Queue<InstrumentedTask> pendingTasks = new LinkedList<>();

        // a non-null stop latch is the flag that this executor has been stopped
        @GuardedBy("this")
        @Nullable
        private CountDownLatch stopLatch;

        @GuardedBy("this")
        private boolean needsRestart;

        SerialExecutor(@NonNull ThreadPoolExecutor executor) {
            Preconditions.checkArgNotNull(executor, "executor");
            this.executor = executor;
        }

        /**
         * Schedule a task for in-order execution.
         *
         * @param task a task to be executed after all currently pending tasks.
         * @throws ExecutorClosedException    if the executor has been stopped
         * @throws RejectedExecutionException if the underlying executor rejects the task
         */
        @Override
        public void execute(@NonNull Runnable task) {
            Preconditions.checkArgNotNull(task, "task");

            synchronized (this) {
                if (stopLatch != null) { throw new ExecutorClosedException("Executor has been stopped"); }

                pendingTasks.add(new InstrumentedTask(task, this::scheduleNext));

                if (needsRestart || (pendingTasks.size() <= 1)) { executeTask(null); }
            }
        }

        /**
         * Stop the executor.
         * If this call returns false, the executor has *not* yet stopped.
         * It will continue to run tasks from its queue until all have completed.
         *
         * @param timeout time to wait for shutdown
         * @param unit    time unit for shutdown wait
         * @return true if all currently scheduled tasks completed before the shutdown
         */
        @Override
        public boolean stop(long timeout, @NonNull TimeUnit unit) {
            Preconditions.testArg(timeout, "timeout must be >= 0", x -> x >= 0);
            Preconditions.checkArgNotNull(unit, "time unit");

            final CountDownLatch latch;
            synchronized (this) {
                if (stopLatch == null) { stopLatch = new CountDownLatch(1); }
                if (pendingTasks.size() <= 0) { return true; }
                latch = stopLatch;
            }

            try { return latch.await(timeout, unit); }
            catch (InterruptedException ignore) { }

            return false;
        }

        // Called on completion of the task at the head of the pending queue.
        private void scheduleNext() {
            final CountDownLatch latch;
            synchronized (this) {
                executeTask(pendingTasks.remove());
                latch = (pendingTasks.size() > 0) ? null : stopLatch;
            }

            if (latch != null) { latch.countDown(); }
        }

        @GuardedBy("this")
        private void executeTask(@Nullable InstrumentedTask prevTask) {
            final InstrumentedTask nextTask = pendingTasks.peek();
            if (nextTask == null) { return; }

            try {
                executor.execute(nextTask);
                needsRestart = false;
            }
            catch (RejectedExecutionException e) {
                needsRestart = true;
                dumpExecutorState(e, prevTask, pendingTasks);
            }
        }

        private void dumpExecutorState(
            @NonNull RejectedExecutionException ex,
            @Nullable InstrumentedTask prev,
            @NonNull Queue<InstrumentedTask> tasks) {
            if (throttled()) { return; }

            dumpServiceState(executor, "size: " + tasks.size(), ex);

            Log.d(DOMAIN, "==== Serial Executor status: " + this);

            if (prev != null) { Log.d(DOMAIN, "== Previous task: " + prev, prev.origin); }

            final InstrumentedTask current = tasks.poll();
            if (current == null) { Log.d(DOMAIN, "== Queue is empty"); }
            else {
                Log.d(DOMAIN, "== Current task: " + current, current.origin);

                final ArrayList<InstrumentedTask> waiting = new ArrayList<>(tasks);
                Log.d(DOMAIN, "== Pending tasks: " + waiting.size());
                int n = 0;
                for (InstrumentedTask t : waiting) { Log.d(DOMAIN, "@" + (++n) + ": " + t, t.origin); }
            }
        }
    }

    //---------------------------------------------
    // Class methods
    //---------------------------------------------
    static void dumpServiceState(@NonNull Executor ex, @NonNull String msg, @Nullable Exception e) {
        if (throttled()) { return; }

        Log.d(LogDomain.DATABASE, "====== Catastrophic failure of executor " + ex + ": " + msg, e);

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

    static boolean throttled() {
        final long now = System.currentTimeMillis();
        synchronized (DUMP_LOCK) {
            if ((now - lastDump) < DUMP_INTERVAL_MS) { return true; }
            lastDump = now;
        }
        return false;
    }


    //---------------------------------------------
    // Instance members
    //---------------------------------------------
    @NonNull
    private final ThreadPoolExecutor baseExecutor;
    @NonNull
    private final ConcurrentExecutor concurrentExecutor;

    //---------------------------------------------
    // Constructor
    //---------------------------------------------
    protected AbstractExecutionService(@NonNull ThreadPoolExecutor baseExecutor) {
        this.baseExecutor = baseExecutor;
        concurrentExecutor = new ConcurrentExecutor(baseExecutor);
    }

    //---------------------------------------------
    // Public methods
    //---------------------------------------------
    @NonNull
    @Override
    public CloseableExecutor getSerialExecutor() { return new SerialExecutor(baseExecutor); }

    @NonNull
    @Override
    public CloseableExecutor getConcurrentExecutor() { return concurrentExecutor; }
}

