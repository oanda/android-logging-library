package com.oanda.logging;

import android.content.Context;

import junit.framework.TestCase;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Testing class for com.oanda.logging.Log.
 */
public class LogTests extends TestCase {

    private final String dir = System.getProperty("user.dir");

    private final File mLogFile = new File(dir, Log.FILENAME);
    private final File mLogFileTemp = new File(dir, Log.TEMP_FILENAME);

    private Context mMockContext;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mMockContext = mock(Context.class);

        // Mock opening input to the file system where gradle is run
        when(mMockContext.openFileInput(Log.FILENAME)).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                return new FileInputStream(mLogFile);
            }
        });
        when(mMockContext.openFileInput(Log.TEMP_FILENAME)).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                return new FileInputStream(mLogFileTemp);
            }
        });

        // Mock opening output to the file system where gradle is run
        when(mMockContext.openFileOutput(Log.FILENAME, Context.MODE_APPEND)).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                return new FileOutputStream(mLogFile);
            }
        });
        when(mMockContext.openFileOutput(Log.TEMP_FILENAME, Context.MODE_APPEND)).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                return new FileOutputStream(mLogFileTemp);
            }
        });

        // Mock deleting files to the file system where gradle is run
        when(mMockContext.deleteFile(Log.FILENAME)).thenAnswer(new Answer() {

            @Override
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                return mLogFile.delete();
            }
        });
        when(mMockContext.deleteFile(Log.TEMP_FILENAME)).thenAnswer(new Answer() {

            @Override
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                return mLogFileTemp.delete();
            }
        });

        // Mock getting the path of a file to the file system where gradle is run
        when(mMockContext.getFileStreamPath(Log.FILENAME)).thenReturn(mLogFile);
        when(mMockContext.getFileStreamPath(Log.TEMP_FILENAME)).thenReturn(mLogFileTemp);

        // This makes the usefulness of testLogGetLogFile() questionable, it will never fail
        // because of this mock
        when(mMockContext.getFilesDir()).thenReturn(mLogFile.getParentFile());
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        mMockContext = null;

        mLogFile.delete();
        mLogFileTemp.delete();
    }

    /**
     * Initialize Log. Used for tests where it is given that Log is initialized.
     */
    private void init() {
        Log.init(mMockContext);

        Log.clearLog();
        Log.waitUntilFinishedWriting();
    }

    /**
     * Scenario:
     * Given Log is uninitialized
     * When I call Log.init with a valid Context
     * Then it should return true
     */
    public void testLogInitValidContext() {
        // Initialize Log with mock Context
        assertTrue("Could not initialize Log with valid context", Log.init(mMockContext));
    }

    /**
     * Scenario:
     * Given Log is uninitialized
     * When I call Log.init with an invalid Context
     * Then it should return false
     */
    public void testLogInitInvalidContext() {
        // Initialize Log with null
        assertFalse("Log was initialized with invalid context", Log.init(null));
    }

    /**
     * Scenario:
     * Given Log is uninitialized and I have an overfull log file
     * When I call init
     * Then the log file is trimmed
     */
    public void testLogInitTrimsToLength() {
        final int numExtraEntries = 205;

        // Manually write to the log file to create the situation where there are too many lines in the
        // file
        boolean successfullyWroteFile;
        try {
            // Manually open the file and append a bunch of lines to it
            BufferedWriter bufferedWriter = Log.getBufferedWriter(Log.FILENAME);
            // Write more than CIRCULAR_BUFFER_SIZE lines to the buffer
            for (int i = 0; i < Log.CIRCULAR_BUFFER_SIZE + numExtraEntries; i++) {
                // Append a whole bunch of near-empty lines to the file
                bufferedWriter.write(" " + System.getProperty("line.separator"));
            }
            bufferedWriter.close();
            successfullyWroteFile = true;
        } catch (IOException ioException) {
            successfullyWroteFile = false;
        }

        assertTrue("Could not write to the log file", successfullyWroteFile);

        // Init because we want to test that the initialization trims the log properly
        Log.init(mMockContext);

        String log = Log.readLog();

        // Find the number of occurrences of '\n' in the current entries
        int numLines = 0;
        for (int i = 0; i < log.length(); i++) {
            if (log.charAt(i) == '\n') {
                numLines++;
            }
        }

        assertTrue("Log.init did not trim the log. numLines: " + numLines, numLines <= Log.CIRCULAR_BUFFER_SIZE);
    }

    /**
     * Scenario:
     * Given Log is uninitialized
     * When I call each Log method that doesn't correspond with android.util.Log
     * Then and empty String, null, or false is returned
     */
    public void testLogUninitialized() {
        assertFalse("Log.init() returned true with a null Context", Log.init(null));
        assertNull("Log.getLogFile() returned non-null without Log being initialized", Log.getLogFile());
        assertEquals("Log.readLog() returned non-empty String without Log being initialized", "", Log.readLog());
        assertFalse("Log.clearLog() returned true without Log being initialized", Log.clearLog());
    }

    /**
     * Scenario:
     * Given Log is initialized and I have an empty log
     * When I call Log.getLogFile()
     * Then I should get a file representing the String mContext.getFilesDir() + Log.FILENAME
     */
    public void testLogGetLogFile() {
        init();

        File logFile = Log.getLogFile();

        String actualPath = logFile.getAbsolutePath();
        String expectedPath = mMockContext.getFilesDir() + "/" + Log.FILENAME;

        assertEquals(expectedPath, actualPath);
    }

    /**
     * Scenario:
     * Given Log is initialized and I have an empty log
     * When I log entries to overfill the circular buffer
     * Then the log is trimmed when I finish logging
     */
    public void testLogCircularBuffer() {
        init();

        int numExtraEntries = 205;

        for (int i = 0; i < Log.CIRCULAR_BUFFER_SIZE + numExtraEntries; i++) {
            Log.d("LogTest", "testLogCircularBuffer");
        }

        // Wait until all writing finishes
        Log.waitUntilFinishedWriting();

        String log = Log.readLog();

        // Find the number of occurrences of '\n' in the current entries
        int numLines = 0;
        for (int i = 0; i < log.length(); i++) {
            if (log.charAt(i) == '\n') {
                numLines++;
            }
        }

        assertTrue("The circular buffer was not applied. numLines: " + numLines, numLines <= Log.CIRCULAR_BUFFER_SIZE);
    }

    /**
     * Scenario:
     * Given Log is initialized and I have an empty log
     * When I log a bunch of entries from multiple threads concurrently
     * Then the log contains each entry without overlap
     */
    public void testLogConcurrence() {
        init();

        final int numTimesToLog = 200;

        // Thread to log a certain number of times
        class LogTestThread extends Thread {
            private String mId;

            public LogTestThread(String id) {
                mId = id;
            }

            @Override
            public void run() {
                for (int i = 0; i < numTimesToLog; i++) {
                    Log.d("LogTestThread", mId);
                }
            }
        }

        String firstThreadId = "fi", secondThreadId = "se";

        LogTestThread firstThread = new LogTestThread(firstThreadId);
        LogTestThread secondThread = new LogTestThread(secondThreadId);

        firstThread.start();
        secondThread.start();

        // Give the LogThreads some time to start up and start the write thread
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Wait until all writing finishes
        Log.waitUntilFinishedWriting();

        String log = Log.readLog();

        // Count the number of occurrences of firstThreadId and secondThreadId
        int numFirstThread = 0, numSecondThread = 0;
        char lastChar = 0, curChar;
        for (int i = 0; i < log.length(); i++) {
            curChar = log.charAt(i);

            if (lastChar == firstThreadId.charAt(0) && curChar == firstThreadId.charAt(1)) {
                numFirstThread++;
            } else if (lastChar == secondThreadId.charAt(0) && curChar == secondThreadId.charAt(1)) {
                numSecondThread++;
            }

            lastChar = curChar;
        }

        assertEquals("The first logging thread did not appear the correct number of times", numTimesToLog, numFirstThread);
        assertEquals("The second logging thread did not appear the correct number of times", numTimesToLog, numSecondThread);
    }

    /**
     * Scenario:
     * Given Log is initialized and I have an empty log
     * When I log a single entry and I call Log.clearLog()
     * Then the log has nothing in it
     */
    public void testLogClear() {
        init();

        // Make sure something gets put into the log
        Log.d("LogTest", "testLogClear");

        // Wait until all writing finishes
        Log.waitUntilFinishedWriting();

        Log.clearLog();

        // Wait until all writing finishes
        Log.waitUntilFinishedWriting();

        String log = Log.readLog();

        assertEquals("", log);
    }

    /**
     * Scenario:
     * Given Log is initialized and I have an empty log
     * When I log a single entry using Log.v
     * Then the log contains the logged entry
     */
    public void testLogVerbose() {
        init();

        long now = System.currentTimeMillis();

        // Call the verbose log function
        Log.v("LogTest", "testLogVerbose " + now);

        // Wait until all writing finishes
        Log.waitUntilFinishedWriting();

        String log = Log.readLog();

        // The log should contain the entry
        assertTrue("Log.v could not write to the log", log.contains("[VERBOSE] LogTest testLogVerbose " + now));
    }

    /**
     * Scenario:
     * Given Log is initialized and I have an empty log
     * When I log a single entry with a Throwable using Log.v
     * Then the log contains the logged entry
     */
    public void testLogVerboseThrowable() {
        init();

        long now = System.currentTimeMillis();

        Throwable tr = new Throwable("Testing throwable");
        tr.fillInStackTrace();

        // Call the verbose log function
        Log.v("LogTest", "testLogVerbose " + now, tr);

        // Wait until all writing finishes
        Log.waitUntilFinishedWriting();

        String log = Log.readLog();

        // The log should contain the entry
        assertTrue("Log.v could not write to the log with Throwable", log.contains("[VERBOSE] LogTest testLogVerbose " + now + System.getProperty("line.separator") + Log.getStackTraceString(tr)));
    }

    /**
     * Scenario:
     * Given Log is initialized and I have an empty log
     * When I log a single entry using Log.d
     * Then the log contains the logged entry
     */
    public void testLogDebug() {
        init();

        long now = System.currentTimeMillis();

        // Call the debug log function
        Log.d("LogTest", "testLogDebug " + now);

        // Wait until all writing finishes
        Log.waitUntilFinishedWriting();

        String log = Log.readLog();

        // The log should contain the entry
        assertTrue("Log.d could not write to the log", log.contains("[DEBUG] LogTest testLogDebug " + now));
    }

    /**
     * Scenario:
     * Given Log is initialized and I have an empty log
     * When I log a single entry with a Throwable using Log.d
     * Then the log contains the logged entry
     */
    public void testLogDebugThrowable() {
        init();

        long now = System.currentTimeMillis();

        Throwable tr = new Throwable("Testing throwable");
        tr.fillInStackTrace();

        // Call the debug log function
        Log.d("LogTest", "testLogDebug " + now, tr);

        // Wait until all writing finishes
        Log.waitUntilFinishedWriting();

        String log = Log.readLog();

        // The log should contain the entry
        assertTrue("Log.d could not write to the log with Throwable", log.contains("[DEBUG] LogTest testLogDebug " + now + System.getProperty("line.separator") + Log.getStackTraceString(tr)));
    }

    /**
     * Scenario:
     * Given Log is initialized and I have an empty log
     * When I log a single entry using Log.i
     * Then the log contains the logged entry
     */
    public void testLogInfo() {
        init();

        long now = System.currentTimeMillis();

        // Call the info log function
        Log.i("LogTest", "testLogInfo " + now);

        // Wait until all writing finishes
        Log.waitUntilFinishedWriting();

        String log = Log.readLog();

        // The log should contain the entry
        assertTrue("Log.i could not write to the log", log.contains("[INFO] LogTest testLogInfo " + now));
    }

    /**
     * Scenario:
     * Given Log is initialized and I have an empty log
     * When I log a single entry with a Throwable using Log.i
     * Then the log contains the logged entry
     */
    public void testLogInfoThrowable() {
        init();

        long now = System.currentTimeMillis();

        Throwable tr = new Throwable("Testing throwable");
        tr.fillInStackTrace();

        // Call the info log function
        Log.i("LogTest", "testLogInfo " + now, tr);

        // Wait until all writing finishes
        Log.waitUntilFinishedWriting();

        String log = Log.readLog();

        // The log should contain the entry
        assertTrue("Log.i could not write to the log with Throwable", log.contains("[INFO] LogTest testLogInfo " + now + System.getProperty("line.separator") + Log.getStackTraceString(tr)));
    }

    /**
     * Scenario:
     * Given Log is initialized and I have an empty log
     * When I log a single entry using Log.w
     * Then the log contains the logged entry
     */
    public void testLogWarning() {
        init();

        long now = System.currentTimeMillis();

        // Call the warn log function
        Log.w("LogTest", "testLogWarning " + now);

        // Wait until all writing finishes
        Log.waitUntilFinishedWriting();

        String log = Log.readLog();

        // The log should contain the entry
        assertTrue("Log.w could not write to the log", log.contains("[WARNING] LogTest testLogWarning " + now));
    }

    /**
     * Scenario:
     * Given Log is initialized and I have an empty log
     * When I log a single entry with a Throwable using Log.w
     * Then the log contains the logged entry
     */
    public void testLogWarningThrowable() {
        init();

        long now = System.currentTimeMillis();

        Throwable tr = new Throwable("Testing throwable");
        tr.fillInStackTrace();

        // Call the warn log function
        Log.w("LogTest", "testLogWarning " + now, tr);

        // Wait until all writing finishes
        Log.waitUntilFinishedWriting();

        String log = Log.readLog();

        // The log should contain the entry
        assertTrue("Log.w could not write to the log with Throwable", log.contains("[WARNING] LogTest testLogWarning " + now + System.getProperty("line.separator") + Log.getStackTraceString(tr)));
    }

    /**
     * Scenario:
     * Given Log is initialized and I have an empty log
     * When I log a single entry with a Throwable and no message using Log.w
     * Then the log contains the logged entry
     */
    public void testLogWarningThrowableNoMessage() {
        init();

        Throwable tr = new Throwable("Testing throwable");
        tr.fillInStackTrace();

        // Call the warn log function
        Log.w("LogTest", tr);

        // Wait until all writing finishes
        Log.waitUntilFinishedWriting();

        String log = Log.readLog();

        // The log should contain the entry
        assertTrue("Log.w could not write to the log with Throwable and no message", log.contains("[WARNING] LogTest\n" + Log.getStackTraceString(tr)));
    }

    /**
     * Scenario:
     * Given Log is initialized and I have an empty log
     * When I log a single entry using Log.e
     * Then the log contains the logged entry
     */
    public void testLogError() {
        init();

        long now = System.currentTimeMillis();

        // Call the error log function
        Log.e("LogTest", "testLogError " + now);

        // Wait until all writing finishes
        Log.waitUntilFinishedWriting();

        String log = Log.readLog();

        // The log should contain the entry
        assertTrue("Log.e could not write to the log", log.contains("[ERROR] LogTest testLogError " + now));
    }

    /**
     * Scenario:
     * Given Log is initialized and I have an empty log
     * When I log a single entry with a Throwable using Log.e
     * Then the log contains the logged entry
     */
    public void testLogErrorThrowable() {
        init();

        long now = System.currentTimeMillis();

        Throwable tr = new Throwable("Testing throwable");
        tr.fillInStackTrace();

        // Call the error log function
        Log.e("LogTest", "testLogError " + now, tr);

        // Wait until all writing finishes
        Log.waitUntilFinishedWriting();

        String log = Log.readLog();

        // The log should contain the entry
        assertTrue("Log.e could not write to the log with Throwable", log.contains("[ERROR] LogTest testLogError " + now + System.getProperty("line.separator") + Log.getStackTraceString(tr)));
    }

    /**
     * Scenario:
     * Given Log is initialized and I have an empty log
     * When I call Log.println with each priority level
     * Then the log contains each logged entry
     */
    public void testLogPrintln() {
        init();

        long now = System.currentTimeMillis();

        // Call the println function for each level of priority
        Log.println(Log.VERBOSE, "LogTest", "testLogPrintln " + now);
        Log.println(Log.DEBUG, "LogTest", "testLogPrintln " + now);
        Log.println(Log.INFO, "LogTest", "testLogPrintln " + now);
        Log.println(Log.WARN, "LogTest", "testLogPrintln " + now);
        Log.println(Log.ERROR, "LogTest", "testLogPrintln " + now);
        Log.println(Log.ASSERT, "LogTest", "testLogPrintln " + now);

        // Wait until all writing finishes
        Log.waitUntilFinishedWriting();

        String log = Log.readLog();

        // The log should contain each of the printlns with their priority
        assertTrue("Log.println could not write to the log with priority verbose", log.contains("[VERBOSE] LogTest testLogPrintln " + now));
        assertTrue("Log.println could not write to the log with priority debug", log.contains("[DEBUG] LogTest testLogPrintln " + now));
        assertTrue("Log.println could not write to the log with priority info", log.contains("[INFO] LogTest testLogPrintln " + now));
        assertTrue("Log.println could not write to the log with priority warning", log.contains("[WARNING] LogTest testLogPrintln " + now));
        assertTrue("Log.println could not write to the log with priority error", log.contains("[ERROR] LogTest testLogPrintln " + now));
        assertTrue("Log.println could not write to the log with priority assert", log.contains("[ASSERT] LogTest testLogPrintln " + now));
    }
}
