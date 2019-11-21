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
package com.couchbase.lite.internal

import com.couchbase.lite.CouchbaseLite
import com.couchbase.lite.LogDomain
import com.couchbase.lite.PlatformBaseTest
import com.couchbase.lite.internal.support.Log
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import java.util.Stack
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit


class ExecutionServicesTest : PlatformBaseTest() {
    private val capacity = AbstractExecutionService.MIN_CAPACITY * 2

    private val queue = ArrayBlockingQueue<Runnable>(capacity)

    private val baseExecutor = ThreadPoolExecutor(3, 3, 5, TimeUnit.SECONDS, queue)

    private val baseService = object : AbstractExecutionService(baseExecutor) {
        override fun postDelayedOnExecutor(
            delayMs: Long,
            executor: Executor,
            task: Runnable
        ): ExecutionService.Cancellable {
            throw UnsupportedOperationException()
        }

        override fun cancelDelayedTask(future: ExecutionService.Cancellable) {
            throw UnsupportedOperationException()
        }

        override fun getMainExecutor(): Executor {
            throw UnsupportedOperationException()
        }
    }

    private lateinit var cblService: ExecutionService

    @Before
    fun setUp() {
        initCouchbaseLite()
        cblService = CouchbaseLite.getExecutionService()
    }

    // The serial executor executes in order.
    @Test
    fun testSerialExecutor() {
        val startLatch = CountDownLatch(1)
        val finishLatch = CountDownLatch(2)

        val stack = Stack<String>()

        val executor = baseService.serialExecutor

        executor.execute {
            try {
                startLatch.await() // wait for the 2nd task to be scheduled.
                Thread.sleep(1000)
            } catch (ignore: InterruptedException) {
            }

            synchronized(stack) { stack.push("ONE") }

            finishLatch.countDown()
        }

        executor.execute {
            synchronized(stack) { stack.push("TWO") }
            finishLatch.countDown()
        }

        // allow the first task to proceed.
        startLatch.countDown()

        try {
            finishLatch.await(5, TimeUnit.SECONDS)
        } catch (ignore: InterruptedException) {
        }

        synchronized(stack) {
            assertEquals("TWO", stack.pop())
            assertEquals("ONE", stack.pop())
        }
    }

    // A stopped serial executor throws on further attempts to schedule
    @Test(expected = RejectedExecutionException::class)
    fun testStoppedSerialExecutorRejects() {
        val executor = baseService.serialExecutor
        assertTrue(executor.stop(0, TimeUnit.SECONDS)) // no tasks
        executor.execute { Log.d(LogDomain.DATABASE, "This test is about to fail!") }
    }

    // A stopped serial executor can finish currently queued tasks.
    @Test
    fun testStoppedSerialExecutorCompletes() {
        val startLatch = CountDownLatch(1)
        val finishLatch = CountDownLatch(2)

        val executor = baseService.serialExecutor

        executor.execute {
            try {
                startLatch.await()
            } catch (ignore: InterruptedException) {
            }

            finishLatch.countDown()
        }

        executor.execute {
            try {
                startLatch.await()
            } catch (ignore: InterruptedException) {
            }

            finishLatch.countDown()
        }

        assertFalse(executor.stop(0, TimeUnit.SECONDS))

        try {
            executor.execute { Log.d(LogDomain.DATABASE, "This test is about to fail!") }
            fail("Stopped executor should not accept new tasks")
        } catch (expected: RejectedExecutionException) {
        }

        // allow the tasks to proceed.
        startLatch.countDown()

        try {
            assertTrue(finishLatch.await(5, TimeUnit.SECONDS))
        } catch (ignore: InterruptedException) {
        }

        assertTrue(executor.stop(5, TimeUnit.SECONDS)) // everything should be done shortly
    }

    // The concurrent executor can execute out of order.
    @Test
    fun testConcurrentExecutor() {
        val startLatch = CountDownLatch(1)
        val finishLatch = CountDownLatch(2)

        val stack = Stack<String>()

        val executor = baseService.concurrentExecutor

        executor.execute {
            try {
                startLatch.await() // wait for the 2nd task to be scheduled.
                Thread.sleep(1000)
            } catch (ignore: InterruptedException) {
            }

            synchronized(stack) { stack.push("ONE") }

            finishLatch.countDown()
        }

        executor.execute {
            synchronized(stack) { stack.push("TWO") }
            finishLatch.countDown()
        }

        // allow the first task to proceed.
        startLatch.countDown()

        try {
            finishLatch.await(5, TimeUnit.SECONDS)
        } catch (ignore: InterruptedException) {
        }

        // tasks should finish in reverse start order
        synchronized(stack) {
            assertEquals("ONE", stack.pop())
            assertEquals("TWO", stack.pop())
        }
    }

