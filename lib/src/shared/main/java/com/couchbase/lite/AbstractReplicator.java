//
// AbstractReplicator.java
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

package com.couchbase.lite;

import android.support.annotation.GuardedBy;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

import com.couchbase.lite.internal.CBLStatus;
import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.ExecutionService;
import com.couchbase.lite.internal.ExecutionService.Cancellable;
import com.couchbase.lite.internal.SocketFactory;
import com.couchbase.lite.internal.core.C4Constants;
import com.couchbase.lite.internal.core.C4DocumentEnded;
import com.couchbase.lite.internal.core.C4Error;
import com.couchbase.lite.internal.core.C4ReplicationFilter;
import com.couchbase.lite.internal.core.C4Replicator;
import com.couchbase.lite.internal.core.C4ReplicatorListener;
import com.couchbase.lite.internal.core.C4ReplicatorMode;
import com.couchbase.lite.internal.core.C4ReplicatorStatus;
import com.couchbase.lite.internal.core.C4Socket;
import com.couchbase.lite.internal.core.C4WebSocketCloseCode;
import com.couchbase.lite.internal.fleece.FLDict;
import com.couchbase.lite.internal.fleece.FLEncoder;
import com.couchbase.lite.internal.fleece.FLValue;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.Preconditions;
import com.couchbase.lite.internal.utils.StringUtils;
import com.couchbase.lite.utils.Fn;


/**
 * A replicator for replicating document changes between a local database and a target database.
 * The replicator can be bidirectional or either push or pull. The replicator can also be one-short
 * or continuous. The replicator runs asynchronously, so observe the status property to
 * be notified of progress.
 */
@SuppressWarnings({"PMD.GodClass", "PMD.CyclomaticComplexity", "PMD.TooManyMethods", "PMD.TooManyFields"})
public abstract class AbstractReplicator extends NetworkReachabilityListener {
    private static final LogDomain DOMAIN = LogDomain.REPLICATOR;

    private static final int MAX_ONE_SHOT_RETRY_COUNT = 2;
    private static final int MAX_RETRY_DELAY = 10 * 60; // 10min (600 sec)

    private static final List<String> REPLICATOR_ACTIVITY_LEVEL_NAMES
        = Collections.unmodifiableList(Arrays.asList("stopped", "offline", "connecting", "idle", "busy"));

    /**
     * Progress of a replicator. If `total` is zero, the progress is indeterminate; otherwise,
     * dividing the two will produce a fraction that can be used to draw a progress bar.
     */
    public static final class Progress {
        //---------------------------------------------
        // member variables
        //---------------------------------------------

        // The number of completed changes processed.
        private final long completed;

        // The total number of changes to be processed.
        private final long total;

        //---------------------------------------------
        // Constructors
        //---------------------------------------------

        private Progress(long completed, long total) {
            this.completed = completed;
            this.total = total;
        }

        //---------------------------------------------
        // API - public methods
        //---------------------------------------------

        /**
         * The number of completed changes processed.
         */
        public long getCompleted() { return completed; }

        /**
         * The total number of changes to be processed.
         */
        public long getTotal() { return total; }

        @NonNull
        @Override
        public String toString() { return "Progress{" + "completed=" + completed + ", total=" + total + '}'; }

        Progress copy() { return new Progress(completed, total); }
    }

    /**
     * Combined activity level and progress of a replicator.
     */
    public static final class Status {
        //---------------------------------------------
        // member variables
        //---------------------------------------------
        private final ActivityLevel activityLevel;
        private final Progress progress;
        private final CouchbaseLiteException error;

        //---------------------------------------------
        // Constructors
        //---------------------------------------------

        // Note: c4Status.level is current matched with CBLReplicatorActivityLevel:
        public Status(C4ReplicatorStatus c4Status) {
            this(
                AbstractReplicator.ActivityLevel.values()[c4Status.getActivityLevel()],
                new Progress((int) c4Status.getProgressUnitsCompleted(), (int) c4Status.getProgressUnitsTotal()),
                c4Status.getErrorCode() != 0 ? CBLStatus.convertError(c4Status.getC4Error()) : null);
        }

        private Status(ActivityLevel activityLevel, Progress progress, CouchbaseLiteException error) {
            this.activityLevel = activityLevel;
            this.progress = progress;
            this.error = error;
        }

        //---------------------------------------------
        // API - public methods
        //---------------------------------------------

        /**
         * The current activity level.
         */
        @NonNull
        public ActivityLevel getActivityLevel() { return activityLevel; }

        /**
         * The current progress of the replicator.
         */
        @NonNull
        public Replicator.Progress getProgress() { return progress; }

        public CouchbaseLiteException getError() { return error; }

        @NonNull
        @Override
        public String toString() {
            return "Status{" + "activityLevel=" + activityLevel + ", progress=" + progress + ", error=" + error + '}';
        }

