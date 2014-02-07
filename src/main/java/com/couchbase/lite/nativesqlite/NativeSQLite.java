package com.couchbase.lite.nativesqlite;

import com.almworks.sqlite4java.SQLiteConnection;

import java.io.File;

public class NativeSQLite {

    public NativeSQLite() {
        SQLiteConnection db = new SQLiteConnection(new File("test.db"));
    }
}
