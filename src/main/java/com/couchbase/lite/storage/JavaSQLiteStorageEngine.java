package com.couchbase.lite.storage;

import com.couchbase.lite.util.NativeUtils;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

public class JavaSQLiteStorageEngine implements SQLiteStorageEngine {
    private static final String TAG = "StorageEngine";

    private static final String[] CONFLICT_VALUES = new String[]
            {"", " OR ROLLBACK ", " OR ABORT ", " OR FAIL ", " OR IGNORE ", " OR REPLACE "};

    private class Connection {
        private long handle;
        public int usageCount;

        public int transactionCounter;
        public boolean transactionSuccessful;
        public boolean innerTransactionIsSuccessful;

        public Connection() {
            handle = _open(path);
        }
    }

    private final ConnectionPool connectionPool = new ConnectionPool();

    private class ConnectionPool {
        //
        // We are implementing one primary connection for both read and write connection.
        // This behavior is similar to the default operating mode of the Android's SQLiteDatabase.
        // We could enhance the connection pool and the storage engine to support
        // multiple connections for ReadOnly mode.
        //
        // Note that using multiple writable SQLite connection to the SQLite Database will end up
        // with the database file locked error.
        //
        private final Semaphore sem = new Semaphore(1, true);
        private Connection primaryConnection;
        private ThreadLocal<Connection> threadLocal = new ThreadLocal<Connection>();
        private AtomicBoolean isStart = new AtomicBoolean(false);

        public void start() {
            if (!isStart.get()) {
                primaryConnection = new Connection();
                isStart.set(true);
            }
        }

        public void stop() {
            if (isStart.get()) {
                try {
                    _close(primaryConnection.handle);
                } catch (Throwable th){ }
                primaryConnection = null;
                isStart.set(false);
            }
        }

        private Connection acquire() throws InterruptedException {
            // ensure the interrupted flag on the current thread is cleared on.
            Thread.interrupted();

            sem.acquire();

            if (primaryConnection == null)
                sem.release();

            return primaryConnection;
        }

        private void release(Connection conn) {
            sem.release();
        }

        protected Connection getConnection(boolean markUsage) {
            if (!isStart.get()) {
                throw new IllegalStateException("Cannot use the pool without starting the pool.");
            }

            Connection conn = threadLocal.get();
            if (conn == null) {
                try {
                    conn = acquire();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

                threadLocal.set(conn);
            }

            if (markUsage)
                conn.usageCount++;

            return conn;
        }

        public Connection getConnection() {
            return getConnection(true);
        }

        public void releaseConnection(Connection conn) {
            if (!isStart.get()) {
                throw new IllegalStateException("Cannot use the pool without starting the pool.");
            }

            if (conn == null) return;

            conn.usageCount--;

            if (conn.usageCount == 0) {
                threadLocal.set(null);
                release(conn);
            }
        }
    }

    private String path;

    static {
        NativeUtils.loadLibrary("CouchbaseLiteJavaNative");
    }

    // Currently this is used by the native library to throw a SQLException back to Java
    private static void throwSQLException(String msg) throws SQLException {
        throw new SQLException(msg);
    }

    private synchronized native long _open(String path);

    @Override
    public synchronized boolean open(String path) {
        this.path = path;
        connectionPool.start();
        return true;
    }

    private synchronized native int _getVersion(long handle);

    @Override
    public synchronized int getVersion() {
        Connection connection = connectionPool.getConnection();

        try {
            return _getVersion(connection.handle);
        } finally {
            connectionPool.releaseConnection(connection);
        }
    }

    private synchronized native void _setVersion(long handle, int version);

    @Override
    public synchronized void setVersion(int version) {
        Connection connection = connectionPool.getConnection();

        try {
            _setVersion(connection.handle, version);
        } finally {
            connectionPool.releaseConnection(connection);
        }
    }

    @Override
    public synchronized boolean isOpen() {
        Connection connection = connectionPool.getConnection();

        try {
            return (connection.handle != 0);
        } finally {
            connectionPool.releaseConnection(connection);
        }
    }

