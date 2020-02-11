//
// C4Socket.java
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
package com.couchbase.lite.internal.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.SocketFactory;
import com.couchbase.lite.internal.support.Log;


@SuppressWarnings({"LineLength", "PMD.TooManyMethods"})
public abstract class C4Socket {
    //-------------------------------------------------------------------------
    // Constants
    //
    // Most of these are defined in c4Replicator.h and must agree with those definitions.
    //
    //@formatter:off
    //-------------------------------------------------------------------------
    private static final LogDomain LOG_DOMAIN = LogDomain.NETWORK;

    public static final String WEBSOCKET_SCHEME = "ws";
    public static final String WEBSOCKET_SECURE_CONNECTION_SCHEME = "wss";

    // Replicator option dictionary keys:
    public static final String REPLICATOR_OPTION_EXTRA_HEADERS = "headers"; // Extra HTTP headers: string[]
    public static final String REPLICATOR_OPTION_COOKIES = "cookies"; // HTTP Cookie header value: string
    public static final String REPLICATOR_OPTION_AUTHENTICATION = "auth"; // Auth settings: Dict
    public static final String REPLICATOR_OPTION_PINNED_SERVER_CERT = "pinnedCert"; // Cert or public key: [data]
    public static final String REPLICATOR_OPTION_DOC_IDS = "docIDs"; // Docs to replicate: string[]
    public static final String REPLICATOR_OPTION_CHANNELS = "channels"; // SG channel names: string[]
    public static final String REPLICATOR_OPTION_FILTER = "filter"; // Filter name: string
    public static final String REPLICATOR_OPTION_FILTER_PARAMS = "filterParams"; // Filter params: Dict[string]
    public static final String REPLICATOR_OPTION_SKIP_DELETED = "skipDeleted"; // Don't push/pull tombstones: bool
    public static final String REPLICATOR_OPTION_NO_INCOMING_CONFLICTS = "noIncomingConflicts"; // Reject incoming conflicts: bool
    public static final String REPLICATOR_OPTION_OUTGOING_CONFLICTS = "outgoingConflicts"; // Allow creating conflicts on remote: bool
    public static final String REPLICATOR_CHECKPOINT_INTERVAL = "checkpointInterval"; // How often to checkpoint, in seconds: number
    public static final String REPLICATOR_OPTION_REMOTE_DB_UNIQUE_ID = "remoteDBUniqueID"; // Stable ID for remote db with unstable URL: string
    public static final String REPLICATOR_HEARTBEAT_INTERVAL = "heartbeat"; // Interval in secs to send a keep-alive: ping
    public static final String REPLICATOR_RESET_CHECKPOINT = "reset";     // Start over w/o checkpoint: bool
    public static final String REPLICATOR_OPTION_PROGRESS_LEVEL = "progress"; // If >=1, notify on every doc; if >=2, on every attachment (int)
    public static final String REPLICATOR_OPTION_DISABLE_DELTAS = "noDeltas";   ///< Disables delta sync: bool

    // Auth dictionary keys:
    public static final String REPLICATOR_AUTH_TYPE = "type"; // Auth property: string::kProtocolsOption
    public static final String REPLICATOR_AUTH_USER_NAME = "username"; // Auth property: string
    public static final String REPLICATOR_AUTH_PASSWORD = "password"; // Auth property: string
    public static final String REPLICATOR_AUTH_CLIENT_CERT = "clientCert"; // Auth property: value platform-dependent: auth.type values

    // auth.type values:
    public static final String AUTH_TYPE_BASIC = "Basic"; // HTTP Basic (the default)
    public static final String AUTH_TYPE_SESSION = "Session"; // SG session cookie
    public static final String AUTH_TYPE_OPEN_ID_CONNECT = "OpenID Connect";
    public static final String AUTH_TYPE_FACEBOOK = "Facebook";
    public static final String AUTH_TYPE_CLIENT_CERT = "Client Cert";

