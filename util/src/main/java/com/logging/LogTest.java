package com.logging;

import android.content.Context;
import android.test.ActivityTestCase;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;


/**
 * Testing class for com.util.Log.
 *
 * Created by bcwatling on 09/05/14.
 */
public class LogTest extends ActivityTestCase {

    /**
     * The context to use with Log.init().
     */
    private Context mContext;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mContext = getInstrumentation().getTargetContext();
        Log.init(mContext);
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
     * Given an empty log file
     * When I open the log file and append a large number (>Log.CIRCULAR_BUFFER_SIZE) of empty lines
     * Then when I call init again, the number of lines in the file is <= Log.CIRCULAR_BUFFER_SIZE
     * and >= Log.CIRCULAR_BUFFER_SIZE - Log.NUM_LINES_PER_CHUNK
     */
    public void testLogInitTrimsToLength() {
        // Clear the log file
        Log.clearLogFile();

        // Manually write to the file to create the situation where there are too many lines in the
        // file
        boolean successfullyWroteFile;
        try {
            // Manually open the file and append a bunch of lines to it
            BufferedWriter bufferedWriter = new BufferedWriter(new PrintWriter(mContext.openFileOutput(Log.FILENAME, Context.MODE_APPEND)));
            // Write more than CIRCULAR_BUFFER_SIZE lines to the buffer
            for (int i = 0; i < Log.CIRCULAR_BUFFER_SIZE + Log.NUM_LINES_PER_CHUNK * 2; i++) {
                // Append a whole bunch of empty lines to the file
                bufferedWriter.append(' ').append('\n');
            }
            bufferedWriter.close();
            successfullyWroteFile = true;
        } catch (IOException ioException) {
            successfullyWroteFile = false;
        }

        assertTrue("Could not write to log file", successfullyWroteFile);

        // Re-init because we want to test that the initialization trims the file down properly
        Log.init(mContext);

        int numLines = Log.getLogFile().split("\n").length;

        // After initialization, the number of lines in the log file should be
        // <= Log.CIRCULAR_BUFFER_SIZE
        assertTrue("Log.init did not trim log file enough", numLines <= Log.CIRCULAR_BUFFER_SIZE);
        assertTrue("Log.init trimmed the log file too much", numLines >= Log.CIRCULAR_BUFFER_SIZE - Log.NUM_LINES_PER_CHUNK);
    }

    /**
     * Scenario:
     * Given an empty log file
     * When I log a bunch of entries exceeding the size of the circular buffer
     * Then the number of lines in the log file is <= Log.CIRCULAR_BUFFER_SIZE
     * and >= Log.CIRCULAR_BUFFER_SIZE - Log.NUM_LINES_PER_CHUNK
     */
    public void testLogCircularBuffer() {
        //Start from an empty log file
        Log.clearLogFile();

        for (int i = 0; i < Log.CIRCULAR_BUFFER_SIZE + Log.NUM_LINES_PER_CHUNK * 2 + 2; i++) {
            Log.d("LogTest", "testLogCircularBuffer");
        }

        int numLines = Log.getLogFile().split("\n").length;
        assertTrue("The circular buffer did not remove enough lines", numLines <= Log.CIRCULAR_BUFFER_SIZE);
        assertTrue("The circular buffer removed too many lines", numLines >= Log.CIRCULAR_BUFFER_SIZE - Log.NUM_LINES_PER_CHUNK);
    }

    /**
     * Scenario:
     * Given an empty log file
     * When I log a single entry into the log file and I call Log.clearLogFile()
     * Then the String representation of the log file is ""
     */
    public void testLogClearFile() {
        Log.clearLogFile();

        // Make sure something gets put into the Log file
        Log.d("LogTest", "testLogClearFile");

        Log.clearLogFile();

        assertEquals(Log.getLogFile(), "");
    }

    /**
     * Scenario:
     * Given an empty log file
     * When I log a single entry using Log.v into the log file
     * Then the String representation of the log file contains the logged entry
     */
    public void testLogVerbose() {
        Log.clearLogFile();

        long now = System.currentTimeMillis();

        // Call the verbose log function
        Log.v("LogTest", "testLogVerbose " + now);

        String logFile = Log.getLogFile();

        // After the log, the log file should contain the entry
        assertTrue("Log.v could not write to file", logFile.contains("[VERBOSE] LogTest testLogVerbose " + now));
    }

