package com.couchbase.lite;

import com.couchbase.lite.storage.JavaSQLiteStorageEngineFactory;
import com.couchbase.lite.storage.SQLiteStorageEngineFactory;
import com.couchbase.lite.support.Version;

import java.io.File;

/**
 * This is the default couchbase lite context when running "portable java" (eg, non-Android platforms
 * such as Linux or OSX).
 *
 * If you are running on Android, you will want to use AndroidContext instead.  At the time of writing,
 * the AndroidContext is currently not available in the javadocs due to an issue in our build
 * infrastructure.
 */
public class JavaContext implements Context {
    private String subdir;

    public JavaContext(String subdir) {
        this.subdir = subdir;
    }

    public JavaContext() {
        this.subdir = "cblite";
    }

    @Override
    public File getFilesDir() {
        return new File(getRootDirectory(), subdir);
    }

    @Override
    public File getTempDir() {
        return new File(System.getProperty("java.io.tmpdir"));
    }

    @Override
    public void setNetworkReachabilityManager(NetworkReachabilityManager networkReachabilityManager) {

    }

    @Override
    public NetworkReachabilityManager getNetworkReachabilityManager() {
        return new FakeNetworkReachabilityManager();
    }

    @Override
    public SQLiteStorageEngineFactory getSQLiteStorageEngineFactory() {
        return new JavaSQLiteStorageEngineFactory();
    }

    public File getRootDirectory() {
        String rootDirectoryPath = System.getProperty("user.dir");
        return new File(rootDirectoryPath, "data/data/com.couchbase.lite.test/files");
    }

    class FakeNetworkReachabilityManager extends NetworkReachabilityManager {
        @Override
        public void startListening() {

        }

        @Override
        public void stopListening() {

        }
    }

    @Override
    public String getUserAgent() {
        return String.format("CouchbaseLite/%s (Java %s/%s %s/%s)",
                Version.SYNC_PROTOCOL_VERSION,
                System.getProperty("os.name"),
                System.getProperty("os.arch"),
                Version.getVersionName(),
                Version.getCommitHash());
    }
}