        Status copy() { return new Status(activityLevel, progress.copy(), error); }
    }

    // just queue everything up for in-order processing.
    final class ReplicatorListener implements C4ReplicatorListener {
        @Override
        public void statusChanged(C4Replicator repl, C4ReplicatorStatus status, Object context) {
            Log.i(DOMAIN, "C4ReplicatorListener.statusChanged, context: %s, status: %s", context, status);

            if (context == null) {
                Log.w(DOMAIN, "C4ReplicatorListener.statusChanged, context is null!");
                return;
            }

            final AbstractReplicator replicator = (AbstractReplicator) context;
            if (!replicator.isSameReplicator(repl)) { return; }

            dispatcher.execute(() -> replicator.c4StatusChanged(status));
        }

        @Override
        public void documentEnded(C4Replicator repl, boolean pushing, C4DocumentEnded[] documents, Object context) {
            Log.i(DOMAIN, "C4ReplicatorListener.documentEnded, context: %s, pushing: %s", context, pushing);

            if (context == null) {
                Log.w(DOMAIN, "C4ReplicatorListener.documentEnded, context is null!");
                return;
            }

            final AbstractReplicator replicator = (AbstractReplicator) context;
            if (!replicator.isSameReplicator(repl)) { return; }

            dispatcher.execute(() -> replicator.documentEnded(pushing, documents));
        }
    }

    /**
     * Activity level of a replicator.
     */
    public enum ActivityLevel {
        /**
         * The replication is finished or hit a fatal error.
         */
        STOPPED(C4ReplicatorStatus.ActivityLevel.STOPPED),
        /**
         * The replicator is offline because the remote host is unreachable.
         */
        OFFLINE(C4ReplicatorStatus.ActivityLevel.OFFLINE),
        /**
         * The replicator is connecting to the remote host.
         */
        CONNECTING(C4ReplicatorStatus.ActivityLevel.CONNECTING),
        /**
         * The replication is inactive; either waiting for changes or offline
         * as the remote host is unreachable.
         */
        IDLE(C4ReplicatorStatus.ActivityLevel.IDLE),
        /**
         * The replication is actively transferring data.
         */
        BUSY(C4ReplicatorStatus.ActivityLevel.BUSY);

        private final int value;

        ActivityLevel(int value) { this.value = value; }

        int getValue() { return value; }
    }

    /**
     * An enum representing level of opt in on progress of replication
     * OVERALL: No additional replication progress callback
     * PER_DOCUMENT: >=1 Every document replication ended callback
     * PER_ATTACHMENT: >=2 Every blob replication progress callback
     */
    enum ReplicatorProgressLevel {
        OVERALL(0),
        PER_DOCUMENT(1),
        PER_ATTACHMENT(2);

        private final int value;

        ReplicatorProgressLevel(int value) { this.value = value; }

        int getValue() { return value; }
    }

    private static int retryDelay(int retryCount) {
        return Math.min(1 << Math.min(retryCount, 30), MAX_RETRY_DELAY);
    }

    private static boolean isPush(ReplicatorConfiguration.ReplicatorType type) {
        return type == ReplicatorConfiguration.ReplicatorType.PUSH_AND_PULL
            || type == ReplicatorConfiguration.ReplicatorType.PUSH;
    }

    private static boolean isPull(ReplicatorConfiguration.ReplicatorType type) {
        return type == ReplicatorConfiguration.ReplicatorType.PUSH_AND_PULL
            || type == ReplicatorConfiguration.ReplicatorType.PULL;
    }

    private static int mkmode(boolean active, boolean continuous) {
        final C4ReplicatorMode mode = (!active)
            ? C4ReplicatorMode.C4_DISABLED
            : ((continuous) ? C4ReplicatorMode.C4_CONTINUOUS : C4ReplicatorMode.C4_ONE_SHOT);
        return mode.getVal();
    }

    //---------------------------------------------
    // member variables
    //---------------------------------------------

    @NonNull
    final ReplicatorConfiguration config;

    private final Object lock = new Object();

    @GuardedBy("lock")
    private final Set<ReplicatorChangeListenerToken> changeListenerTokens = new HashSet<>();
    @GuardedBy("lock")
    private final Set<DocumentReplicationListenerToken> docEndedListenerTokens = new HashSet<>();

    private final Executor dispatcher = CouchbaseLiteInternal.getExecutionService().getSerialExecutor();

    private final Set<Fn.Consumer> pendingResolutions = new HashSet<>();
    private final Deque<C4ReplicatorStatus> pendingStatusNotifications = new LinkedList<>();
    private final SocketFactory socketFactory;

    @GuardedBy("lock")
    private C4Replicator c4Replicator;