    /**
     * Scenario:
     * Given an empty log file
     * When I log a single entry with a Throwable using Log.v into the log file
     * Then the String representation of the log file contains the logged entry
     */
    public void testLogVerboseThrowable() {
        Log.clearLogFile();

        long now = System.currentTimeMillis();

        Throwable tr = new Throwable("Testing throwable");
        tr.fillInStackTrace();

        // Call the verbose log function
        Log.v("LogTest", "testLogVerbose " + now, tr);

        String logFile = Log.getLogFile();

        // After the log, the log file should contain the entry
        assertTrue("Log.v could not write to file with Throwable", logFile.contains("[VERBOSE] LogTest testLogVerbose " + now + "\n" + Log.getStackTraceString(tr)));
    }

    /**
     * Scenario:
     * Given an empty log file
     * When I log a single entry using Log.d into the log file
     * Then the String representation of the log file contains the logged entry
     */
    public void testLogDebug() {
        Log.clearLogFile();

        long now = System.currentTimeMillis();

        // Call the debug log function
        Log.d("LogTest", "testLogDebug " + now);

        String logFile = Log.getLogFile();

        // After the log, the log file should contain the entry
        assertTrue("Log.d could not write to file", logFile.contains("[DEBUG] LogTest testLogDebug " + now));
    }

    /**
     * Scenario:
     * Given an empty log file
     * When I log a single entry with a Throwable using Log.d into the log file
     * Then the String representation of the log file contains the logged entry
     */
    public void testLogDebugThrowable() {
        Log.clearLogFile();

        long now = System.currentTimeMillis();

        Throwable tr = new Throwable("Testing throwable");
        tr.fillInStackTrace();

        // Call the debug log function
        Log.d("LogTest", "testLogDebug " + now, tr);

        String logFile = Log.getLogFile();

        // After the log, the log file should contain the entry
        assertTrue("Log.d could not write to file with Throwable", logFile.contains("[DEBUG] LogTest testLogDebug " + now + "\n" + Log.getStackTraceString(tr)));
    }

    /**
     * Scenario:
     * Given an empty log file
     * When I log a single entry using Log.i into the log file
     * Then the String representation of the log file contains the logged entry
     */
    public void testLogInfo() {
        Log.clearLogFile();

        long now = System.currentTimeMillis();

        // Call the info log function
        Log.i("LogTest", "testLogInfo " + now);

        String logFile = Log.getLogFile();

        // After the log, the log file should contain the entry
        assertTrue("Log.i could not write to file", logFile.contains("[INFO] LogTest testLogInfo " + now));
    }

    /**
     * Scenario:
     * Given an empty log file
     * When I log a single entry with a Throwable using Log.i into the log file
     * Then the String representation of the log file contains the logged entry
     */
    public void testLogInfoThrowable() {
        Log.clearLogFile();

        long now = System.currentTimeMillis();

        Throwable tr = new Throwable("Testing throwable");
        tr.fillInStackTrace();

        // Call the info log function
        Log.i("LogTest", "testLogInfo " + now, tr);

        String logFile = Log.getLogFile();

        // After the log, the log file should contain the entry
        assertTrue("Log.i could not write to file with Throwable", logFile.contains("[INFO] LogTest testLogInfo " + now + "\n" + Log.getStackTraceString(tr)));
    }

    /**
     * Scenario:
     * Given an empty log file
     * When I log a single entry using Log.w into the log file
     * Then the String representation of the log file contains the logged entry
     */
    public void testLogWarning() {
        Log.clearLogFile();

        long now = System.currentTimeMillis();

        // Call the warn log function
        Log.w("LogTest", "testLogWarning " + now);

        String logFile = Log.getLogFile();

        // After the log, the log file should contain the entry
        assertTrue("Log.w could not write to file", logFile.contains("[WARNING] LogTest testLogWarning " + now));
    }