    // WebSocket protocol options (WebSocketInterface.hh)
    public static final String SOCKET_OPTION_WS_PROTOCOLS = "WS-Protocols"; // litecore::websocket::Provider
    public static final String SOCKET_OPTION_HEARTBEAT = "heartbeat"; // litecore::websocket::Provider

    /**
     * @deprecated No longer used in core
     */
    @Deprecated
    public static final String REPLICATOR_OPTION_NO_CONFLICTS = "noConflicts"; // Puller rejects conflicts: bool

    // C4SocketFraming (C4SocketFactory.framing)
    public static final int WEB_SOCKET_CLIENT_FRAMING = 0; ///< Frame as WebSocket client messages (masked)
    public static final int NO_FRAMING = 1;                ///< No framing; use messages as-is
    public static final int WEB_SOCKET_SERVER_FRAMING = 2; ///< Frame as WebSocket server messages (not masked)
    //@formatter:on

    //-------------------------------------------------------------------------
    // Static Variables
    //-------------------------------------------------------------------------

    // Lookup table: the handle to a native socket object maps to its Java companion
    private static final Map<Long, C4Socket> HANDLES_TO_SOCKETS = Collections.synchronizedMap(new HashMap<>());


    //-------------------------------------------------------------------------
    // JNI callback methods
    //-------------------------------------------------------------------------

    // This method is called by reflection.  Don't change its name.
    @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD")
    @SuppressWarnings("PMD.UnusedPrivateMethod")
    private static void open(
        long handle,
        Object context,
        String scheme,
        String hostname,
        int port,
        String path,
        byte[] options) {
        C4Socket socket = HANDLES_TO_SOCKETS.get(handle);
        Log.d(LOG_DOMAIN, "C4Socket.open @" + handle + ": " + socket + ", " + context);

        if (socket == null) {
            if (!(context instanceof SocketFactory)) {
                throw new IllegalArgumentException("Context is not a socket factory: " + context);
            }
            socket = ((SocketFactory) context).createSocket(handle, scheme, hostname, port, path, options);
        }

        socket.openSocket();
    }

    // This method is called by reflection.  Don't change its name.
    @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD")
    @SuppressWarnings("PMD.UnusedPrivateMethod")
    private static void write(long handle, byte[] allocatedData) {
        if (allocatedData == null) {
            Log.v(LOG_DOMAIN, "C4Socket.callback.write: allocatedData is null");
            return;
        }

        final C4Socket socket = HANDLES_TO_SOCKETS.get(handle);
        Log.d(LOG_DOMAIN, "C4Socket.write @" + handle + ": " + socket);
        if (socket == null) { return; }

        socket.send(allocatedData);
    }

    // This method is called by reflection.  Don't change its name.
    // NOTE: No further action is not required?
    @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD")
    @SuppressWarnings("PMD.UnusedPrivateMethod")
    private static void completedReceive(long handle, long byteCount) {
        final C4Socket socket = HANDLES_TO_SOCKETS.get(handle);
        Log.d(LOG_DOMAIN, "C4Socket.completedReceive @" + handle + ": " + socket);
        if (socket == null) { return; }

        socket.completedReceive(byteCount);
    }

    // This method is called by reflection.  Don't change its name.
    // NOTE: close(long) method should not be called.
    @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD")
    @SuppressWarnings("PMD.UnusedPrivateMethod")
    private static void close(long handle) {
        final C4Socket socket = HANDLES_TO_SOCKETS.get(handle);
        Log.d(LOG_DOMAIN, "C4Socket.close @" + handle + ": " + socket);
        if (socket == null) { return; }

        socket.close();
    }

    // This method is called by reflection.  Don't change its name.
    @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD")
    @SuppressWarnings("PMD.UnusedPrivateMethod")
    private static void requestClose(long handle, int status, String message) {
        final C4Socket socket = HANDLES_TO_SOCKETS.get(handle);
        Log.d(LOG_DOMAIN, "C4Socket.requestClose @" + handle + ": " + socket);
        if (socket == null) { return; }

        socket.requestClose(status, message);
    }

