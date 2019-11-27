package com.couchbase.lite;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.couchbase.lite.internal.core.CBLVersion;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.utils.TestUtils;

import static com.couchbase.lite.utils.TestUtils.assertThrows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;


public class LogTest extends BaseTest {
    static class BasicLogger implements Logger {
        private String message;

        @NonNull
        @Override
        public LogLevel getLevel() { return LogLevel.DEBUG; }

        @Override
        public void log(@NonNull LogLevel level, @NonNull LogDomain domain, @NonNull String msg) { message = msg; }

        public String getMessage() { return message; }
    }

    static class LogTestLogger implements Logger {
        private final Map<LogLevel, Integer> lineCounts = new HashMap<>();
        private final StringBuilder content = new StringBuilder();
        private LogLevel level;

        @Override
        public void log(@NotNull LogLevel level, @NotNull LogDomain domain, @NotNull String message) {
            if (level.compareTo(this.level) < 0) { return; }
            lineCounts.put(level, getLineCount(level) + 1);
            content.append(message);
        }

        @NotNull
        @Override
        public LogLevel getLevel() { return level; }

        void setLevel(LogLevel level) { this.level = level; }

        int getLineCount() {
            int total = 0;
            for (LogLevel level : LogLevel.values()) { total += getLineCount(level); }
            return total;
        }

        int getLineCount(LogLevel level) {
            Integer levelCount = lineCounts.get(level);
            return (levelCount == null) ? 0 : levelCount;
        }

        String getContent() { return content.toString(); }
    }


    private String tempDirPath;

    @Before
    public void setUp() throws CouchbaseLiteException {
        super.setUp();

        tempDirPath = getTempDirectory("logtest-" + System.currentTimeMillis());

        Database.log.reset();
    }

    @After
    public void tearDown() {
        super.tearDown();
        recursiveDelete(getTempDir());
    }

    @Test
    public void testCustomLoggingLevels() {
        LogTestLogger customLogger = new LogTestLogger();
        Database.log.setCustom(customLogger);

        for (LogLevel level : LogLevel.values()) {
            customLogger.setLevel(level);
            Log.d(LogDomain.DATABASE, "TEST DEBUG");
            Log.v(LogDomain.DATABASE, "TEST VERBOSE");
            Log.i(LogDomain.DATABASE, "TEST INFO");
            Log.w(LogDomain.DATABASE, "TEST WARNING");
            Log.e(LogDomain.DATABASE, "TEST ERROR");
        }

        assertEquals(2, customLogger.getLineCount(LogLevel.VERBOSE));
        assertEquals(3, customLogger.getLineCount(LogLevel.INFO));
        assertEquals(4, customLogger.getLineCount(LogLevel.WARNING));
        assertEquals(5, customLogger.getLineCount(LogLevel.ERROR));
    }

    @Test
    public void testEnableAndDisableCustomLogging() {
        LogTestLogger customLogger = new LogTestLogger();
        Database.log.setCustom(customLogger);

        customLogger.setLevel(LogLevel.NONE);
        Log.d(LogDomain.DATABASE, "TEST DEBUG");
        Log.v(LogDomain.DATABASE, "TEST VERBOSE");
        Log.i(LogDomain.DATABASE, "TEST INFO");
        Log.w(LogDomain.DATABASE, "TEST WARNING");
        Log.e(LogDomain.DATABASE, "TEST ERROR");
        assertEquals(0, customLogger.getLineCount());

        customLogger.setLevel(LogLevel.VERBOSE);
        Log.d(LogDomain.DATABASE, "TEST DEBUG");
        Log.v(LogDomain.DATABASE, "TEST VERBOSE");
        Log.i(LogDomain.DATABASE, "TEST INFO");
        Log.w(LogDomain.DATABASE, "TEST WARNING");
        Log.e(LogDomain.DATABASE, "TEST ERROR");
        assertEquals(4, customLogger.getLineCount());
    }