    /**
     * Scenario:
     * Given an empty log file
     * When I log a single entry with a Throwable using Log.w into the log file
     * Then the String representation of the log file contains the logged entry
     */
    public void testLogWarningThrowable() {
        Log.clearLogFile();

        long now = System.currentTimeMillis();

        Throwable tr = new Throwable("Testing throwable");
        tr.fillInStackTrace();

        // Call the warn log function
        Log.w("LogTest", "testLogWarning " + now, tr);

        String logFile = Log.getLogFile();

        // After the log, the log file should contain the entry
        assertTrue("Log.w could not write to file with Throwable", logFile.contains("[WARNING] LogTest testLogWarning " + now + "\n" + Log.getStackTraceString(tr)));
    }

    /**
     * Scenario:
     * Given an empty log file
     * When I log a single entry with a Throwable and no message using Log.w into the log file
     * Then the String representation of the log file contains the logged entry
     */
    public void testLogWarningThrowableNoMessage() {
        Log.clearLogFile();

        Throwable tr = new Throwable("Testing throwable");
        tr.fillInStackTrace();

        // Call the warn log function
        Log.w("LogTest", tr);

        String logFile = Log.getLogFile();

        // After the log, the log file should contain the entry
        assertTrue("Log.w could not write to file with Throwable and no message", logFile.contains("[WARNING] LogTest\n" + Log.getStackTraceString(tr)));
    }

    /**
     * Scenario:
     * Given an empty log file
     * When I log a single entry using Log.e into the log file
     * Then the String representation of the log file contains the logged entry
     */
    public void testLogError() {
        Log.clearLogFile();

        long now = System.currentTimeMillis();

        // Call the error log function
        Log.e("LogTest", "testLogError " + now);

        String logFile = Log.getLogFile();

        // After the log, the log file should contain the entry
        assertTrue("Log.e could not write to file", logFile.contains("[ERROR] LogTest testLogError " + now));
    }

    /**
     * Scenario:
     * Given an empty log file
     * When I log a single entry with a Throwable using Log.e into the log file
     * Then the String representation of the log file contains the logged entry
     */
    public void testLogErrorThrowable() {
        Log.clearLogFile();

        long now = System.currentTimeMillis();

        Throwable tr = new Throwable("Testing throwable");
        tr.fillInStackTrace();

        // Call the error log function
        Log.e("LogTest", "testLogError " + now, tr);

        String logFile = Log.getLogFile();

        // After the log, the log file should contain the entry
        assertTrue("Log.e could not write to file with Throwable", logFile.contains("[ERROR] LogTest testLogError " + now + "\n" + Log.getStackTraceString(tr)));
    }

    /**
     * Scenario:
     * Given an empty log file
     * When I call Log.println with each priority level
     * Then the String representation of the log file contains each logged entry
     */
    public void testLogPrintln() {
        Log.clearLogFile();

        long now = System.currentTimeMillis();

        // Call the println function for each level of priority
        Log.println(Log.VERBOSE, "LogTest", "testLogPrintln " + now);
        Log.println(Log.DEBUG, "LogTest", "testLogPrintln " + now);
        Log.println(Log.INFO, "LogTest", "testLogPrintln " + now);
        Log.println(Log.WARN, "LogTest", "testLogPrintln " + now);
        Log.println(Log.ERROR, "LogTest", "testLogPrintln " + now);
        Log.println(Log.ASSERT, "LogTest", "testLogPrintln " + now);

        String logFile = Log.getLogFile();

        // After the log, the log file should contain each of the printlns with their priority
        assertTrue("Log.println could not write to file with priority verbose", logFile.contains("[VERBOSE] LogTest testLogPrintln " + now));
        assertTrue("Log.println could not write to file with priority debug", logFile.contains("[DEBUG] LogTest testLogPrintln " + now));
        assertTrue("Log.println could not write to file with priority info", logFile.contains("[INFO] LogTest testLogPrintln " + now));
        assertTrue("Log.println could not write to file with priority warning", logFile.contains("[WARNING] LogTest testLogPrintln " + now));
        assertTrue("Log.println could not write to file with priority error", logFile.contains("[ERROR] LogTest testLogPrintln " + now));
        assertTrue("Log.println could not write to file with priority assert", logFile.contains("[ASSERT] LogTest testLogPrintln " + now));
    }
}