    private Status status = new Status(ActivityLevel.IDLE, new Progress(0, 0), null);
    private C4ReplicatorStatus c4ReplStatus;
    private C4ReplicatorListener c4ReplListener;
    private C4ReplicationFilter c4ReplPushFilter;
    private C4ReplicationFilter c4ReplPullFilter;
    private ReplicatorProgressLevel progressLevel = ReplicatorProgressLevel.OVERALL;
    private CouchbaseLiteException lastError;
    private int retryCount;
    private Cancellable retryTask;
    private String desc;

    private AbstractNetworkReachabilityManager reachabilityManager;

    private Map<String, Object> responseHeaders; // Do something with these (for auth)
    private boolean shouldResetCheckpoint; // Reset the replicator checkpoint.

    /**
     * Initializes a replicator with the given configuration.
     *
     * @param config replicator configuration
     */
    public AbstractReplicator(@NonNull ReplicatorConfiguration config) {
        Preconditions.assertNotNull(config, "config");
        this.config = config.readonlyCopy();
        socketFactory = new SocketFactory(config);
    }

    /**
     * Starts the replicator. This method returns immediately; the replicator runs asynchronously
     * and will report its progress through the replicator change notification.
     */
    public void start() {
        synchronized (lock) {
            Log.i(DOMAIN, "Replicator is starting .....");
            if (c4Replicator != null) {
                Log.i(DOMAIN, "%s has already started", this);
                return;
            }

            if (!pendingResolutions.isEmpty()) {
                Log.i(DOMAIN, "%s is already running", this);
                return;
            }

            Log.i(DOMAIN, "%s: Starting", this);
            resetRetryCount();
            startLocked();
        }
    }

    /**
     * Stops a running replicator. This method returns immediately; when the replicator actually
     * stops, the replicator will change its status's activity level to `kCBLStopped`
     * and the replicator change notification will be notified accordingly.
     */
    public void stop() {
        synchronized (lock) {
            Log.i(DOMAIN, "%s: Replicator is stopping ...", this);

            // this is async; status will change when repl actually stops
            if (c4Replicator != null) { c4Replicator.stop(); }
            else { Log.i(DOMAIN, "%s: Replicator already stopped or offline.", this); }

            if ((c4ReplStatus != null)
                && (c4ReplStatus.getActivityLevel() == C4ReplicatorStatus.ActivityLevel.OFFLINE)) {
                Log.i(DOMAIN, "%s: Replicator is offline.  Stopping.", this);
                dispatcher.execute(
                    () -> c4StatusChanged(new C4ReplicatorStatus(C4ReplicatorStatus.ActivityLevel.STOPPED)));
            }

            if (reachabilityManager != null) { reachabilityManager.removeNetworkReachabilityListener(this); }
        }
    }

    /**
     * The replicator's configuration.
     *
     * @return this replicator's configuration
     */
    @NonNull
    public ReplicatorConfiguration getConfig() { return config.readonlyCopy(); }

    /**
     * The replicator's current status: its activity level and progress. Observable.
     *
     * @return this replicator's status
     */
    @NonNull
    public Status getStatus() { return status.copy(); }

    @NonNull
    public Set<String> getPendingDocumentIds() throws CouchbaseLiteException {
        if (config.getReplicatorType().equals(ReplicatorConfiguration.ReplicatorType.PULL)) {
            throw new CouchbaseLiteException(
                "PullOnlyPendingDocIDs",
                CBLError.Domain.CBLITE,
                CBLError.Code.UNSUPPORTED);
        }

        final C4Replicator repl;
        synchronized (lock) { repl = c4Replicator; }

        // ??? Not yet implemented
        if (repl == null) {
            Log.i(DOMAIN, "%s: Call to getPendingDocumentIds with unstarted replicator", this);
            return Collections.emptySet();
        }

        final Set<String> pending;
        try { pending = repl.getPendingDocIDs(); }
        catch (LiteCoreException e) { throw CBLStatus.convertException(e, "Failed fetching pending documentIds"); }

        // ??? C4 call returns null if the native replicator is gone: Not yet implemented
        return (pending == null) ? Collections.emptySet() : Collections.unmodifiableSet(pending);
    }

    public boolean isDocumentPending(@NonNull String docId) throws CouchbaseLiteException {
        Preconditions.assertNotNull(docId, "document ID");

        if (config.getReplicatorType().equals(ReplicatorConfiguration.ReplicatorType.PULL)) {
            throw new CouchbaseLiteException(
                "PullOnlyPendingDocIDs",
                CBLError.Domain.CBLITE,
                CBLError.Code.UNSUPPORTED);
        }

        final C4Replicator repl;
        synchronized (lock) { repl = c4Replicator; }

        // ??? Not yet implemented
        if (repl == null) {
            Log.i(DOMAIN, "%s: Call to isDocumentPending with unstarted replicator", this);
            return false;
        }

        final Boolean pending;
        try { pending = repl.isDocumentPending(docId); }
        catch (LiteCoreException e) { throw CBLStatus.convertException(e, "Failed getting document pending status"); }

        // ??? C4 call returns null if the native replicator is gone: Not yet implemented
        return (pending != null) && pending;
    }