    @Test
    public void testFileLoggingLevels() throws Exception {
        LogFileConfiguration config = new LogFileConfiguration(tempDirPath)
            .setUsePlaintext(true)
            .setMaxRotateCount(0);

        testWithConfiguration(
            LogLevel.DEBUG,
            config,
            () -> {
                for (LogLevel level : LogLevel.values()) {
                    Database.log.getFile().setLevel(level);
                    Log.d(LogDomain.DATABASE, "TEST DEBUG");
                    Log.v(LogDomain.DATABASE, "TEST VERBOSE");
                    Log.i(LogDomain.DATABASE, "TEST INFO");
                    Log.w(LogDomain.DATABASE, "TEST WARNING");
                    Log.e(LogDomain.DATABASE, "TEST ERROR");
                }

                for (File log : getLogFiles()) {
                    BufferedReader fin = new BufferedReader(new FileReader(log));
                    int lineCount = 0;
                    while ((fin.readLine()) != null) { lineCount++; }

                    String logPath = log.getAbsolutePath();
                    // One meta line per log, so the actual logging lines is X + 1
                    if (logPath.contains("verbose")) { assertEquals(3, lineCount); }
                    else if (logPath.contains("info")) { assertEquals(4, lineCount); }
                    else if (logPath.contains("warning")) { assertEquals(5, lineCount); }
                    else if (logPath.contains("error")) { assertEquals(6, lineCount); }
                }
            });
    }

    @Test
    public void testFileLoggingDefaultBinaryFormat() throws Exception {
        LogFileConfiguration config = new LogFileConfiguration(tempDirPath);

        testWithConfiguration(
            LogLevel.INFO,
            config,
            () -> {
                Log.i(LogDomain.DATABASE, "TEST INFO");

                File[] files = getLogFiles();
                assertTrue(files.length > 0);

                File lastModifiedFile = getMostRecent(files);

                byte[] bytes = new byte[4];
                InputStream is = new FileInputStream(lastModifiedFile);
                assertEquals(4, is.read(bytes));

                assertEquals(bytes[0], (byte) 0xCF);
                assertEquals(bytes[1], (byte) 0xB2);
                assertEquals(bytes[2], (byte) 0xAB);
                assertEquals(bytes[3], (byte) 0x1B);
            });
    }

    @Test
    public void testFileLoggingUsePlainText() throws Exception {
        LogFileConfiguration config = new LogFileConfiguration(tempDirPath).setUsePlaintext(true);

        testWithConfiguration(
            LogLevel.INFO,
            config,
            () -> {
                String uuidString = UUID.randomUUID().toString();
                Log.i(LogDomain.DATABASE, uuidString);

                File[] files = getTempDir().listFiles((dir, name) -> name.toLowerCase().startsWith("cbl_info_"));
                assertNotNull(files);
                assertEquals(1, files.length);

                assertTrue(getLogContents(getMostRecent(files)).contains(uuidString));
            });
    }

    @Test
    public void testFileLoggingLogFilename() throws Exception {
        LogFileConfiguration config = new LogFileConfiguration(tempDirPath);

        testWithConfiguration(
            LogLevel.DEBUG,
            config,
            () -> {
                Log.e(LogDomain.DATABASE, "TEST MESSAGE");

                File[] files = getLogFiles();
                assertTrue(files.length >= 4);

                String filenameRegex = "cbl_(debug|verbose|info|warning|error)_\\d+\\.cbllog";
                for (File file : files) { assertTrue(file.getName().matches(filenameRegex)); }
            });
    }

    @Test
    public void testFileLoggingMaxSize() throws Exception {
        final LogFileConfiguration config = new LogFileConfiguration(tempDirPath)
            .setUsePlaintext(true)
            .setMaxSize(1024);

        testWithConfiguration(
            LogLevel.DEBUG,
            config,
            () -> {
                // this should create two files, as the 1KB logs + extra header
                writeOneKiloByteOfLog();
                assertEquals((config.getMaxRotateCount() + 1) * 5, getLogFiles().length);
            });
    }

    @Test
    public void testFileLoggingDisableLogging() throws Exception {
        String uuidString = UUID.randomUUID().toString();

        final LogFileConfiguration config = new LogFileConfiguration(tempDirPath).setUsePlaintext(true);

        testWithConfiguration(
            LogLevel.NONE,
            config,
            () -> {
                writeAllLogs(uuidString);
                for (File log : getLogFiles()) { assertFalse(getLogContents(log).contains(uuidString)); }
            });
    }