    // This method is called by reflection.  Don't change its name.
    // NOTE: close(long) method should not be called.
    @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD")
    @SuppressWarnings("PMD.UnusedPrivateMethod")
    private static void dispose(long handle) {
        final C4Socket socket = HANDLES_TO_SOCKETS.get(handle);
        Log.d(LOG_DOMAIN, "C4Socket.dispose @" + handle + ": " + socket);
        if (socket == null) { return; }

        socket.release();
    }

    //-------------------------------------------------------------------------
    // Member Variables
    //-------------------------------------------------------------------------

    private long handle; // pointer to C4Socket

    //-------------------------------------------------------------------------
    // constructors
    //-------------------------------------------------------------------------

    protected C4Socket(long handle) { bind(handle); }

    protected C4Socket(String schema, String host, int port, String path, int framing) {
        bind(fromNative(this, schema, host, port, path, framing));
    }

    //-------------------------------------------------------------------------
    // Abstract methods
    //-------------------------------------------------------------------------

    protected abstract void openSocket();

    protected abstract void send(byte[] allocatedData);

    // Apparently not used...
    protected abstract void completedReceive(long byteCount);

    protected abstract void close();

    protected abstract void requestClose(int status, String message);

    //-------------------------------------------------------------------------
    // Protected methods
    //-------------------------------------------------------------------------

    protected boolean released() { return handle == 0L; }

    protected final void opened() {
        Log.d(LOG_DOMAIN, "C4Socket.opened @" + handle);
        if (released()) { return; }
        opened(handle);
    }

    protected final void completedWrite(long byteCount) {
        Log.d(LOG_DOMAIN, "C4Socket.completedWrite @" + handle + ": " + byteCount);
        if (released()) { return; }
        completedWrite(handle, byteCount);
    }

    protected final void received(byte[] data) {
        Log.d(LOG_DOMAIN, "C4Socket.received @" + handle + ": " + data.length);
        if (released()) { return; }
        received(handle, data);
    }

    protected final void closed(int errorDomain, int errorCode, String message) {
        Log.d(LOG_DOMAIN, "C4Socket.closed @" + handle + ": " + errorCode);
        if (released()) { return; }
        closed(handle, errorDomain, errorCode, message);
    }

    protected final void closeRequested(int status, String message) {
        Log.d(LOG_DOMAIN, "C4Socket.closeRequested @" + handle + ": " + status);
        if (released()) { return; }
        closeRequested(handle, status, message);
    }

    protected final void gotHTTPResponse(int httpStatus, byte[] responseHeadersFleece) {
        Log.d(LOG_DOMAIN, "C4Socket.gotHTTPResponse @" + handle + ": " + httpStatus);
        if (released()) { return; }
        gotHTTPResponse(handle, httpStatus, responseHeadersFleece);
    }

    //-------------------------------------------------------------------------
    // package protected methods
    //-------------------------------------------------------------------------

    final long getHandle() { return handle; }

    //-------------------------------------------------------------------------
    // private methods
    //-------------------------------------------------------------------------

    private void bind(long handle) {
        if (handle == 0) { throw new IllegalArgumentException("binding to 0"); }
        HANDLES_TO_SOCKETS.put(handle, this);
        Log.d(LOG_DOMAIN, "C4Socket.bind @" + handle + ": " + HANDLES_TO_SOCKETS.size());
        this.handle = handle;
    }

    private void release() {
        HANDLES_TO_SOCKETS.remove(handle);
        Log.d(LOG_DOMAIN, "C4Socket.release @" + handle + ": " + HANDLES_TO_SOCKETS.size());
        handle = 0L;
    }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    private static native void opened(long handle);

    private static native void completedWrite(long handle, long byteCount);

    private static native void received(long handle, byte[] data);

    private static native void closed(long handle, int errorDomain, int errorCode, String message);

    private static native void closeRequested(long handle, int status, String message);

    private static native void gotHTTPResponse(long handle, int httpStatus, byte[] responseHeadersFleece);

    private static native long fromNative(
        Object nativeHandle,
        String schema,
        String host,
        int port,
        String path,
        int framing);
}
