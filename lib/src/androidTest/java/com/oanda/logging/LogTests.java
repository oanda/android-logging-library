package com.oanda.logging;

import android.content.Context;
import android.test.ActivityTestCase;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Testing class for com.util.Log. The reason why most methods have a 1 second delay is to ensure
 * that the write thread has had time to perform all of the write operations.
 */
public class LogTests extends ActivityTestCase {

    /**
     * The system's newline String
     */
    private static final String mNewLine = System.getProperty("line.separator");

    /**
     * The context to use with Log.init().
     */
    private Context mContext;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mContext = getInstrumentation().getTargetContext();
        Log.init(mContext);

        Log.clearLog();
        Log.waitUntilFinishedWriting();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        mContext = null;
    }

    /**
     * Scenario:
     * When I call Log.init with a valid Context
     * Then it should return true
     */
    public void testLogInit() {
        // Initialize Log with testContext
        assertTrue("Could not initialize Log", Log.init(mContext));
    }

    /**
     * Scenario:
     * Given an empty log
     * When I log a large number (>Log.CIRCULAR_BUFFER_SIZE) of empty lines
     * Then when I call init again, the number of lines in the log is <= Log.CIRCULAR_BUFFER_SIZE
     * and >= Log.CIRCULAR_BUFFER_SIZE - Log.NUM_LINES_PER_CHUNK
     */
    public void testLogInitTrimsToLength() {
        int numExtraEntries = 205;
        // Manually write to the log file to create the situation where there are too many lines in the
        // file
        boolean successfullyWroteFile;
        try {
            // Manually open the file and append a bunch of lines to it
            BufferedWriter bufferedWriter = new BufferedWriter(new PrintWriter(mContext.openFileOutput(Log.FILENAME, Context.MODE_APPEND)));
            // Write more than CIRCULAR_BUFFER_SIZE lines to the buffer
            for (int i = 0; i < Log.CIRCULAR_BUFFER_SIZE + numExtraEntries; i++) {
                // Append a whole bunch of near-empty lines to the file
                bufferedWriter.write(" " + mNewLine);
            }
            bufferedWriter.close();
            successfullyWroteFile = true;
        } catch (IOException ioException) {
            successfullyWroteFile = false;
        }

        assertTrue("Could not write to the log file", successfullyWroteFile);

        // Re-init because we want to test that the initialization trims the log properly
        Log.init(mContext);

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
     * Given an empty log
     * When I log a bunch of entries exceeding the size of the circular buffer
     * Then the number of lines in the log is <= Log.CIRCULAR_BUFFER_SIZE
     * and >= Log.CIRCULAR_BUFFER_SIZE - Log.NUM_LINES_PER_CHUNK
     */
    public void testLogCircularBuffer() {
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
     * Given an empty log
     * When I log a bunch of entries from multiple threads concurrently
     * Then the log contains each entry without overlap
     */
    public void testLogConcurrence() {
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

        assertTrue("Concurrence test failed: numFirstThread: " + numFirstThread +
                        " numSecondThread: " + numSecondThread,
                numFirstThread == numTimesToLog && numSecondThread == numTimesToLog
        );
    }

    /**
     * Scenario:
     * Given an empty log
     * When I log a single entry and I call Log.clearLog()
     * Then the String representation of the log is ""
     */
    public void testLogClear() {
        // Make sure something gets put into the log
        Log.d("LogTest", "testLogClear");

        // Wait until all writing finishes
        Log.waitUntilFinishedWriting();

        Log.clearLog();

        // Wait until all writing finishes
        Log.waitUntilFinishedWriting();

        assertEquals(Log.readLog(), "");
    }

    /**
     * Scenario:
     * Given an empty log
     * When I log a single entry using Log.v
     * Then the String representation of the log contains the logged entry
     */
    public void testLogVerbose() {
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
     * Given an empty log
     * When I log a single entry with a Throwable using Log.v
     * Then the String representation of the log contains the logged entry
     */
    public void testLogVerboseThrowable() {
        long now = System.currentTimeMillis();

        Throwable tr = new Throwable("Testing throwable");
        tr.fillInStackTrace();

        // Call the verbose log function
        Log.v("LogTest", "testLogVerbose " + now, tr);

        // Wait until all writing finishes
        Log.waitUntilFinishedWriting();

        String log = Log.readLog();

        // The log should contain the entry
        assertTrue("Log.v could not write to the log with Throwable", log.contains("[VERBOSE] LogTest testLogVerbose " + now + mNewLine + Log.getStackTraceString(tr)));
    }

    /**
     * Scenario:
     * Given an empty log
     * When I log a single entry using Log.d
     * Then the String representation of the log contains the logged entry
     */
    public void testLogDebug() {
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
     * Given an empty log
     * When I log a single entry with a Throwable using Log.d
     * Then the String representation of the log contains the logged entry
     */
    public void testLogDebugThrowable() {
        long now = System.currentTimeMillis();

        Throwable tr = new Throwable("Testing throwable");
        tr.fillInStackTrace();

        // Call the debug log function
        Log.d("LogTest", "testLogDebug " + now, tr);

        // Wait until all writing finishes
        Log.waitUntilFinishedWriting();

        String log = Log.readLog();

        // The log should contain the entry
        assertTrue("Log.d could not write to the log with Throwable", log.contains("[DEBUG] LogTest testLogDebug " + now + mNewLine + Log.getStackTraceString(tr)));
    }

    /**
     * Scenario:
     * Given an empty log
     * When I log a single entry using Log.i
     * Then the String representation of the log contains the logged entry
     */
    public void testLogInfo() {
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
     * Given an empty log
     * When I log a single entry with a Throwable using Log.i
     * Then the String representation of the log contains the logged entry
     */
    public void testLogInfoThrowable() {
        long now = System.currentTimeMillis();

        Throwable tr = new Throwable("Testing throwable");
        tr.fillInStackTrace();

        // Call the info log function
        Log.i("LogTest", "testLogInfo " + now, tr);

        // Wait until all writing finishes
        Log.waitUntilFinishedWriting();

        String log = Log.readLog();

        // The log should contain the entry
        assertTrue("Log.i could not write to the log with Throwable", log.contains("[INFO] LogTest testLogInfo " + now + mNewLine + Log.getStackTraceString(tr)));
    }

    /**
     * Scenario:
     * Given an empty log
     * When I log a single entry using Log.w
     * Then the String representation of the log contains the logged entry
     */
    public void testLogWarning() {
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
     * Given an empty log
     * When I log a single entry with a Throwable using Log.w
     * Then the String representation of the log contains the logged entry
     */
    public void testLogWarningThrowable() {
        long now = System.currentTimeMillis();

        Throwable tr = new Throwable("Testing throwable");
        tr.fillInStackTrace();

        // Call the warn log function
        Log.w("LogTest", "testLogWarning " + now, tr);

        // Wait until all writing finishes
        Log.waitUntilFinishedWriting();

        String log = Log.readLog();

        // The log should contain the entry
        assertTrue("Log.w could not write to the log with Throwable", log.contains("[WARNING] LogTest testLogWarning " + now + mNewLine + Log.getStackTraceString(tr)));
    }

    /**
     * Scenario:
     * Given an empty log
     * When I log a single entry with a Throwable and no message using Log.w
     * Then the String representation of the log contains the logged entry
     */
    public void testLogWarningThrowableNoMessage() {
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
     * Given an empty log
     * When I log a single entry using Log.e
     * Then the String representation of the log contains the logged entry
     */
    public void testLogError() {
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
     * Given an empty log
     * When I log a single entry with a Throwable using Log.e
     * Then the String representation of the log contains the logged entry
     */
    public void testLogErrorThrowable() {
        long now = System.currentTimeMillis();

        Throwable tr = new Throwable("Testing throwable");
        tr.fillInStackTrace();

        // Call the error log function
        Log.e("LogTest", "testLogError " + now, tr);

        // Wait until all writing finishes
        Log.waitUntilFinishedWriting();

        String log = Log.readLog();

        // The log should contain the entry
        assertTrue("Log.e could not write to the log with Throwable", log.contains("[ERROR] LogTest testLogError " + now + mNewLine + Log.getStackTraceString(tr)));
    }

    /**
     * Scenario:
     * Given an empty log
     * When I call Log.println with each priority level
     * Then the String representation of the log contains each logged entry
     */
    public void testLogPrintln() {
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
