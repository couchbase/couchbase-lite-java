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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.couchbase.lite.internal.core.C4Constants;
import com.couchbase.lite.internal.core.C4Database;
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
import com.couchbase.lite.internal.utils.StringUtils;


/**
 * A replicator for replicating document changes between a local database and a target database.
 * The replicator can be bidirectional or either push or pull. The replicator can also be one-short
 * or continuous. The replicator runs asynchronously, so observe the status property to
 * be notified of progress.
 */
public abstract class AbstractReplicator extends NetworkReachabilityListener {
    private static final LogDomain DOMAIN = LogDomain.REPLICATOR;

    private static final int MAX_ONE_SHOT_RETRY_COUNT = 2;
    private static final int MAX_RETRY_DELAY = 10 * 60; // 10min (600 sec)

    private static final List<String> REPLICATOR_ACTIVITY_LEVEL_NAMES
        = Collections.unmodifiableList(Arrays.asList("stopped", "offline", "connecting", "idle", "busy"));

    //---------------------------------------------
    // Load LiteCore library and its dependencies
    //---------------------------------------------
    static {
        NativeLibraryLoader.load();

        // Register CBLWebSocket which is C4Socket implementation
        // CBLWebSocket.register();
    }

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

    final class ReplicatorListener implements C4ReplicatorListener {
        @Override
        public void statusChanged(final C4Replicator repl, final C4ReplicatorStatus status, final Object context) {
            Log.i(DOMAIN, "C4ReplicatorListener.statusChanged, context: " + context + ", status: " + status);
            final AbstractReplicator replicator = (AbstractReplicator) context;
            if (repl == replicator.c4repl) { background.execute(() -> replicator.c4StatusChanged(status)); }
        }

        @Override
        public void documentEnded(C4Replicator repl, boolean pushing, C4DocumentEnded[] documents, Object context) {
            Log.i(DOMAIN, "C4ReplicatorListener.documentEnded, context: " + context + ", pushing: " + pushing);
            final AbstractReplicator replicator = (AbstractReplicator) context;
            if (repl == replicator.c4repl) { background.execute(() -> replicator.documentEnded(pushing, documents)); }
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
        if (!active) { return C4ReplicatorMode.C4_DISABLED; }
        return (continuous) ? C4ReplicatorMode.C4_CONTINUOUS : C4ReplicatorMode.C4_ONE_SHOT;
    }


    //---------------------------------------------
    // member variables
    //---------------------------------------------

    private final Object lock = new Object();

    // Protected by lock
    private final Set<ReplicatorChangeListenerToken> changeListenerTokens = new HashSet<>();
    // Protected by lock
    private final Set<DocumentReplicationListenerToken> docEndedListenerTokens = new HashSet<>();

    private final ScheduledExecutorService background
        = Executors.newSingleThreadScheduledExecutor(target -> new Thread(target, "ReplicatorListenerThread"));

    final ReplicatorConfiguration config;

    private Status status;
    private C4Replicator c4repl;
    private C4ReplicatorStatus c4ReplStatus;
    private C4ReplicatorListener c4ReplListener;
    private C4ReplicationFilter c4ReplPushFilter;
    private C4ReplicationFilter c4ReplPullFilter;
    private ReplicatorProgressLevel progressLevel = ReplicatorProgressLevel.OVERALL;
    private CouchbaseLiteException lastError;
    private int retryCount;
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
        if (config == null) { throw new IllegalArgumentException("config cannot be null."); }
        this.config = config.readonlyCopy();
    }

