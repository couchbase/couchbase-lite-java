package com.couchbase.lite.storage;

import com.couchbase.lite.util.NativeUtils;

public class JavaSQLiteStorageEngine implements SQLiteStorageEngine {
    private static final String[] CONFLICT_VALUES = new String[]
            {"", " OR ROLLBACK ", " OR ABORT ", " OR FAIL ", " OR IGNORE ", " OR REPLACE "};
    
    private long handle = 0;
    private boolean transactionSuccessful = false;
    
    static {
        NativeUtils.loadLibrary("CouchbaseLiteJavaNative");
    }
    
    private static void throwSQLException(String msg) throws SQLException {
        throw new SQLException(msg);
    }
    
    private synchronized native boolean _open(String path);
    
    @Override
    public synchronized boolean open(String path) {
        boolean opened = _open(path);
        
        return opened;
    }

    @Override
    public synchronized native int getVersion();

    @Override
    public synchronized native void setVersion(int version);

    @Override
    public synchronized boolean isOpen() {
        return (handle != 0);
    }

    @Override
    public synchronized native void beginTransaction();

    private synchronized native void _commit();

    private synchronized native void _rollback();

    @Override
    public synchronized void endTransaction() {
        if (transactionSuccessful) {
            _commit();
        } else {
            _rollback();
        }
        
        transactionSuccessful = false;
    }

    @Override
    public synchronized void setTransactionSuccessful() {
        transactionSuccessful = true;
    }
    
    private synchronized native void _execute(String sql) throws SQLException;

    @Override
    public synchronized void execSQL(String sql) throws SQLException {
        _execute(sql);
    }
    
    private synchronized native void _execute(String sql, Object[] bindArgs) throws SQLException;

    @Override
    public synchronized void execSQL(String sql, Object[] bindArgs) throws SQLException {
        _execute(sql, bindArgs);
    }
    
    private synchronized native StatementCursor _query(String sql, String[] bindArgs);
    
    @Override
    public synchronized StatementCursor rawQuery(String sql, String[] selectionArgs) {
        return _query(sql, selectionArgs);
    }
    
    private synchronized native long _insert(String sql, Object[] bindArgs);

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
        
        return _insert(sql.toString(), bindArgs);
    }

    private synchronized native int _update(String sql, Object[] bindArgs);

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
        
        return _update(sql.toString(), bindArgs);
    }
    
    private synchronized native int _delete(String sql, Object[] bindArgs);

    // COPY: Partially copied from android.database.sqlite.SQLiteDatabase
    @Override
    public synchronized int delete(String table, String whereClause, String[] whereArgs) {
        StringBuilder sql = new StringBuilder(120);
        sql.append("DELETE FROM " + table);
        
        if (whereClause != null && whereClause.length() > 0) {
            sql.append(" WHERE ");
            sql.append(whereClause);
        }
        
        return _update(sql.toString(), whereArgs);
    }

    @Override
    public synchronized native void close();

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