    public synchronized native void _beginTransaction(long handle);

    @Override
    public void beginTransaction() {
        Connection connection = connectionPool.getConnection();

        ++connection.transactionCounter;

        boolean ok = false;
        try {
            if (connection.transactionCounter > 1) {
                ok = true;
                return;
            }

            connection.transactionSuccessful = true;
            connection.innerTransactionIsSuccessful = false;

            _beginTransaction(connection.handle);

            ok = true;
        } finally {
            if (!ok) {
                --connection.transactionCounter;
            }
        }
    }

    private synchronized native void _commit(long handle);

    private synchronized native void _rollback(long handle);

    @Override
    public synchronized void endTransaction() {
        // Getting a connection without marking a usage count
        Connection connection = connectionPool.getConnection(false);

        try {
            if (connection.innerTransactionIsSuccessful)
                connection.innerTransactionIsSuccessful = false;
            else
                connection.transactionSuccessful = false;

            if (connection.transactionCounter != 1)
                return;

            if (connection.transactionSuccessful)
                _commit(connection.handle);
            else
                _rollback(connection.handle);
        } finally {
            --connection.transactionCounter;
            connectionPool.releaseConnection(connection);
        }
    }

    @Override
    public synchronized void setTransactionSuccessful() {
        Connection connection = connectionPool.getConnection();

        try {
            if (connection.innerTransactionIsSuccessful) {
                throw new IllegalStateException(
                        "setTransactionSuccessful() should only be called once per beginTransaction().");
            }

            connection.innerTransactionIsSuccessful = true;
        } finally {
            connectionPool.releaseConnection(connection);
        }
    }

    private synchronized native void _execute(long handle, String sql) throws SQLException;

    @Override
    public synchronized void execSQL(String sql) throws SQLException {
        Connection connection = connectionPool.getConnection();

        try {
            _execute(connection.handle, sql);
        } finally {
            connectionPool.releaseConnection(connection);
        }
    }

    private synchronized native void _execute(long handle, String sql, Object[] bindArgs) throws SQLException;

    @Override
    public synchronized void execSQL(String sql, Object[] bindArgs) throws SQLException {
        Connection connection = connectionPool.getConnection();
        if (connection == null)
            return;

        try {
            _execute(connection.handle, sql, bindArgs);
        } finally {
            connectionPool.releaseConnection(connection);
        }
    }

    private synchronized native StatementCursor _query(long handle, String sql, String[] bindArgs);

    @Override
    public StatementCursor rawQuery(String sql, String[] selectionArgs) {
        Connection connection = connectionPool.getConnection();

        try {
            return _query(connection.handle, sql, selectionArgs);
        } finally {
            connectionPool.releaseConnection(connection);
        }
    }

    private synchronized native long _insert(long handle, String sql, Object[] bindArgs);

    @Override
    public synchronized long insert(String table, String nullColumnHack, ContentValues values) {
        return insertWithOnConflict(table, nullColumnHack, values, CONFLICT_NONE);
    }

    // COPY: Partially copied from android.database.sqlite.SQLiteDatabase
    @Override
    public synchronized long insertWithOnConflict(String table, String nullColumnHack, ContentValues initialValues, int conflictAlgorithm) {
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT");
        sql.append(CONFLICT_VALUES[conflictAlgorithm]);
        sql.append(" INTO ");
        sql.append(table);
        sql.append('(');

        Object[] bindArgs = null;
        int size = (initialValues != null && initialValues.size() > 0 ? initialValues.size() : 0);

        if (size > 0) {
            bindArgs = new Object[size];
            int i = 0;
            for (String colName : initialValues.keySet()) {
                sql.append((i > 0) ? "," : "");
                sql.append(colName);
                bindArgs[i++] = initialValues.get(colName);
            }
            sql.append(')');
            sql.append(" VALUES (");
            for (i = 0; i < size; i++) {
                sql.append((i > 0) ? ",?" : "?");
            }
        } else {
            sql.append(nullColumnHack + ") VALUES (NULL");
        }
        sql.append(')');

        Connection connection = connectionPool.getConnection();

        try {
            return _insert(connection.handle, sql.toString(), bindArgs);
        } finally {
            connectionPool.releaseConnection(connection);
        }
    }