    /**
     * Starts the replicator. This method returns immediately; the replicator runs asynchronously
     * and will report its progress throuh the replicator change notification.
     */
    public void start() {
        synchronized (lock) {
            Log.i(DOMAIN, "Replicator is starting .....");
            if (c4repl != null) {
                Log.i(DOMAIN, "%s has already started", this);
                return;
            }

            Log.i(DOMAIN, "%s: Starting", this);
            retryCount = 0;
            internalStart();
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
            if (c4repl != null) {
                c4repl.stop(); // this is async; status will change when repl actually stops
            }
            else {
                Log.i(DOMAIN, "%s: Replicator has been stopped or offlined ...", this);
            }

            if (c4ReplStatus.getActivityLevel() == C4ReplicatorStatus.ActivityLevel.OFFLINE) {
                Log.i(DOMAIN, "%s: Replicator is offline.  Stopping.", this);
                final C4ReplicatorStatus c4replStatus = new C4ReplicatorStatus();
                c4replStatus.setActivityLevel(C4ReplicatorStatus.ActivityLevel.STOPPED);
                this.c4StatusChanged(c4replStatus);
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

    /**
     * Set the given ReplicatorChangeListener for this replicator, delivering events on the default executor
     *
     * @param listener callback
     */
    @NonNull
    public ListenerToken addChangeListener(@NonNull ReplicatorChangeListener listener) {
        if (listener == null) { throw new IllegalArgumentException("listener cannot be null."); }
        return addChangeListener(null, listener);
    }

    /**
     * Set the given ReplicatorChangeListener for this replicator, delivering events on the passed executor
     *
     * @param executor executor on which events will be delivered
     * @param listener callback
     */
    @NonNull
    public ListenerToken addChangeListener(Executor executor, @NonNull ReplicatorChangeListener listener) {
        if (listener == null) { throw new IllegalArgumentException("listener cannot be null."); }

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
        if (token == null) { throw new IllegalArgumentException("token cannot be null."); }

        synchronized (lock) {
            if (token instanceof ReplicatorChangeListenerToken) {
                changeListenerTokens.remove(token);
                return;
            }

            if (token instanceof DocumentReplicationListenerToken) {
                docEndedListenerTokens.remove(token);
                if (docEndedListenerTokens.size() == 0) { setProgressLevel(ReplicatorProgressLevel.OVERALL); }
                return;
            }

            throw new IllegalArgumentException("unexpected token: " + token);
        }
    }

    /**
     * Set the given DocumentReplicationListener to the this replicator.
     *
     * @param listener callback
     * @return A ListenerToken that can be used to remove the handler in the future.
     */
    @NonNull
    public ListenerToken addDocumentReplicationListener(@NonNull DocumentReplicationListener listener) {
        if (listener == null) { throw new IllegalArgumentException("listener cannot be null."); }

        return addDocumentReplicationListener(null, listener);
    }

    /**
     * Set the given DocumentReplicationListener to the this replicator.
     *
     * @param executor executor on which events will be delivered
     * @param listener callback
     */
    @NonNull
    public ListenerToken addDocumentReplicationListener(
        @Nullable Executor executor,
        @NonNull DocumentReplicationListener listener) {
        if (listener == null) { throw new IllegalArgumentException("listener cannot be null."); }

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
                throw new IllegalStateException(
                    "Attempt to reset the checkpoint for a replicator that is not stopped.");
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
    // Protected methods
    //---------------------------------------------

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        clearRepl();
        if (reachabilityManager != null) { reachabilityManager.removeNetworkReachabilityListener(this); }
        super.finalize();
    }

    //---------------------------------------------
    // Package protected methods
    //---------------------------------------------

    abstract void initSocketFactory(Object socketFactoryContext);

    abstract int framing();

    abstract String schema();

    //---------------------------------------------
    // Implementation of NetworkReachabilityListener
    //---------------------------------------------

    @Override
    void networkReachable() {
        synchronized (lock) {
            if (c4repl == null) {
                Log.i(DOMAIN, "%s: Server may now be reachable; retrying...", this);
                retryCount = 0;
                retry();
            }
        }
    }

    @Override
    void networkUnreachable() { Log.v(DOMAIN, "%s: Server may NOT be reachable now.", this); }

    //---------------------------------------------
    // Private methods
    //---------------------------------------------

    private void internalStart() {
        // Source
        final C4Database db = config.getDatabase().getC4Database();

        // Target:
        String schema;
        final String host;
        final int port;
        final String path;

        final String dbName;
        final C4Database otherDB;

        final URI remoteUri = config.getTargetURI();
        // replicate against remote endpoint
        if (remoteUri != null) {
            schema = remoteUri.getScheme();
            host = remoteUri.getHost();
            // NOTE: litecore use 0 for not set
            port = remoteUri.getPort() <= 0 ? 0 : remoteUri.getPort();
            path = StringUtils.stringByDeletingLastPathComponent(remoteUri.getPath());
            dbName = StringUtils.lastPathComponent(remoteUri.getPath());
            otherDB = null;
        }
        // replicate against other database
        else {
            schema = null;
            host = null;
            port = 0;
            path = null;
            dbName = null;

            final Database targetDb = config.getTargetDatabase();
            otherDB = (targetDb == null) ? null : targetDb.getC4Database();
        }

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
        if (options.size() > 0) {
            final FLEncoder enc = new FLEncoder();

            enc.write(options);

            try { optionsFleece = enc.finish(); }
            catch (LiteCoreException e) { Log.e(DOMAIN, "Failed to encode", e); }
            finally { enc.free(); }
        }

        // Figure out C4Socket Factory class based on target type:
        // Note: We should call this method something else:
        initSocketFactory(this);

        final int framing = framing();
        final String newSchema = schema();
        if (newSchema != null) { schema = newSchema; }

        // This allow the socket callback to map from the socket factory context
        // and the replicator:
        C4Socket.SOCKET_FACTORY_CONTEXT.put(this, (Replicator) this);

        // Push / Pull / Continuous:
        final boolean push = isPush(config.getReplicatorType());
        final boolean pull = isPull(config.getReplicatorType());
        final boolean continuous = config.isContinuous();

        if (config.getPushFilter() != null) {
            c4ReplPushFilter = (docID, flags, dict, isPush, context)
                -> ((AbstractReplicator) context).validationFunction(docID, documentFlags(flags), dict, isPush);
        }

        if (config.getPullFilter() != null) {
            c4ReplPullFilter = (docID, flags, dict, isPush, context)
                -> ((AbstractReplicator) context).validationFunction(docID, documentFlags(flags), dict, isPush);
        }

        c4ReplListener = new ReplicatorListener();

        // Create a C4Replicator:
        C4ReplicatorStatus status;
        try {
            synchronized (config.getDatabase().getLock()) {
                c4repl = db.createReplicator(schema, host, port, path, dbName, otherDB,
                    mkmode(push, continuous), mkmode(pull, continuous),
                    optionsFleece,
                    c4ReplListener,
                    c4ReplPushFilter,
                    c4ReplPullFilter,
                    this,
                    this,
                    framing);
            }
            status = c4repl.getStatus();
            config.getDatabase().getActiveReplications().add((Replicator) this); // keeps me from being deallocated
        }
        catch (LiteCoreException e) {
            status = new C4ReplicatorStatus(
                C4ReplicatorStatus.ActivityLevel.STOPPED,
                0,
                0,
                0,
                e.domain,
                e.code,
                0);
        }
        updateStateProperties(status);

        // Post an initial notification:
        c4ReplListener.statusChanged(c4repl, c4ReplStatus, this);
    }

    private void documentEnded(boolean pushing, C4DocumentEnded[] docEnds) {
        final List<ReplicatedDocument> docs = new ArrayList<>();
        for (C4DocumentEnded docEnd : docEnds) {
            final String docID = docEnd.getDocID();

            CouchbaseLiteException error = null;
            final C4Error c4Error = docEnd.getC4Error();
            if ((c4Error != null) && (c4Error.getCode() != 0)) {
                error = (!pushing && isConflicted(docEnd))
                    ? handleConflict(docID)
                    : CBLStatus.convertError(c4Error);
            }

            final ReplicatedDocument doc
                = new ReplicatedDocument(docID, docEnd.getFlags(), error, docEnd.errorIsTransient());

            docs.add(doc);
        }

        notifyDocumentEnded(new DocumentReplication((Replicator) this, pushing, docs));
    }

    // Conflict pulling a document -- the revision was added but app needs to resolve it:
    private CouchbaseLiteException handleConflict(String docID) {
        Log.i(DOMAIN, "%s: pulled conflicting version of '%s'", this, docID);
        try { config.getDatabase().resolveConflictInDocument(config.getConflictResolver(), docID); }
        catch (CouchbaseLiteException ex) {
            Log.e(DOMAIN, "Failed to resolve conflict: docID -> %s", ex, docID);
            return ex;
        }
        return null;
    }

    private void notifyDocumentEnded(DocumentReplication update) {
        synchronized (lock) {
            for (DocumentReplicationListenerToken token : docEndedListenerTokens) { token.notify(update); }
        }
        Log.i(DOMAIN, "C4ReplicatorListener.documentEnded() " + update.toString());
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

    private boolean validationFunction(String docID, EnumSet<DocumentFlag> flags, long dict, boolean isPush) {
        final Document document = new Document(config.getDatabase(), docID, new FLDict(dict));
        if (isPush) { return config.getPushFilter().filtered(document, flags); }
        else { return config.getPullFilter().filtered(document, flags); }
    }

    private void retry() {
        synchronized (lock) {
            if (c4repl != null || this.c4ReplStatus
                .getActivityLevel() != C4ReplicatorStatus.ActivityLevel.OFFLINE) { return; }
            Log.i(DOMAIN, "%s: Retrying...", this);
            internalStart();
        }
    }

    private void c4StatusChanged(C4ReplicatorStatus c4Status) {
        synchronized (lock) {
            if (responseHeaders == null && c4repl != null) {
                final byte[] h = c4repl.getResponseHeaders();
                if (h != null) { responseHeaders = FLValue.fromData(h).asDict(); }
            }

            Log.i(DOMAIN, "%s: status changed: " + c4Status, this);
            if (c4Status.getActivityLevel() == C4ReplicatorStatus.ActivityLevel.STOPPED) {
                if (handleError(c4Status.getC4Error())) {
                    // Change c4Status to offline, so my state will reflect that, and proceed:
                    c4Status.setActivityLevel(C4ReplicatorStatus.ActivityLevel.OFFLINE);
                }
            }
            else if (c4Status.getActivityLevel() > C4ReplicatorStatus.ActivityLevel.CONNECTING) {
                retryCount = 0;
                if (reachabilityManager != null) { reachabilityManager.removeNetworkReachabilityListener(this); }
            }

            // Update my properties:
            updateStateProperties(c4Status);

            // Post notification
            synchronized (lock) {
                // Replicator.getStatus() creates a copy of Status.
                final ReplicatorChange change = new ReplicatorChange((Replicator) this, this.getStatus());
                for (ReplicatorChangeListenerToken token : changeListenerTokens) { token.notify(change); }
            }

            // If Stopped:
            if (c4Status.getActivityLevel() == C4ReplicatorStatus.ActivityLevel.STOPPED) {
                // Stopped
                this.clearRepl();
                config.getDatabase().getActiveReplications().remove(this); // this is likely to dealloc me
            }
        }
    }

    // - (bool) handleError: (C4Error)c4err
    private boolean handleError(C4Error c4err) {
        // If this is a transient error, or if I'm continuous and the error might go away with a change
        // in network (i.e. network down, hostname unknown), then go offline and retry later.
        final boolean isTransient = C4Replicator.mayBeTransient(c4err) ||
            ((c4err.getDomain() == C4Constants.ErrorDomain.WEB_SOCKET) &&
                (c4err.getCode() == C4WebSocketCloseCode.kWebSocketCloseUserTransient));

        final boolean isNetworkDependent = C4Replicator.mayBeNetworkDependent(c4err);
        if (!isTransient && !(config.isContinuous() && isNetworkDependent)) {
            return false; // nope, this is permanent
        }
        if (!config.isContinuous() && retryCount >= MAX_ONE_SHOT_RETRY_COUNT) {
            return false; //too many retries
        }

        clearRepl();

        if (!isTransient) {
            Log.i(DOMAIN, "%s: Network error (%s); will retry when network changes...", this, c4err);
        }
        else {
            // On transient error, retry periodically, with exponential backoff:
            final int delay = retryDelay(++retryCount);
            Log.i(DOMAIN, "%s: Transient error (%s); will retry in %d sec...", this, c4err, delay);
            background.schedule(this::retry, delay, TimeUnit.SECONDS);
        }

        // Also retry when the network changes:
        startReachabilityObserver();
        return true;
    }

    private void updateStateProperties(C4ReplicatorStatus c4Status) {
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
            new Progress((int) c4Status.getProgressUnitsCompleted(), (int) c4Status.getProgressUnitsTotal()),
            error);

        Log.i(DOMAIN, "%s is %s, progress %d/%d, error: %s",
            this,
            REPLICATOR_ACTIVITY_LEVEL_NAMES.get(c4Status.getActivityLevel()),
            c4Status.getProgressUnitsCompleted(),
            c4Status.getProgressUnitsTotal(),
            error);
    }

    private void startReachabilityObserver() {
        final URI remoteUri = config.getTargetURI();

        // target is databaes
        if (remoteUri == null) { return; }

        try {
            final InetAddress host = InetAddress.getByName(remoteUri.getHost());
            if (host.isAnyLocalAddress() || host.isLoopbackAddress()) { return; }
        }
        // an unknown host is surely not local
        catch (UnknownHostException ignore) { }

        if (reachabilityManager == null) {
            reachabilityManager = new NetworkReachabilityManager();
        }

        reachabilityManager.addNetworkReachabilityListener(this);
    }

    // - (void) clearRepl
    private void clearRepl() {
        if (c4repl != null) {
            c4repl.free();
            c4repl = null;
        }
    }

    private String description() { return baseDesc() + "," + config.getDatabase() + "," + config.getTarget() + "]"; }

    private String simpleDesc() { return baseDesc() + "}"; }

    private String baseDesc() {
        return "Replicator{@"
            + Integer.toHexString(hashCode()) + ","
            + (isPull(config.getReplicatorType()) ? "<" : "")
            + (config.isContinuous() ? "*" : "-")
            + (isPush(config.getReplicatorType()) ? ">" : "");
    }

    private boolean isConflicted(C4DocumentEnded docEnd) {
        return docEnd.getErrorDomain() == C4Constants.ErrorDomain.LITE_CORE
            && docEnd.getErrorCode() == C4Constants.LiteCoreError.CONFLICT;
    }

    private void setProgressLevel(ReplicatorProgressLevel level) { progressLevel = level; }
}