    // A concurrent executor fails over before swamping the underlying executor's queue
    @Test
    fun testConcurrentExecutorFailover() {
        val nTasks = capacity * 2

        val startLatch = CountDownLatch(1)
        val finishLatch = CountDownLatch(nTasks)

        val executor = baseService.concurrentExecutor

        // Queue up more tasks than the base executor has room for.
        // This should work...
        for (i in 0 until nTasks) {
            executor.execute {
                try {
                    startLatch.await(1, TimeUnit.SECONDS)
                } catch (ignore: InterruptedException) {
                } finally {
                    finishLatch.countDown()
                }
            }
        }

        // Set all of the tasks free
        startLatch.countDown()

        assertTrue(finishLatch.await(1, TimeUnit.SECONDS))
    }

    // A stopped concurrent executor finishes currently queued tasks.
    @Test
    fun testStoppedConcurrentExecutorCompletes() {
        val startLatch = CountDownLatch(1)
        val finishLatch = CountDownLatch(2)

        val executor = baseService.concurrentExecutor

        // enqueue two tasks
        executor.execute {
            try {
                startLatch.await()
            } catch (ignore: InterruptedException) {
            }

            finishLatch.countDown()
        }

        executor.execute {
            try {
                startLatch.await()
            } catch (ignore: InterruptedException) {
            }

            finishLatch.countDown()
        }

        assertFalse(executor.stop(0, TimeUnit.SECONDS))

        try {
            executor.execute { Log.d(LogDomain.DATABASE, "This test is about to fail!") }
            fail("Stopped executor should not accept new tasks")
        } catch (expected: RejectedExecutionException) {
        }

        // allow the tasks to proceed.
        startLatch.countDown()

        try {
            assertTrue(finishLatch.await(5, TimeUnit.SECONDS))
        } catch (ignore: InterruptedException) {
        }

        assertTrue(executor.stop(5, TimeUnit.SECONDS)) // everything should be done shortly
    }

    // A stopped concurrent executor throws on further attempts to schedule
    @Test(expected = RejectedExecutionException::class)
    fun testStoppedConcurrentExecutorRejects() {
        val executor = baseService.concurrentExecutor
        assertTrue(executor.stop(0, TimeUnit.SECONDS)) // no tasks
        executor.execute { Log.d(LogDomain.DATABASE, "This test is about to fail!") }
    }

    // The main executor always uses the same thread.
    @Test
    fun testMainThreadExecutor() {
        val latch = CountDownLatch(2)

        val threads = arrayOfNulls<Thread>(2)

        cblService.mainExecutor.execute {
            threads[0] = Thread.currentThread()
            latch.countDown()
        }

        cblService.mainExecutor.execute {
            threads[1] = Thread.currentThread()
            latch.countDown()
        }

        try {
            latch.await(2, TimeUnit.SECONDS)
        } catch (ignore: InterruptedException) {
        }

        assertEquals(threads[0], threads[1])
    }

    // The scheduler schedules on the passed queue, with the proper delay.
    @Test
    fun testEnqueueWithDelay() {
        val finishLatch = CountDownLatch(1)

        val threads = arrayOfNulls<Thread>(2)

        val executor = cblService.mainExecutor

        // get the thread used by the executor
        // note that only the mainThreadExecutor guarantees execution on a single thread...
        executor.execute { threads[0] = Thread.currentThread() }

        var t = System.currentTimeMillis()
        val delay: Long = 777
        cblService.postDelayedOnExecutor(
            delay,
            executor,
            Runnable {
                t = System.currentTimeMillis() - t
                threads[1] = Thread.currentThread()
                finishLatch.countDown()
            })

        try {
            assertTrue(finishLatch.await(5, TimeUnit.SECONDS))
        } catch (ignore: InterruptedException) {
        }

        // within 10% is good enough
        assertEquals(0L, (t - delay) / (delay / 10))
        assertEquals(threads[0], threads[1])
    }

    // A delayed task can be cancelled
    @Test
    fun testCancelDelayedTask() {
        val completed = BooleanArray(1)

        // schedule far enough in the future so that there is plenty of time to cancel it
        // but not so far that we have to wait a long tim to be sure it didn't run.
        val task = cblService.postDelayedOnExecutor(
            100,
            baseService.concurrentExecutor,
            Runnable { completed[0] = true })

        cblService.cancelDelayedTask(task)

        try {
            Thread.sleep(200)
        } catch (ignore: InterruptedException) {
        }

        assertFalse(completed[0])
    }

    @Ignore("This is not actually a test.  Use it to verify logcat output")
    @Test(expected = RejectedExecutionException::class)
    fun testSerialExecutorFailure() {
        val executor = baseService.serialExecutor

        // hang the queue
        val latch = CountDownLatch(1)
        executor.execute {
            try {
                latch.await(2, TimeUnit.SECONDS)
            } catch (ignore: InterruptedException) {
            }
        }

        // put some stuff in the serial executor queue
        for (i in 1..9) {
            executor.execute { }
        }

        // fill the base executor.
        try {
            while (true) {
                baseExecutor.execute {
                    try {
                        Thread.sleep(1000) // sleep for a second
                    } catch (ignore: InterruptedException) {
                    }
                }
            }
        } catch (ignore: RejectedExecutionException) {
        }

        // this should free the running serial job,
        // which should fail trying to start the next job
        // which should log a bunch of stuff.
        latch.countDown()
    }
}