    @Test
    public void testFileLoggingReEnableLogging() throws Exception {
        String uuidString = UUID.randomUUID().toString();

        LogFileConfiguration config = new LogFileConfiguration(tempDirPath).setUsePlaintext(true);

        testWithConfiguration(
            LogLevel.NONE,
            config,
            () -> {
                writeAllLogs(uuidString);

                for (File log : getLogFiles()) { assertFalse(getLogContents(log).contains(uuidString)); }

                Database.log.getFile().setLevel(LogLevel.VERBOSE);
                writeAllLogs(uuidString);

                File[] filesExceptDebug
                    = getTempDir().listFiles((ign, name) -> !name.toLowerCase().startsWith("cbl_debug_"));
                assertNotNull(filesExceptDebug);

                for (File log : filesExceptDebug) { assertTrue(getLogContents(log).contains(uuidString)); }
            });
    }

    @Test
    public void testFileLoggingHeader() throws Exception {
        LogFileConfiguration config = new LogFileConfiguration(tempDirPath).setUsePlaintext(true);

        testWithConfiguration(
            LogLevel.VERBOSE,
            config,
            () -> {
                writeOneKiloByteOfLog();
                for (File log : getLogFiles()) {
                    BufferedReader fin = new BufferedReader(new FileReader(log));
                    String firstLine = fin.readLine();

                    assertNotNull(firstLine);
                    assertTrue(firstLine.contains("CouchbaseLite " + PlatformBaseTest.PRODUCT));
                    assertTrue(firstLine.contains("Core/"));
                    assertTrue(firstLine.contains(CBLVersion.getSysInfo()));
                }
            });
    }

    @Test
    public void testBasicLogFormatting() {
        BasicLogger logger = new BasicLogger();
        Database.log.setCustom(logger);

        Log.d(LogDomain.DATABASE, "TEST DEBUG");
        assertEquals("TEST DEBUG", logger.getMessage());

        Log.d(LogDomain.DATABASE, "TEST DEBUG", new Exception("whoops"));
        assertEquals("TEST DEBUG (java.lang.Exception: whoops)", logger.getMessage());

        // test formatting, including argument ordering
        Log.d(LogDomain.DATABASE, "TEST DEBUG %2$s %1$d %3$.2f", 1, "arg", 3.0F);
        assertEquals("TEST DEBUG arg 1 3.00", logger.getMessage());

        Log.d(LogDomain.DATABASE, "TEST DEBUG %2$s %1$d %3$.2f", new Exception("whoops"), 1, "arg", 3.0F);
        assertEquals("TEST DEBUG arg 1 3.00 (java.lang.Exception: whoops)", logger.getMessage());
    }

    @Test
    public void testWriteLogWithError() throws Exception {
        String message = "test message";
        String uuid = UUID.randomUUID().toString();
        CouchbaseLiteException error = new CouchbaseLiteException(uuid);

        LogFileConfiguration config = new LogFileConfiguration(tempDirPath).setUsePlaintext(true);

        testWithConfiguration(
            LogLevel.DEBUG,
            config,
            () -> {
                Log.d(LogDomain.DATABASE, message, error);
                Log.v(LogDomain.DATABASE, message, error);
                Log.i(LogDomain.DATABASE, message, error);
                Log.w(LogDomain.DATABASE, message, error);
                Log.e(LogDomain.DATABASE, message, error);

                for (File log : getLogFiles()) { assertTrue(getLogContents(log).contains(uuid)); }
            });
    }

