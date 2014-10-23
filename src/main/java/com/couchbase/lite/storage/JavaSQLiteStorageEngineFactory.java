package com.couchbase.lite.storage;

public class JavaSQLiteStorageEngineFactory implements SQLiteStorageEngineFactory {
    @Override
    public SQLiteStorageEngine createStorageEngine() {
        return new JavaSQLiteStorageEngine();
    }
}