    /**
     * Adds a change listener for the changes in the replication status and progress. The changes will be
     * delivered on the UI thread for the Android platform and on an arbitrary thread for the Java platform.
     * When developing a Java Desktop application using Swing or JavaFX that needs to update the UI after
     * receiving the changes, make sure to schedule the UI update on the UI thread by using
     * SwingUtilities.invokeLater(Runnable) or Platform.runLater(Runnable) respectively.
     *
     * @param listener callback
     */
    @NonNull
    public ListenerToken addChangeListener(@NonNull ReplicatorChangeListener listener) {
        Preconditions.assertNotNull(listener, "listener");
        return addChangeListener(null, listener);
    }

    /**
     * Adds a change listener for the changes in the replication status and progress with an executor on which
     * the changes will be posted to the listener. If the executor is not specified, the changes will be delivered
     * on the UI thread for the Android platform and on an arbitrary thread for the Java platform.
     *
     * @param executor executor on which events will be delivered
     * @param listener callback
     */
    @NonNull
    public ListenerToken addChangeListener(Executor executor, @NonNull ReplicatorChangeListener listener) {
        Preconditions.assertNotNull(listener, "listener");
        synchronized (lock) {
            final ReplicatorChangeListenerToken token = new ReplicatorChangeListenerToken(executor, listener);
            changeListenerTokens.add(token);
            return token;
        }
    }

    /**
     * Remove the given ReplicatorChangeListener or DocumentReplicationListener from the this replicator.
     *
     * @param token returned by a previous call to addChangeListener or addDocumentListener.
     */
    public void removeChangeListener(@NonNull ListenerToken token) {
        Preconditions.assertNotNull(token, "token");

        synchronized (lock) {
            if (token instanceof ReplicatorChangeListenerToken) {
                changeListenerTokens.remove(token);
                return;
            }

            if (token instanceof DocumentReplicationListenerToken) {
                docEndedListenerTokens.remove(token);
                if (docEndedListenerTokens.isEmpty()) { setProgressLevel(ReplicatorProgressLevel.OVERALL); }
                return;
            }

            throw new IllegalArgumentException("unexpected token: " + token);
        }
    }

    /**
     * Adds a listener for receiving the replication status of the specified document. The status will be
     * delivered on the UI thread for the Android platform and on an arbitrary thread for the Java platform.
     * When developing a Java Desktop application using Swing or JavaFX that needs to update the UI after
     * receiving the status, make sure to schedule the UI update on the UI thread by using
     * SwingUtilities.invokeLater(Runnable) or Platform.runLater(Runnable) respectively.
     *
     * @param listener callback
     * @return A ListenerToken that can be used to remove the handler in the future.
     */
    @NonNull
    public ListenerToken addDocumentReplicationListener(@NonNull DocumentReplicationListener listener) {
        Preconditions.assertNotNull(listener, "listener");

        return addDocumentReplicationListener(null, listener);
    }

    /**
     * Adds a listener for receiving the replication status of the specified document with an executor on which
     * the status will be posted to the listener. If the executor is not specified, the status will be delivered
     * on the UI thread for the Android platform and on an arbitrary thread for the Java platform.
     *
     * @param executor executor on which events will be delivered
     * @param listener callback
     */
    @NonNull
    public ListenerToken addDocumentReplicationListener(
        @Nullable Executor executor,
        @NonNull DocumentReplicationListener listener) {
        Preconditions.assertNotNull(listener, "listener");
        synchronized (lock) {
            setProgressLevel(ReplicatorProgressLevel.PER_DOCUMENT);
            final DocumentReplicationListenerToken token = new DocumentReplicationListenerToken(executor, listener);
            docEndedListenerTokens.add(token);
            return token;
        }
    }

    /**
     * Reset the replicator's local checkpoint: read all changes since the beginning of time
     * from the remote database. This can only be called when the replicator is in a stopped state.
     */
    public void resetCheckpoint() {
        synchronized (lock) {
            if ((c4ReplStatus != null)
                && (c4ReplStatus.getActivityLevel() != C4ReplicatorStatus.ActivityLevel.STOPPED)) {
                throw new IllegalStateException(Log.lookupStandardMessage("ReplicatorNotStopped"));
            }
            shouldResetCheckpoint = true;
        }
    }

    @NonNull
    @Override
    public String toString() {
        if (desc == null) { desc = description(); }
        return desc;
    }

    //---------------------------------------------
    // Abstract methods
    //---------------------------------------------

    protected abstract C4Replicator getC4ReplicatorLocked() throws LiteCoreException;

