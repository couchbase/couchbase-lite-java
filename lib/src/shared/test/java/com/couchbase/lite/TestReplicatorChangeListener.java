package com.couchbase.lite;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;


public class TestReplicatorChangeListener implements ReplicatorChangeListener {
    private static final String[] ACTIVITY_NAMES = {"stopped", "offline", "connecting", "idle", "busy"};

    private final AtomicReference<Throwable> testFailureReason = new AtomicReference<>();
    private final CountDownLatch latch = new CountDownLatch(1);

    private final Replicator replicator;
    private final boolean continuous;
    private final String domain;
    private final int code;
    private final boolean ignoreErrorAtStopped;

    public TestReplicatorChangeListener(Replicator replicator, String domain, int code, boolean ignoreErrorAtStopped) {
        this.replicator = replicator;
        this.continuous = replicator.getConfig().isContinuous();
        this.domain = domain;
        this.code = code;
        this.ignoreErrorAtStopped = ignoreErrorAtStopped;
    }

    public Throwable getFailureReason() { return testFailureReason.get(); }

    public boolean awaitCompletion(long timeout, TimeUnit unit) {
        try { return latch.await(timeout, unit); }
        catch (InterruptedException ignore) { }
        return false;
    }

    @Override
    public void changed(ReplicatorChange change) {
        final Replicator.Status status = change.getStatus();
        try {
            if (continuous) { checkContinuousStatus(status); }
            else { checkOneShotStatus(status); }
        }
        catch (RuntimeException | CouchbaseLiteException | AssertionError e) {
            testFailureReason.compareAndSet(null, e);
        }
    }

    private void checkOneShotStatus(AbstractReplicator.Status status) throws CouchbaseLiteException {
        final CouchbaseLiteException error = status.getError();
        final AbstractReplicator.ActivityLevel state = status.getActivityLevel();

        if (state != Replicator.ActivityLevel.STOPPED) { return; }

        try {
            if (code != 0) { checkError(error); }
            else {
                if ((!ignoreErrorAtStopped) && (error != null)) { throw error; }
            }
        }
        finally {
            latch.countDown();
        }
    }


    private void checkContinuousStatus(AbstractReplicator.Status status) throws CouchbaseLiteException {
        final Replicator.Progress progress = status.getProgress();
        final CouchbaseLiteException error = status.getError();
        final AbstractReplicator.ActivityLevel state = status.getActivityLevel();

        switch (state) {
            case OFFLINE:
                try {
                    if (code != 0) { checkError(error); }
                    else {
                        // TBD
                    }
                }
                finally {
                    latch.countDown();
                }
            case IDLE:
                try {
                    assertEquals(status.getProgress().getTotal(), (status.getProgress().getCompleted()));
                    if (code != 0) { checkError(error); }
                    else {
                        if (error != null) { throw error; }
                    }
                }
                finally {
                    latch.countDown();
                }
            default:
        }
    }

    private void checkError(CouchbaseLiteException error) {
        assertNotNull(error);
        assertEquals(code, error.getCode());
        if (domain != null) { assertEquals(domain, error.getDomain()); }
    }
}

