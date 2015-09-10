package com.couchbase.lite.storage;

import com.couchbase.lite.CouchbaseLiteException;

public class JavaSQLiteStorageEngineFactory implements SQLiteStorageEngineFactory {
    @Override
    public SQLiteStorageEngine createStorageEngine(boolean enableEncryption) throws CouchbaseLiteException {
        return new JavaSQLiteStorageEngine();
    }
}
