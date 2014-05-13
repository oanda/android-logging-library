package com.logging;

import android.content.Context;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberInputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * <p>A wrapper class for the android.util.Log class. Intercepts all calls to android.util.Log
 * functions, records the logged information in a the file system on the device, and passes
 * it on to android.util.Log. The file acts as a circular buffer, keeping it from consuming
 * too much space on the file system.</p>
 *
 * <p>Note: If an entry contains more lines than Log.CIRCULAR_BUFFER_SIZE, then it is possible that
 * the entry will eventually be split up, since the circular buffer implemented in this class limits
 * the number of lines in the log file, rather than the number of entries.</p>
 *
 * API for sending log output.
 *
 * <p>Generally, use the Log.v() Log.d() Log.i() Log.w() and Log.e()
 * methods.
 *
 * <p>The order in terms of verbosity, from least to most is
 * ERROR, WARN, INFO, DEBUG, VERBOSE.  Verbose should never be compiled
 * into an application except during development.  Debug logs are compiled
 * in but stripped at runtime.  Error, warning and info logs are always kept.
 *
 * <p><b>Tip:</b> A good convention is to declare a <code>TAG</code> constant
 * in your class:
 *
 * <pre>private static final String TAG = "MyActivity";</pre>
 *
 * and use that in subsequent calls to the log methods.
 * </p>
 *
 * <p><b>Tip:</b> Don't forget that when you make a call like
 * <pre>Log.v(TAG, "index=" + i);</pre>
 * that when you're building the string to pass into Log.d, the compiler uses a
 * StringBuilder and at least three allocations occur: the StringBuilder
 * itself, the buffer, and the String object.  Realistically, there is also
 * another buffer allocation and copy, and even more pressure on the gc.
 * That means that if your log message is filtered out, you might be doing
 * significant work and incurring significant overhead.
 */
public class Log {

    /**
     * Priority constant for the println method; use Log.v.
     */
    public static final int VERBOSE = 2;

    /**
     * Priority constant for the println method; use Log.d.
     */
    public static final int DEBUG = 3;

    /**
     * Priority constant for the println method; use Log.i.
     */
    public static final int INFO = 4;

    /**
     * Priority constant for the println method; use Log.w.
     */
    public static final int WARN = 5;

    /**
     * Priority constant for the println method; use Log.e.
     */
    public static final int ERROR = 6;

    /**
     * Priority constant for the println method.
     */
    public static final int ASSERT = 7;

    /**
     * The size of the chunk that will be removed whenever the number of lines in the file reaches
     * {@link #CIRCULAR_BUFFER_SIZE}.
     */
    protected static final int NUM_LINES_PER_CHUNK = 100;

    /**
     * The number of entries to store in the file before removing a chunk.
     */
    protected static final int CIRCULAR_BUFFER_SIZE = 500;

    /**
     * The name of the log file.
     */
    protected static final String FILENAME = "fxTrade_log";

    /**
     * The name of the temporary file to use while removing a "chunk".
     */
    protected static final String TEMP_FILENAME = FILENAME + "_temp";

    /**
     * A context that provides access to the file system. Will most commonly be a reference to the
     * application's main activity.
     */
    private static Context mContext;

    /**
     * A SimpleDateFormat object used to create a timestamp for each entry.
     */
    private static final SimpleDateFormat mSimpleDateFormat = new SimpleDateFormat("MM-dd kk:mm:ss.SSS");

    /**
     * Keeps track of the number of entries in the log file
     */
    private static int mNumLines;

    /**
     * Whether or not the init method has been successfully called.
     */
    private static boolean mInitialized;

    /**
     * Initialize Log for use. This function must be called before calls to any other function are
     * made. If this function is not called first, then the functions will simply call the
     * corresponding function in android.util.Log, and nothing will be written to the log file.
     *
     * Calling this function also trims the log file down to the correct size if, for some reason,
     * it has exceeded its maximum buffer size.
     *
     * @param context A context that provides access to the file system. A reference to the app's
     *                main activity will do.
     * @return A boolean representing the success of the initialization
     */
    public static boolean init(Context context) {
        if (context == null) {
            return (mInitialized = false);
        }

        mContext = context;
        mNumLines = 0;

        try {
            // Open the log file just to read the number of lines
            LineNumberInputStream lineNumberInputStream = new LineNumberInputStream(getInputStream(FILENAME));

            // Count the number of lines in the file
            while (lineNumberInputStream.read() > 0);
            lineNumberInputStream.close();

            mNumLines = lineNumberInputStream.getLineNumber();

            // If we exceed CIRCULAR_BUFFER_SIZE, trim it down to be below CIRCULAR_BUFFER_SIZE
            if (mNumLines > CIRCULAR_BUFFER_SIZE) {
                // Determine how many extra lines we have
                int diff = mNumLines - CIRCULAR_BUFFER_SIZE;

                // Remove chunks until the difference is <= 0
                for (; diff > 0; diff -= NUM_LINES_PER_CHUNK) {
                    removeChunk();
                }
            }
        } catch (IOException ioException) {
            // We shouldn't do anything here, since the IOException gives no feedback on whether we
            // could access the file system - it only tells us "FileNotFound".
        }

        return (mInitialized = true);
    }