    @Test
    public void testWriteLogWithErrorAndArgs() throws Exception {
        String uuid1 = UUID.randomUUID().toString();
        String uuid2 = UUID.randomUUID().toString();
        String message = "test message %s";
        CouchbaseLiteException error = new CouchbaseLiteException(uuid1);

        LogFileConfiguration config = new LogFileConfiguration(tempDirPath).setUsePlaintext(true);

        testWithConfiguration(
            LogLevel.DEBUG,
            config,
            () -> {
                Log.d(LogDomain.DATABASE, message, error, uuid2);
                Log.v(LogDomain.DATABASE, message, error, uuid2);
                Log.i(LogDomain.DATABASE, message, error, uuid2);
                Log.w(LogDomain.DATABASE, message, error, uuid2);
                Log.e(LogDomain.DATABASE, message, error, uuid2);

                for (File log : getLogFiles()) {
                    String content = getLogContents(log);
                    assertTrue(content.contains(uuid1));
                    assertTrue(content.contains(uuid2));
                }
            });
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void testLogFileConfigurationConstructors() {
        int rotateCount = 4;
        long maxSize = 2048;
        boolean usePlainText = true;

        assertThrows(IllegalArgumentException.class, () -> new LogFileConfiguration((String) null));

        assertThrows(IllegalArgumentException.class, () -> new LogFileConfiguration((LogFileConfiguration) null));

        LogFileConfiguration config = new LogFileConfiguration(tempDirPath)
            .setMaxRotateCount(rotateCount)
            .setMaxSize(maxSize)
            .setUsePlaintext(usePlainText);
        assertEquals(config.getMaxRotateCount(), rotateCount);
        assertEquals(config.getMaxSize(), maxSize);
        assertEquals(config.usesPlaintext(), usePlainText);
        assertEquals(config.getDirectory(), tempDirPath);

        final String tempDir2 = getTempDirectory("logtest2");
        LogFileConfiguration newConfig = new LogFileConfiguration(tempDir2, config);
        assertEquals(newConfig.getMaxRotateCount(), rotateCount);
        assertEquals(newConfig.getMaxSize(), maxSize);
        assertEquals(newConfig.usesPlaintext(), usePlainText);
        assertEquals(newConfig.getDirectory(), tempDir2);
    }

    @Test
    public void testEditReadOnlyLogFileConfiguration() throws Exception {
        LogFileConfiguration config = new LogFileConfiguration(tempDirPath);

        testWithConfiguration(
            LogLevel.DEBUG,
            config,
            () -> {
                assertThrows(IllegalStateException.class, () -> Database.log.getFile().getConfig().setMaxSize(1024));

                assertThrows(
                    IllegalStateException.class,
                    () -> Database.log.getFile().getConfig().setMaxRotateCount(3));

                assertThrows(
                    IllegalStateException.class,
                    () -> Database.log.getFile().getConfig().setUsePlaintext(true));
            });
    }

    @Test
    public void testSetNewLogFileConfiguration() {
        LogFileConfiguration config = new LogFileConfiguration(tempDirPath);

        final FileLogger fileLogger = Database.log.getFile();
        fileLogger.setConfig(config);
        assertEquals(fileLogger.getConfig(), config);
        fileLogger.setConfig(config);
        assertEquals(fileLogger.getConfig(), config);
        fileLogger.setConfig(null);
        assertNull(fileLogger.getConfig());
        fileLogger.setConfig(null);
        assertNull(fileLogger.getConfig());
        fileLogger.setConfig(config);
        assertEquals(fileLogger.getConfig(), config);
        fileLogger.setConfig(new LogFileConfiguration(tempDirPath + "/foo"));
        assertEquals(fileLogger.getConfig(), new LogFileConfiguration(tempDirPath + "/foo"));
    }

    @Test
    public void testNonASCII() throws CouchbaseLiteException {
        String hebrew = "מזג האוויר נחמד היום"; // The weather is nice today.

        LogTestLogger customLogger = new LogTestLogger();
        customLogger.setLevel(LogLevel.VERBOSE);
        Database.log.setCustom(customLogger);

        MutableDocument doc = new MutableDocument();
        doc.setString("hebrew", hebrew);
        save(doc);

        Query query = QueryBuilder.select(SelectResult.all()).from(DataSource.database(db));
        ResultSet rs = query.execute();
        assertEquals(rs.allResults().size(), 1);

        assertTrue(customLogger.getContent().contains("[{\"hebrew\":\"" + hebrew + "\"}]"));
    }

    @Test
    public void testLogStandardErrorWithFormatting() {
        Map<String, String> stdErr = new HashMap<>();
        stdErr.put("FOO", "TEST DEBUG %2$s %1$d %3$.2f");
        try {
            Log.initLogging(stdErr);

            BasicLogger logger = new BasicLogger();
            Database.log.setCustom(logger);

            Log.d(LogDomain.DATABASE, "FOO", new Exception("whoops"), 1, "arg", 3.0F);
            assertEquals("TEST DEBUG arg 1 3.00 (java.lang.Exception: whoops)", logger.getMessage());
        }
        finally {
            reloadStandardErrorMessages();
        }
    }

    @Test
    public void testLookupStandardMessage() {
        Map<String, String> stdErr = new HashMap<>();
        stdErr.put("FOO", "TEST DEBUG");
        try {
            Log.initLogging(stdErr);
            assertEquals("TEST DEBUG", Log.lookupStandardMessage("FOO"));
        }
        finally {
            reloadStandardErrorMessages();
        }
    }

    @Test
    public void testFormatStandardMessage() {
        Map<String, String> stdErr = new HashMap<>();
        stdErr.put("FOO", "TEST DEBUG %2$s %1$d %3$.2f");
        try {
            Log.initLogging(stdErr);
            assertEquals("TEST DEBUG arg 1 3.00", Log.formatStandardMessage("FOO", 1, "arg", 3.0F));
        }
        finally {
            reloadStandardErrorMessages();
        }
    }

    // brittle:  will break when the wording of the error message is changed
    @Test
    public void testStandardCBLException() {
        Map<String, String> stdErr = new HashMap<>();
        stdErr.put("FOO", "TEST DEBUG");
        try {
            Log.initLogging(stdErr);
            CouchbaseLiteException e = new CouchbaseLiteException(
                "FOO",
                CBLError.Domain.CBLITE,
                CBLError.Code.UNIMPLEMENTED);
            assertEquals("TEST DEBUG", e.getMessage());
        }
        finally {
            reloadStandardErrorMessages();
        }
    }

    @Test
    public void testNonStandardCBLException() {
        Map<String, String> stdErr = new HashMap<>();
        stdErr.put("FOO", "TEST DEBUG");
        try {
            Log.initLogging(stdErr);
            CouchbaseLiteException e = new CouchbaseLiteException(
                "BORK",
                CBLError.Domain.CBLITE,
                CBLError.Code.UNIMPLEMENTED);
            assertEquals("BORK", e.getMessage());
        }
        finally {
            reloadStandardErrorMessages();
        }
    }

    private void testWithConfiguration(LogLevel level, LogFileConfiguration config, TestUtils.Task task)
        throws Exception {
        final com.couchbase.lite.Log logger = Database.log;

        final ConsoleLogger consoleLogger = logger.getConsole();
        consoleLogger.setLevel(level);

        final FileLogger fileLogger = logger.getFile();
        fileLogger.setConfig(config);
        fileLogger.setLevel(level);

        task.run();
    }

    private void writeOneKiloByteOfLog() {
        String message = "11223344556677889900"; // ~43 bytes
        // 24 * 43 = 1032
        for (int i = 0; i < 24; i++) { writeAllLogs(message); }
    }

    private void writeAllLogs(String message) {
        Log.d(LogDomain.DATABASE, message);
        Log.v(LogDomain.DATABASE, message);
        Log.i(LogDomain.DATABASE, message);
        Log.w(LogDomain.DATABASE, message);
        Log.e(LogDomain.DATABASE, message);
    }

    @NotNull
    private String getLogContents(File log) throws IOException {
        byte[] b = new byte[(int) log.length()];
        FileInputStream fileInputStream = new FileInputStream(log);
        assertEquals(b.length, fileInputStream.read(b));
        return new String(b, StandardCharsets.US_ASCII);
    }

    @NotNull
    private File getMostRecent(@NotNull File[] files) {
        File lastModifiedFile = files[0];
        for (File log : files) {
            if (log.lastModified() > lastModifiedFile.lastModified()) { lastModifiedFile = log; }
        }
        return lastModifiedFile;
    }

    @NotNull
    private File[] getLogFiles() {
        File[] files = getTempDir().listFiles();
        assertNotNull(files);
        return files;
    }

    @Nullable
    private File getTempDir() { return (tempDirPath == null) ? null : new File(tempDirPath); }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void recursiveDelete(@Nullable File root) {
        if (root == null) { return; }
        File[] files = root.listFiles();
        if (files != null) {
            for (File file : files) { recursiveDelete(file); }
        }
        root.delete();
    }
}