    private synchronized native int _update(long handle, String sql, Object[] bindArgs);

    @Override
    public synchronized int update(String table, ContentValues values, String whereClause, String[] whereArgs) {
        return _updateWithOnConflict(table, values, whereClause, whereArgs, CONFLICT_NONE);
    }

    // COPY: Partially copied from android.database.sqlite.SQLiteDatabase
    private synchronized int _updateWithOnConflict(String table, ContentValues values, String whereClause, String[] whereArgs, int conflictAlgorithm) {
        if (values == null || values.size() == 0) {
            throw new IllegalArgumentException("Empty values");
        }

        StringBuilder sql = new StringBuilder(120);
        sql.append("UPDATE ");
        sql.append(CONFLICT_VALUES[conflictAlgorithm]);
        sql.append(table);
        sql.append(" SET ");

        // move all bind args to one array
        int setValuesSize = values.size();
        int bindArgsSize = (whereArgs == null) ? setValuesSize : (setValuesSize + whereArgs.length);
        Object[] bindArgs = new Object[bindArgsSize];
        int i = 0;
        for (String colName : values.keySet()) {
            sql.append((i > 0) ? "," : "");
            sql.append(colName);
            bindArgs[i++] = values.get(colName);
            sql.append("=?");
        }
        if (whereArgs != null) {
            for (i = setValuesSize; i < bindArgsSize; i++) {
                bindArgs[i] = whereArgs[i - setValuesSize];
            }
        }
        if (whereClause != null && whereClause.length() > 0) {
            sql.append(" WHERE ");
            sql.append(whereClause);
        }

        Connection connection = connectionPool.getConnection();

        try {
            return _update(connection.handle, sql.toString(), bindArgs);
        } finally {
            connectionPool.releaseConnection(connection);
        }
    }

    private synchronized native int _delete(long handle, String sql, Object[] bindArgs);

    // COPY: Partially copied from android.database.sqlite.SQLiteDatabase
    @Override
    public synchronized int delete(String table, String whereClause, String[] whereArgs) {
        StringBuilder sql = new StringBuilder(120);
        sql.append("DELETE FROM " + table);

        if (whereClause != null && whereClause.length() > 0) {
            sql.append(" WHERE ");
            sql.append(whereClause);
        }

        Connection connection = connectionPool.getConnection();

        try {
            return _update(connection.handle, sql.toString(), whereArgs);
        } finally {
            connectionPool.releaseConnection(connection);
        }
    }

    private synchronized native void _close(long handle);

    @Override
    public synchronized void close() {
        connectionPool.stop();
    }

    private static class StatementCursor implements Cursor {
        private long handle = 0;
        private boolean isAfterLast = false;

        public StatementCursor(long handle) {
            this.handle = handle;
        }

        private native boolean _next(long handle);

        @Override
        public boolean moveToNext() {
            boolean stepped = _next(handle);

            if (!stepped) {
                isAfterLast = true;
            }

            return stepped;
        }

        @Override
        public boolean isAfterLast() {
            return isAfterLast;
        }

        private native String _getString(long handle, int columnIndex);

        @Override
        public String getString(int columnIndex) {
            return _getString(handle, columnIndex);
        }

        private native int _getInt(long handle, int columnIndex);

        @Override
        public int getInt(int columnIndex) {
            return _getInt(handle, columnIndex);
        }

        private native long _getLong(long handle, int columnIndex);

        @Override
        public long getLong(int columnIndex) {
            return _getLong(handle, columnIndex);
        }

        private native byte[] _getBlob(long handle, int columnIndex);

        @Override
        public byte[] getBlob(int columnIndex) {
            return _getBlob(handle, columnIndex);
        }

        private native void _close(long handle);

        @Override
        public void close() {
            _close(handle);
            handle = 0;
        }
    }
}