    /**
     * Send a {@link #VERBOSE} log message.
     *
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static int v(String tag, String msg) {
        if (mInitialized) {
            writeToFile(VERBOSE, tag, msg);
        }
        return android.util.Log.v(tag, msg);
    }

    /**
     * Send a {@link #VERBOSE} log message and log the exception.
     *
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr An exception to log
     */
    public static int v(String tag, String msg, Throwable tr) {
        if (mInitialized) {
            writeToFile(VERBOSE, tag, msg, tr);
        }
        return android.util.Log.v(tag, msg, tr);
    }

    /**
     * Send a {@link #DEBUG} log message.
     *
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static int d(String tag, String msg) {
        if (mInitialized) {
            writeToFile(DEBUG, tag, msg);
        }
        return android.util.Log.d(tag, msg);
    }

    /**
     * Send a {@link #DEBUG} log message and log the exception.
     *
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr An exception to log
     */
    public static int d(String tag, String msg, Throwable tr) {
        if (mInitialized) {
            writeToFile(DEBUG, tag, msg, tr);
        }
        return android.util.Log.d(tag, msg, tr);
    }

    /**
     * Send an {@link #INFO} log message.
     *
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static int i(String tag, String msg) {
        if (mInitialized) {
            writeToFile(INFO, tag, msg);
        }
        return android.util.Log.i(tag, msg);
    }

    /**
     * Send a {@link #INFO} log message and log the exception.
     *
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr An exception to log
     */
    public static int i(String tag, String msg, Throwable tr) {
        if (mInitialized) {
            writeToFile(INFO, tag, msg, tr);
        }
        return android.util.Log.i(tag, msg, tr);
    }

    /**
     * Send a {@link #WARN} log message.
     *
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static int w(String tag, String msg) {
        if (mInitialized) {
            writeToFile(WARN, tag, msg);
        }
        return android.util.Log.w(tag, msg);
    }

    /**
     * Send a {@link #WARN} log message and log the exception.
     *
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr An exception to log
     */
    public static int w(String tag, String msg, Throwable tr) {
        if (mInitialized) {
            writeToFile(WARN, tag, msg, tr);
        }
        return android.util.Log.w(tag, msg, tr);
    }

    /**
     * Send a {@link #WARN} log message and log the exception.
     *
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param tr An exception to log
     */
    public static int w(String tag, Throwable tr) {
        if (mInitialized) {
            writeToFile(WARN, tag, tr);
        }
        return android.util.Log.w(tag, tr);
    }

    /**
     * Send an {@link #ERROR} log message.
     *
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static int e(String tag, String msg) {
        if (mInitialized) {
            writeToFile(ERROR, tag, msg);
        }
        return android.util.Log.e(tag, msg);
    }

    /**
     * Send a {@link #ERROR} log message and log the exception.
     *
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr An exception to log
     */
    public static int e(String tag, String msg, Throwable tr) {
        if (mInitialized) {
            writeToFile(ERROR, tag, msg, tr);
        }
        return android.util.Log.e(tag, msg, tr);
    }

    /**
     * Checks to see whether or not a log for the specified tag is loggable at the specified level.
     * <p/>
     * The default level of any tag is set to INFO. This means that any level above and including
     * INFO will be logged. Before you make any calls to a logging method you should check to see
     * if your tag should be logged. You can change the default level by setting a system property:
     * 'setprop log.tag.&lt;YOUR_LOG_TAG> &lt;LEVEL>'
     * Where level is either VERBOSE, DEBUG, INFO, WARN, ERROR, ASSERT, or SUPPRESS. SUPPRESS will
     * turn off all logging for your tag. You can also create a local.prop file that with the
     * following in it:
     * 'log.tag.&lt;YOUR_LOG_TAG>=&lt;LEVEL>'
     * and place that in /data/local.prop.
     *
     * @param tag   The tag to check.
     * @param level The level to check.
     * @return Whether or not that this is allowed to be logged.
     * @throws IllegalArgumentException is thrown if the tag.length() > 23.
     */
    public static boolean isLoggable(String tag, int level) throws IllegalArgumentException {
        return android.util.Log.isLoggable(tag, level);
    }

