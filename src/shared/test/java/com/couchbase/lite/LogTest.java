package com.couchbase.lite;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.couchbase.lite.internal.core.CBLVersion;
import com.couchbase.lite.internal.support.Log;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


public class LogTest extends BaseTest {
    interface Task {
        void run() throws Exception;
    }

    static class LogTestLogger implements Logger {
        private final List<String> lines = new ArrayList<>();
        private LogLevel level;

        @Override
        public void log(@NotNull LogLevel level, @NotNull LogDomain domain, @NotNull String message) {
            if (level.compareTo(this.level) < 0) { return; }
            lines.add(message);
        }

        @NotNull
        @Override
        public LogLevel getLevel() { return level; }

        List<String> getLines() { return lines; }

        void setLevel(LogLevel level) { this.level = level; }
    }


    private String tempDirPath;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        tempDirPath = createTmpDirectory("logtest");

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

        for (int i = 5; i >= 1; i--) {
            customLogger.getLines().clear();
            customLogger.setLevel(LogLevel.values()[i]);
            Log.v(LogDomain.DATABASE, "TEST VERBOSE");
            Log.i(LogDomain.DATABASE, "TEST INFO");
            Log.w(LogDomain.DATABASE, "TEST WARNING");
            Log.e(LogDomain.DATABASE, "TEST ERROR");
            assertEquals(5 - i, customLogger.getLines().size());
        }
    }

    @Test
    public void testEnableAndDisableCustomLogging() {
        LogTestLogger customLogger = new LogTestLogger();
        Database.log.setCustom(customLogger);

        customLogger.setLevel(LogLevel.NONE);
        Log.v(LogDomain.DATABASE, "TEST VERBOSE");
        Log.i(LogDomain.DATABASE, "TEST INFO");
        Log.w(LogDomain.DATABASE, "TEST WARNING");
        Log.e(LogDomain.DATABASE, "TEST ERROR");
        assertEquals(0, customLogger.getLines().size());

        customLogger.setLevel(LogLevel.VERBOSE);
        Log.v(LogDomain.DATABASE, "TEST VERBOSE");
        Log.i(LogDomain.DATABASE, "TEST INFO");
        Log.w(LogDomain.DATABASE, "TEST WARNING");
        Log.e(LogDomain.DATABASE, "TEST ERROR");
        assertEquals(4, customLogger.getLines().size());
    }

    @Test
    public void testFileLoggingLevels() throws Exception {
        LogFileConfiguration config = new LogFileConfiguration(tempDirPath)
            .setUsePlaintext(true)
            .setMaxRotateCount(0);

        testWithConfiguration(
            LogLevel.INFO,
            config,
            () -> {
                for (LogLevel level : LogLevel.values()) {
                    if (level == LogLevel.DEBUG) { continue; }
                    Database.log.getFile().setLevel(level);
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
                    // One meta line per log, so the actual logging lines is X - 1
                    if (logPath.contains("verbose")) { assertEquals(2, lineCount); }
                    else if (logPath.contains("info")) { assertEquals(3, lineCount); }
                    else if (logPath.contains("warning")) { assertEquals(4, lineCount); }
                    else if (logPath.contains("error")) { assertEquals(5, lineCount); }
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
                Log.i(LogDomain.DATABASE, "TEST MESSAGE");

                File[] files = getLogFiles();
                assertTrue(files.length > 0);

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
    public void testWriteLogWithError() throws Exception {
        String message = "test message";
        String uuid = UUID.randomUUID().toString();
        CouchbaseLiteException error = new CouchbaseLiteException(uuid);

        LogFileConfiguration config = new LogFileConfiguration(tempDirPath).setUsePlaintext(true);

        testWithConfiguration(
            LogLevel.DEBUG,
            config,
            () -> {
                Log.v(LogDomain.DATABASE, message, error);
                Log.i(LogDomain.DATABASE, message, error);
                Log.w(LogDomain.DATABASE, message, error);
                Log.e(LogDomain.DATABASE, message, error);
                Log.d(LogDomain.DATABASE, message, error);
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
                Log.v(LogDomain.DATABASE, message, error, uuid2);
                Log.i(LogDomain.DATABASE, message, error, uuid2);
                Log.w(LogDomain.DATABASE, message, error, uuid2);
                Log.e(LogDomain.DATABASE, message, error, uuid2);
                Log.d(LogDomain.DATABASE, message, error, uuid2);

                for (File log : getLogFiles()) {
                    String contents = getLogContents(log);
                    assertTrue(contents.contains(uuid1));
                    assertTrue(contents.contains(uuid2));
                }
            });
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void testLogFileConfigurationConstructors() {
        int rotateCount = 4;
        long maxSize = 2048;
        boolean usePlainText = true;

        thrown.expect(IllegalArgumentException.class);
        new LogFileConfiguration((String) null);

        thrown.expect(IllegalArgumentException.class);
        new LogFileConfiguration((LogFileConfiguration) null);

        LogFileConfiguration config = new LogFileConfiguration(tempDirPath)
            .setMaxRotateCount(rotateCount)
            .setMaxSize(maxSize)
            .setUsePlaintext(usePlainText);
        assertEquals(config.getMaxRotateCount(), rotateCount);
        assertEquals(config.getMaxSize(), maxSize);
        assertEquals(config.usesPlaintext(), usePlainText);
        assertEquals(config.getDirectory(), tempDirPath);

        final String tempDir2 = createTmpDirectory("logtest2");
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
                thrown.expect(IllegalStateException.class);
                Database.log.getFile().setConfig(config);

                thrown.expect(IllegalStateException.class);
                Database.log.getFile().getConfig().setMaxSize(1024);

                thrown.expect(IllegalStateException.class);
                Database.log.getFile().getConfig().setMaxRotateCount(3);

                thrown.expect(IllegalStateException.class);
                Database.log.getFile().getConfig().setUsePlaintext(true);
            });
    }

    @Test
    public void testNonASCII() throws CouchbaseLiteException {
        LogTestLogger customLogger = new LogTestLogger();
        customLogger.setLevel(LogLevel.VERBOSE);
        Database.log.setCustom(customLogger);
        Database.log.getConsole().setDomains(LogDomain.ALL_DOMAINS);
        Database.log.getConsole().setLevel(LogLevel.VERBOSE);

        String hebrew = "מזג האוויר נחמד היום"; // The weather is nice today.
        MutableDocument doc = new MutableDocument();
        doc.setString("hebrew", hebrew);
        save(doc);

        Query query = QueryBuilder.select(SelectResult.all()).from(DataSource.database(db));
        ResultSet rs = query.execute();
        assertEquals(rs.allResults().size(), 1);

        String expectedHebrew = "[{\"hebrew\":\"" + hebrew + "\"}]";
        boolean found = false;
        for (String line : customLogger.getLines()) {
            if (line.contains(expectedHebrew)) {
                found = true;
                break;
            }
        }
        assertTrue(found);
    }

    private void testWithConfiguration(LogLevel level, LogFileConfiguration config, Task task) throws Exception {
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
        Log.v(LogDomain.DATABASE, message);
        Log.i(LogDomain.DATABASE, message);
        Log.w(LogDomain.DATABASE, message);
        Log.e(LogDomain.DATABASE, message);
        Log.d(LogDomain.DATABASE, message);
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

    @NotNull
    private File getTempDir() { return new File(tempDirPath); }

    private String createTmpDirectory(String nameRoot) {
        return getTempDirectory(nameRoot + "-" + System.currentTimeMillis());
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void recursiveDelete(File root) {
        File[] files = root.listFiles();
        if (files != null) {
            for (File file : files) {
                recursiveDelete(file);
            }
        }
        root.delete();
    }
}