    //---------------------------------------------
    // Protected methods
    //---------------------------------------------

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        freeC4Replicator(c4Replicator);
        final AbstractNetworkReachabilityManager mgr = reachabilityManager;
        if (mgr != null) { mgr.removeNetworkReachabilityListener(this); }
        super.finalize();
    }

    /**
     * Create and return a c4Replicator targeting the passed URI
     *
     * @param remoteUri a URI for the replication target
     * @return the c4Replicator
     * @throws LiteCoreException on failure to create the replicator
     */
    @GuardedBy("lock")
    @NonNull
    protected final C4Replicator getRemoteC4ReplicatorLocked(@NonNull URI remoteUri) throws LiteCoreException {
        // Set up the port: core uses 0 for not set
        final int p = remoteUri.getPort();
        final int port = (p <= 0) ? 0 : p;

        // get db name and path
        final Deque<String> splitPath = splitPath(remoteUri.getPath());
        final String dbName = (splitPath.size() <= 0) ? "" : splitPath.removeLast();
        final String path = "/" + StringUtils.join("/", splitPath);

        setupFilters();

        final boolean continuous = config.isContinuous();

        c4ReplListener = new ReplicatorListener();

        return config.getDatabase().createRemoteReplicator(
            (Replicator) this,
            remoteUri.getScheme(),
            remoteUri.getHost(),
            port,
            path,
            dbName,
            mkmode(isPush(config.getReplicatorType()), continuous),
            mkmode(isPull(config.getReplicatorType()), continuous),
            getFleeceOptions(),
            c4ReplListener,
            c4ReplPushFilter,
            c4ReplPullFilter,
            socketFactory,
            C4Socket.NO_FRAMING);
    }

    /**
     * Create and return a c4Replicator targeting the passed Database
     *
     * @param otherDb a local database for the replication target
     * @return the c4Replicator
     * @throws LiteCoreException on failure to create the replicator
     */
    @GuardedBy("lock")
    @NonNull
    protected final C4Replicator getLocalC4ReplicatorLocked(@NonNull Database otherDb) throws LiteCoreException {
        setupFilters();

        final boolean continuous = config.isContinuous();

        c4ReplListener = new ReplicatorListener();

        return config.getDatabase().createLocalReplicator(
            (Replicator) this,
            otherDb.getC4Database(),
            mkmode(isPush(config.getReplicatorType()), continuous),
            mkmode(isPull(config.getReplicatorType()), continuous),
            getFleeceOptions(),
            c4ReplListener,
            c4ReplPushFilter,
            c4ReplPullFilter);
    }

    /**
     * Create and return a c4Replicator.
     * The socket factory is responsible for setting up the target
     *
     * @param framing the framing mode (C4Socket.XXX_FRAMING)
     * @return the c4Replicator
     * @throws LiteCoreException on failure to create the replicator
     */
    @GuardedBy("lock")
    @NonNull
    protected final C4Replicator getMessageC4ReplicatorLocked(int framing)
        throws LiteCoreException {
        setupFilters();

        final boolean continuous = config.isContinuous();

        c4ReplListener = new ReplicatorListener();

        return config.getDatabase().createRemoteReplicator(
            (Replicator) this,
            C4Socket.MESSAGE_SCHEME,
            null,
            0,
            null,
            null,
            mkmode(isPush(config.getReplicatorType()), continuous),
            mkmode(isPull(config.getReplicatorType()), continuous),
            getFleeceOptions(),
            c4ReplListener,
            c4ReplPushFilter,
            c4ReplPullFilter,
            socketFactory,
            framing);
    }

    //---------------------------------------------
    // Implementation of NetworkReachabilityListener
    //---------------------------------------------

    @Override
    void networkReachable() {
        Log.i(DOMAIN, "%s: Server may now be reachable; retrying...", this);
        synchronized (lock) {
            if (c4Replicator != null) { return; }
            resetRetryCount();
            scheduleRetry(0);
        }
    }

    @Override
    void networkUnreachable() { Log.v(DOMAIN, "%s: Server may NOT be reachable now.", this); }

    ///// Notification

    @SuppressWarnings("PMD.NPathComplexity")
    void c4StatusChanged(C4ReplicatorStatus c4Status) {
        final ReplicatorChange change;
        final List<ReplicatorChangeListenerToken> tokens;

        Log.i(
            DOMAIN,
            "%s: status changed: (%d, %d) @%s",
            this, pendingResolutions.size(), pendingStatusNotifications.size(), c4Status);

        synchronized (lock) {
            final int c4ActivityLevel = c4Status.getActivityLevel();
            if (!pendingResolutions.isEmpty()
                && ((c4ActivityLevel == C4ReplicatorStatus.ActivityLevel.IDLE)
                || (c4ActivityLevel == C4ReplicatorStatus.ActivityLevel.STOPPED))
                && !actuallyMeansOffline(c4Status.getC4Error())) {
                pendingStatusNotifications.add(c4Status);
            }
            if (!pendingStatusNotifications.isEmpty()) { return; }

            if ((responseHeaders == null) && (c4Replicator != null)) {
                final byte[] h = c4Replicator.getResponseHeaders();
                if (h != null) { responseHeaders = FLValue.fromData(h).asDict(); }
            }

            switch (c4ActivityLevel) {
                case C4ReplicatorStatus.ActivityLevel.IDLE:
                case C4ReplicatorStatus.ActivityLevel.BUSY:
                    resetRetryCount();
                    if (reachabilityManager != null) { reachabilityManager.removeNetworkReachabilityListener(this); }
                    break;

                case C4ReplicatorStatus.ActivityLevel.STOPPED:
                    final C4Error error = c4Status.getC4Error();
                    if (actuallyMeansOffline(error)) {
                        handleError(error);
                        // Change c4Status to offline, so my state will reflect that, and proceed:
                        c4Status = c4Status.copyAtlevel(C4ReplicatorStatus.ActivityLevel.OFFLINE);
                    }
                    break;

                default:
                    // This should be happening:
                    Log.w(DOMAIN, "%s: received unrecognized activity level: ", this, c4Status);
                    break;
            }

            // Update my properties:
            updateStateProperties(c4Status);

            // Post notification
            // Replicator.getStatus() creates a copy of Status.
            change = new ReplicatorChange((Replicator) this, this.getStatus());
            tokens = new ArrayList<>(changeListenerTokens);

            // Note: don't use c4ActivityLevel here.  It might not be up to date.
            if (c4Status.getActivityLevel() == C4ReplicatorStatus.ActivityLevel.STOPPED) {
                cancelScheduledRetry();
                freeC4Replicator();
                config.getDatabase().removeActiveReplicator((Replicator) this); // this is likely to dealloc me
            }
        }

        for (ReplicatorChangeListenerToken token : tokens) { token.notify(change); }
    }

    void documentEnded(boolean pushing, C4DocumentEnded... docEnds) {
        final List<ReplicatedDocument> unconflictedDocs = new ArrayList<>();

        for (C4DocumentEnded docEnd : docEnds) {
            final String docId = docEnd.getDocID();
            final C4Error c4Error = docEnd.getC4Error();

            CouchbaseLiteException error = null;

            if ((c4Error != null) && (c4Error.getCode() != 0)) {
                if (!pushing && docEnd.isConflicted()) {
                    queueConflictResolution(docId, docEnd.getFlags());
                    continue;
                }

                error = CBLStatus.convertError(c4Error);
            }

            unconflictedDocs.add(new ReplicatedDocument(docId, docEnd.getFlags(), error, docEnd.errorIsTransient()));
        }

        if (!unconflictedDocs.isEmpty()) { notifyDocumentEnded(pushing, unconflictedDocs); }
    }

    void queueConflictResolution(@NonNull String docId, int flags) {
        Log.i(DOMAIN, "%s: pulled conflicting version of '%s'", this, docId);

        final ExecutionService.CloseableExecutor executor
            = CouchbaseLiteInternal.getExecutionService().getConcurrentExecutor();
        final Database db = config.getDatabase();
        final ConflictResolver resolver = config.getConflictResolver();
        final Fn.Consumer<CouchbaseLiteException> task = new Fn.Consumer<CouchbaseLiteException>() {
            public void accept(CouchbaseLiteException err) { onConflictResolved(this, docId, flags, err); }
        };

        synchronized (lock) {
            executor.execute(() -> db.resolveReplicationConflict(resolver, docId, task));
            pendingResolutions.add(task);
        }
    }

    // callback from queueConflictResolution
    void onConflictResolved(Fn.Consumer task, String docId, int flags, CouchbaseLiteException err) {
        Log.i(DOMAIN, "Conflict resolved: %s", docId);
        List<C4ReplicatorStatus> pendingNotifications = null;
        synchronized (lock) {
            pendingResolutions.remove(task);
            // if no more resolutions, deliver any outstanding status notifications
            if (pendingResolutions.isEmpty()) {
                pendingNotifications = new ArrayList<>(pendingStatusNotifications);
                pendingStatusNotifications.clear();
            }
        }

        notifyDocumentEnded(false, Arrays.asList(new ReplicatedDocument(docId, flags, err, false)));

        if ((pendingNotifications != null) && (!pendingNotifications.isEmpty())) {
            for (C4ReplicatorStatus status : pendingNotifications) {
                dispatcher.execute(() -> c4StatusChanged(status));
            }
        }
    }

    void notifyDocumentEnded(boolean pushing, List<ReplicatedDocument> docs) {
        final DocumentReplication update = new DocumentReplication((Replicator) this, pushing, docs);
        final List<DocumentReplicationListenerToken> tokens;
        synchronized (lock) { tokens = new ArrayList<>(docEndedListenerTokens); }
        for (DocumentReplicationListenerToken token : tokens) { token.notify(update); }
        Log.i(DOMAIN, "notifyDocumentEnded: %s" + update);
    }

    //---------------------------------------------
    // Private methods
    //---------------------------------------------

    /**
     * Compare the passed C4Replicator to ours.
     *
     * @param repl a C4Replicator
     * @return true if the passed replicator is ours
     */
    private boolean isSameReplicator(C4Replicator repl) {
        synchronized (lock) { return repl == c4Replicator; }
    }

    private byte[] getFleeceOptions() {
        // Encode the options:
        final Map<String, Object> options = config.effectiveOptions();

        // Update shouldResetCheckpoint flag if needed:
        if (shouldResetCheckpoint) {
            options.put(AbstractReplicatorConfiguration.REPLICATOR_RESET_CHECKPOINT, true);
            // Clear the reset flag, it is a one-time thing
            shouldResetCheckpoint = false;
        }

        options.put(AbstractReplicatorConfiguration.REPLICATOR_OPTION_PROGRESS_LEVEL, progressLevel.value);

        byte[] optionsFleece = null;
        if (!options.isEmpty()) {
            final FLEncoder enc = new FLEncoder();
            try {
                enc.write(options);
                optionsFleece = enc.finish();
            }
            catch (LiteCoreException e) { Log.e(DOMAIN, "Failed to encode", e); }
            finally { enc.free(); }
        }

        return optionsFleece;
    }

    private void setupFilters() {
        if (config.getPushFilter() != null) {
            c4ReplPushFilter = (docID, revId, flags, dict, isPush, context)
                -> ((AbstractReplicator) context).validationFunction(docID, revId, documentFlags(flags), dict, isPush);
        }

        if (config.getPullFilter() != null) {
            c4ReplPullFilter = (docID, revId, flags, dict, isPush, context)
                -> ((AbstractReplicator) context).validationFunction(docID, revId, documentFlags(flags), dict, isPush);
        }
    }

    /**
     * Create and start the c4Replicator
     * Must be called holding lock
     */
    private void startLocked() {
        C4Replicator repl = null;
        C4ReplicatorStatus status;

        try {
            repl = getC4ReplicatorLocked();
            c4Replicator = repl;
            repl.start();
            status = repl.getStatus();
        }
        catch (LiteCoreException e) {
            status = new C4ReplicatorStatus(C4ReplicatorStatus.ActivityLevel.STOPPED, e.domain, e.code);
        }

        updateStateProperties((status != null)
            ? status
            : new C4ReplicatorStatus(
                C4ReplicatorStatus.ActivityLevel.STOPPED,
                C4Constants.ErrorDomain.LITE_CORE,
                C4Constants.LiteCoreError.UNEXPECTED_ERROR));

        // Post an initial notification:
        c4ReplListener.statusChanged(repl, c4ReplStatus, this);
    }

    private EnumSet<DocumentFlag> documentFlags(int flags) {
        final EnumSet<DocumentFlag> documentFlags = EnumSet.noneOf(DocumentFlag.class);
        if ((flags & C4Constants.RevisionFlags.DELETED) == C4Constants.RevisionFlags.DELETED) {
            documentFlags.add(DocumentFlag.DocumentFlagsDeleted);
        }
        if ((flags & C4Constants.RevisionFlags.PURGED) == C4Constants.RevisionFlags.PURGED) {
            documentFlags.add(DocumentFlag.DocumentFlagsAccessRemoved);
        }
        return documentFlags;
    }

    private boolean validationFunction(
        String docId,
        String revId,
        EnumSet<DocumentFlag> flags,
        long dict,
        boolean isPush) {
        final ReplicationFilter filter = (isPush) ? config.getPushFilter() : config.getPullFilter();
        return filter.filtered(new Document(config.getDatabase(), docId, revId, new FLDict(dict)), flags);
    }

    private void retry() {
        synchronized (lock) {
            if ((c4Replicator != null)
                || (c4ReplStatus.getActivityLevel() != C4ReplicatorStatus.ActivityLevel.OFFLINE)) {
                return;
            }

            Log.i(DOMAIN, "%s: Retrying...", this);
            startLocked();
        }
    }

    private void scheduleRetry(int delaySec) {
        cancelScheduledRetry();
        final ExecutionService exec = CouchbaseLiteInternal.getExecutionService();
        retryTask = exec.postDelayedOnExecutor(delaySec * 1000, dispatcher, this::retry);
    }

    private void cancelScheduledRetry() {
        if (retryTask != null) {
            Log.v(DOMAIN, "%s Cancel the pending scheduled retry", this);
            final ExecutionService exec = CouchbaseLiteInternal.getExecutionService();
            exec.cancelDelayedTask(retryTask);
            retryTask = null;
        }
    }

    private void resetRetryCount() {
        if (retryCount > 0) {
            retryCount = 0;
            Log.v(DOMAIN, "%s Reset retry count to zero", this);
        }
    }

    // See if this is a transient error, or if I'm continuous and the error might go away with a change
    // in network (i.e. network down, hostname unknown)
    private boolean actuallyMeansOffline(C4Error c4err) {
        final boolean isContinuous = config.isContinuous();
        if (!(isTransient(c4err) || (isContinuous && C4Replicator.mayBeNetworkDependent(c4err)))) {
            return false;
        }
        // this is permanent
        return isContinuous || (retryCount < MAX_ONE_SHOT_RETRY_COUNT);
    }

    // If actuallyMeansOffline == true, go offline and retry later.
    private void handleError(C4Error c4err) {
        freeC4Replicator();

        if (!isTransient(c4err)) {
            Log.i(DOMAIN, "%s: Network error (%s); will retry when network changes...", this, c4err);
        }
        else {
            // On transient error, retry periodically, with exponential backoff:
            final int delaySec = retryDelay(++retryCount);
            Log.i(DOMAIN, "%s: Transient error (%s); will retry in %d sec...", this, c4err, delaySec);
            scheduleRetry(delaySec);
        }

        // Also retry when the network changes:
        startReachabilityObserver();
    }

    private boolean isTransient(C4Error c4err) {
        return C4Replicator.mayBeTransient(c4err) ||
            ((c4err.getDomain() == C4Constants.ErrorDomain.WEB_SOCKET)
                && (c4err.getCode() == C4WebSocketCloseCode.kWebSocketCloseUserTransient));
    }

    private void updateStateProperties(@NonNull C4ReplicatorStatus c4Status) {
        CouchbaseLiteException error = null;
        if (c4Status.getErrorCode() != 0) {
            error = CBLStatus.convertException(
                c4Status.getErrorDomain(),
                c4Status.getErrorCode(),
                c4Status.getErrorInternalInfo());
        }
        if (error != this.lastError) { this.lastError = error; }

        c4ReplStatus = c4Status.copy();

        // Note: c4Status.level is current matched with CBLReplicatorActivityLevel:
        final ActivityLevel level = AbstractReplicator.ActivityLevel.values()[c4Status.getActivityLevel()];

        this.status = new Status(
            level,
            new Progress((int) c4Status.getProgressUnitsCompleted(), (int) c4Status.getProgressUnitsTotal()), error);

        Log.i(DOMAIN, "%s is %s, progress %d/%d, error: %s",
            this,
            REPLICATOR_ACTIVITY_LEVEL_NAMES.get(c4Status.getActivityLevel()),
            c4Status.getProgressUnitsCompleted(),
            c4Status.getProgressUnitsTotal(),
            error);
    }

    // !!! CBL-689
    private void startReachabilityObserver() {
        if (!(config.getTarget() instanceof URLEndpoint)) { return; }

        final URI remoteUri = ((URLEndpoint) config.getTarget()).getURL();

        try {
            final InetAddress host = InetAddress.getByName(remoteUri.getHost());
            if (host.isAnyLocalAddress() || host.isLoopbackAddress()) { return; }
        }
        // an unknown host is surely not local
        catch (UnknownHostException ignore) { }

        if (reachabilityManager == null) { reachabilityManager = new NetworkReachabilityManager(); }

        reachabilityManager.addNetworkReachabilityListener(this);
    }

    // - (void) clearRepl
    private void freeC4Replicator() {
        final C4Replicator repl;
        synchronized (lock) {
            repl = c4Replicator;
            c4Replicator = null;
        }
        freeC4Replicator(repl);
    }

    private void freeC4Replicator(C4Replicator repl) {
        if (repl == null) { return; }
        repl.free();
    }

    // Decompose a path into its elements.
    private Deque<String> splitPath(String fullPath) {
        final Deque<String> path = new ArrayDeque<>();
        for (String element : fullPath.split("/")) {
            if (element.length() > 0) { path.addLast(element); }
        }
        return path;
    }

    private String description() { return baseDesc() + "," + config.getDatabase() + "," + config.getTarget() + "]"; }

    @SuppressWarnings("PMD.UnusedPrivateMethod")
    private String simpleDesc() { return baseDesc() + "}"; }

    private String baseDesc() {
        return "Replicator{@"
            + Integer.toHexString(hashCode()) + ","
            + (isPull(config.getReplicatorType()) ? "<" : "")
            + (config.isContinuous() ? "*" : "-")
            + (isPush(config.getReplicatorType()) ? ">" : "");
    }

    private void setProgressLevel(ReplicatorProgressLevel level) { progressLevel = level; }
}