    /**
     * Handy function to get a loggable stack trace from a Throwable
     *
     * @param tr An exception to log
     */
    public static String getStackTraceString(Throwable tr) {
        return android.util.Log.getStackTraceString(tr);
    }

    /**
     * Low-level logging call.
     *
     * @param priority The priority/type of this log message
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @return The number of bytes written.
     */
    public static int println(int priority, String tag, String msg) {
        if (mInitialized) {
            writeToFile(priority, tag, msg);
        }
        return android.util.Log.println(priority, tag, msg);
    }

    /**
     * Gets a String representation of the Log file.
     *
     * @return The contents of the log file as a String, null if the log file could not be opened.
     */
    public static String getLogFile() {
        StringBuilder sb = new StringBuilder();

        try {
            // Read the entire file and append it to the StringBuilder
            BufferedReader bufferedReader = getBufferedReader(FILENAME);

            String currentLine;
            // Step through the file line by line and add each line to the StringBuilder
            while ((currentLine = bufferedReader.readLine()) != null) {
                sb.append(currentLine).append('\n');
            }

            bufferedReader.close();
        } catch (IOException ioException) {
            // If there was a failure in reading the log file
            return null;
        }

        // Return the built String
        return sb.toString();
    }

    /**
     * Clears the log file.
     */
    public static void clearLogFile() {
        if (mContext != null) {
            mContext.deleteFile(FILENAME);

            try {
                // Re-create the file, but leave it empty
                BufferedWriter bufferedWriter = getBufferedWriter(FILENAME);
                bufferedWriter.close();
            } catch (IOException ioException) {
                // Do nothing here because if there was an error in re-creating the file, then there's
                // nothing we can do
            }
        }
    }

    /**
     * Handles the writing of the logged entry to the log file.
     *
     * @param priority The priority/type of this log message.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    private static void writeToFile(int priority, String tag, String msg) {
        writeToFile(priority, tag, msg, null);
    }

    /**
     * Handles the writing of the logged entry to the log file.
     *
     * @param priority The priority/type of this log message.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param tr An exception to log.
     */
    private static void writeToFile(int priority, String tag, Throwable tr) {
        writeToFile(priority, tag, null, tr);
    }

    /**
     * Handles the writing of the logged entry to the log file.
     *
     * @param priority The priority/type of this log message.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr An exception to log.
     */
    private static void writeToFile(int priority, String tag, String msg, Throwable tr) {
        try {
            // If we've been provided with a context and we've successfully initialized
            if (mContext != null && mInitialized) {
                // Append all the information we have
                String currentEntry = buildEntry(priority, tag, msg, tr);

                // If we've exceeded CIRCULAR_BUFFER_SIZE, then remove a chunk from the
                // beginning of the file
                if (mNumLines >= CIRCULAR_BUFFER_SIZE) {
                    removeChunk();
                }

                // Open the file to write to
                // Will create a file if it's not found
                BufferedWriter bufferedWriter = getBufferedWriter(FILENAME);
                // Append to the end of the existing file
                bufferedWriter.append(currentEntry);
                bufferedWriter.newLine();
                bufferedWriter.close();

                // Find the number of occurrences of "\n" in the current entry
                int count = 0;
                for (int i = 0; i < currentEntry.length(); i++) {
                    if (currentEntry.charAt(i) == '\n') {
                        count++;
                    }
                }

                // Increment the number of lines in this file by the number of lines in the entry
                // plus 1 for the new line appended to bufferedWriter
                mNumLines += count + 1;
            }
        } catch (IOException ioException) {
            // We've already made sure that init() was successful, which requires the log
            // file to be opened, so we can ignore this exception.
            // Might want to log it with android.util.Log
        }
    }

    /**
     * Removes NUM_LINES_PER_CHUNK lines from the beginning of the file specified by FILENAME.
     * To do this, a temporary file is created and deleted. This function also keeps mNumLines
     * accurate by subtracting NUM_LINES_PER_CHUNK from it.
     */
    private static void removeChunk() {

        boolean eofEarly = false;
        try {
            // Create a BufferedReader to read the existing file, and a BufferedWriter to write to
            // a temporary file
            BufferedReader bufferedReader = getBufferedReader(FILENAME);

            // Read and discard the first NUM_LINES_PER_CHUNK lines
            for (int i = 0; i < NUM_LINES_PER_CHUNK; i++) {
                // If we get to the end of the file while still discarding lines
                if (bufferedReader.readLine() == null) {
                    // Don't do any more reading/writing operations, just delete the whole file
                    eofEarly = true;
                    bufferedReader.close();
                }
            }

            if (!eofEarly) {
                BufferedWriter bufferedWriter = getBufferedWriter(TEMP_FILENAME);

                String currentLine;
                // Step through the rest of the file and write each line to the temporary file
                while ((currentLine = bufferedReader.readLine()) != null) {
                    bufferedWriter.write(currentLine);
                    bufferedWriter.newLine();
                }

                // Close the reader and writer
                bufferedReader.close();
                bufferedWriter.close();
            }
        } catch (IOException ioException) {
            // Do nothing here, since we can't do anything if we fail in reading from the files
        }

        // Create a File representation of the temp file and the permanent file
        File tempFile = mContext.getFileStreamPath(TEMP_FILENAME);
        File permanentFile = new File(tempFile.getParent(), FILENAME);
        // Delete the permanent file
        if (permanentFile.exists()) {
            mContext.deleteFile(FILENAME);
        }
        // Rename the temp file to the permanent file
        tempFile.renameTo(permanentFile);

        // We just removed NUM_LINES_PER_CHUNK lines
        mNumLines -= NUM_LINES_PER_CHUNK;

        // Prevent the number of entries from going negative
        if (mNumLines < 0) {
            mNumLines = 0;
        }
    }

    /**
     * Create an entry from the information passed to one of the Log functions.
     *
     * @param priority The priority/type of this log message.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr An exception to log.
     * @return A String representing the entry.
     */
    private static String buildEntry(int priority, String tag, String msg, Throwable tr) {
        // Create a StringBuilder and append our information to it, if applicable
        StringBuilder sb = new StringBuilder();

        long now = System.currentTimeMillis();

        // Append each piece of information
        sb.append(mSimpleDateFormat.format(new Date(now)));

        // Append the priority
        sb.append(" [");
        switch (priority) {
            case VERBOSE:
                sb.append("VERBOSE");
                break;
            case DEBUG:
                sb.append("DEBUG");
                break;
            case INFO:
                sb.append("INFO");
                break;
            default:
            case WARN:
                sb.append("WARNING");
                break;
            case ERROR:
                sb.append("ERROR");
                break;
            case ASSERT:
                sb.append("ASSERT");
                break;
        }
        sb.append(']');

        // Append the tag, message and throwable
        if (tag != null) {
            sb.append(' ').append(tag);
        }
        if (msg != null) {
            sb.append(' ').append(msg);
        }
        if (tr != null) {
            // As per android.util.Log format
            sb.append('\n').append(getStackTraceString(tr));
        }

        return sb.toString();
    }

    /**
     * Get a FileInputStream to represent the specified file.
     *
     * @param fileName The name of the file to open.
     * @return A FileInputStream representing the file.
     * @throws FileNotFoundException If the file could not be opened.
     */
    private static FileInputStream getInputStream(String fileName) throws FileNotFoundException {
        return mContext.openFileInput(fileName);
    }

    /**
     * Get a BufferedReader to represent the specified file.
     *
     * @param fileName The name of the file to open.
     * @return A BufferedReader representing the file.
     * @throws FileNotFoundException If the file could not be opened.
     */
    private static BufferedReader getBufferedReader(String fileName) throws FileNotFoundException {
        return new BufferedReader(new InputStreamReader(getInputStream(fileName)));
    }

    /**
     * Get a FileOutputStream to represent the specified file.
     *
     * @param fileName The name of the file to open.
     * @return A FileOutputStream representing the file.
     * @throws FileNotFoundException If the file could not be opened.
     */
    private static FileOutputStream getOutputStream(String fileName) throws FileNotFoundException {
        return mContext.openFileOutput(fileName, Context.MODE_APPEND);
    }

    /**
     * Get a BufferedWriter to represent the specified file.
     *
     * @param fileName The name of the file to open.
     * @return A BufferedWriter representing the file.
     * @throws FileNotFoundException If the file could not be opened.
     */
    private static BufferedWriter getBufferedWriter(String fileName) throws FileNotFoundException {
        return new BufferedWriter(new PrintWriter(getOutputStream(fileName)));
    }
}
