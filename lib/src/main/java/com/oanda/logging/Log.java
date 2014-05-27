package com.oanda.logging;

import android.content.Context;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * <p>A wrapper class for the android.util.Log class. Intercepts all calls to android.util.Log
 * functions, records the logged information in a the file system on the device, and passes
 * it on to android.util.Log. The file acts as a circular buffer, keeping it from consuming
 * too much space on the file system.</p>
 * <p/>
 * <p>Note: If an entry contains more lines than Log.CIRCULAR_BUFFER_SIZE, then it is possible that
 * the entry will eventually be split up, since the circular buffer implemented in this class limits
 * the number of lines in the log file, rather than the number of entries.</p>
 * <p>Whenever a Log request is made, it gets added to a buffer that gets appended to the log file
 * on a dedicated thread. When readLog() is called, it must wait for the read/write lock to be
 * released by the write thread. This means that these calls will be delayed until the write thread
 * completes its current write job.</p>
 * <p>All writing to the file system is done on a separate thread than the one that invoked the
 * Log, which means that the writing will not be completed immediately when the function returns;
 * it will finish some time afterwards.</p>
 * <b>From anroid.util.Log:</b>
 * <p/>
 * API for sending log output.
 * <p/>
 * <p>Generally, use the Log.v() Log.d() Log.i() Log.w() and Log.e()
 * methods.
 * <p/>
 * <p>The order in terms of verbosity, from least to most is
 * ERROR, WARN, INFO, DEBUG, VERBOSE.  Verbose should never be compiled
 * into an application except during development.  Debug logs are compiled
 * in but stripped at runtime.  Error, warning and info logs are always kept.
 * <p/>
 * <p><b>Tip:</b> A good convention is to declare a <code>TAG</code> constant
 * in your class:
 * <p/>
 * <pre>private static final String TAG = "MyActivity";</pre>
 * <p/>
 * and use that in subsequent calls to the log methods.
 * </p>
 * <p/>
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
     * The target number of entries to store in the file after the file has been trimmed
     */
    static final int CIRCULAR_BUFFER_SIZE = 500;

    /**
     * The name of the log file.
     */
    static final String FILENAME = "fxtrade_log.txt";

    /**
     * The name of the temporary file to use while removing a "chunk".
     */
    static final String TEMP_FILENAME = '~' + FILENAME;

    /**
     * The system's newline String
     */
    private static final String mNewLine = System.getProperty("line.separator");

    /**
     * A SimpleDateFormat object used to create a timestamp for each entry.
     */
    private static final SimpleDateFormat mSimpleDateFormat =
            new SimpleDateFormat("MM-dd kk:mm:ss.SSS", Locale.US);

    /**
     * The lock for file I/O operations
     */
    private static ReentrantLock mFileLock;

    /**
     * The queue of entries that will be written to file by the write thread
     */
    private static ConcurrentLinkedQueue<Entry> mEntryQueue;

    /**
     * Used to signal that the write thread has ended
     */
    private static AtomicBoolean mWriteThreadRunning;

    /**
     * Used to signal to the write thread that a clear request has been made. The clear request
     * should stop the write thread from polling for more entries from the queue and should not
     * write the entries that have already been polled.
     */
    private static AtomicBoolean mRequestedClearLog;

    /**
     * Context that provides access to the file system. Should be a reference to the
     * application's main activity.
     */
    private static Context mContext;

    /**
     * The dedicated thread for writing new entries to the log file.
     */
    private static WriteThread mWriteThread;

    /**
     * Whether or not the init method has been successfully called.
     */
    private static boolean mInitialized;

    /**
     * Avoid instances and subclasses of Log
     */
    private Log() {
    }

    /**
     * Initialize Log for use. This function must be called before calls to any other function are
     * made. If this function is not called first, then the functions will simply call the
     * corresponding function in android.util.Log, and nothing will be written to the log.
     * <p/>
     * Calling this function also trims the log down to the correct size if, for some reason,
     * it has exceeded its maximum buffer size.
     *
     * @param context A context that provides access to the file system. A reference to the app's
     *                main activity will do.
     * @return A boolean representing the success of the initialization
     */
    public static synchronized boolean init(Context context) {
        // Destroy the previously initialized Log to ensure that we have new instances
        destroy();

        if (context == null) {
            return false;
        }

        mFileLock = new ReentrantLock();
        mEntryQueue = new ConcurrentLinkedQueue<Entry>();
        mWriteThreadRunning = new AtomicBoolean(false);
        mRequestedClearLog = new AtomicBoolean(false);
        mContext = context;

        mInitialized = true;

        // The first action once initialized must be to ensure that the file is the correct length
        mFileLock.lock();
        try {
            // Make sure the file is the correct length
            trimFileToSize();
        } finally {
            mFileLock.unlock();
        }

        return mInitialized;
    }

    /**
     * This method removes all pointers to member objects to free them up for garbage collection.
     * Also stops the write thread if running.
     */
    static void destroy() {
        mFileLock = null;
        mEntryQueue = null;
        mWriteThreadRunning = null;
        mRequestedClearLog = null;
        mContext = null;
        mInitialized = false;

        if (mWriteThread != null) {
            mWriteThread.interrupt();
        }
        mWriteThread = null;
    }

    /**
     * Send a {@link #VERBOSE} log message.
     *
     * @param tag Used to identify the source of a log message.  It usually identifies
     *            the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static int v(String tag, String msg) {
        int ret = android.util.Log.v(tag, msg);
        if (mInitialized) {
            addEntryToStack(VERBOSE, tag, msg);
        }
        return ret;
    }

    /**
     * Send a {@link #VERBOSE} log message and log the exception.
     *
     * @param tag Used to identify the source of a log message.  It usually identifies
     *            the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr  An exception to log
     */
    public static int v(String tag, String msg, Throwable tr) {
        int ret = android.util.Log.v(tag, msg, tr);
        if (mInitialized) {
            addEntryToStack(VERBOSE, tag, msg, tr);
        }
        return ret;
    }

    /**
     * Send a {@link #DEBUG} log message.
     *
     * @param tag Used to identify the source of a log message.  It usually identifies
     *            the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static int d(String tag, String msg) {
        int ret = android.util.Log.d(tag, msg);
        if (mInitialized) {
            addEntryToStack(DEBUG, tag, msg);
        }
        return ret;
    }

    /**
     * Send a {@link #DEBUG} log message and log the exception.
     *
     * @param tag Used to identify the source of a log message.  It usually identifies
     *            the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr  An exception to log
     */
    public static int d(String tag, String msg, Throwable tr) {
        int ret = android.util.Log.d(tag, msg, tr);
        if (mInitialized) {
            addEntryToStack(DEBUG, tag, msg, tr);
        }
        return ret;
    }

    /**
     * Send an {@link #INFO} log message.
     *
     * @param tag Used to identify the source of a log message.  It usually identifies
     *            the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static int i(String tag, String msg) {
        int ret = android.util.Log.i(tag, msg);
        if (mInitialized) {
            addEntryToStack(INFO, tag, msg);
        }
        return ret;
    }

    /**
     * Send a {@link #INFO} log message and log the exception.
     *
     * @param tag Used to identify the source of a log message.  It usually identifies
     *            the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr  An exception to log
     */
    public static int i(String tag, String msg, Throwable tr) {
        int ret = android.util.Log.i(tag, msg, tr);
        if (mInitialized) {
            addEntryToStack(INFO, tag, msg, tr);
        }
        return ret;
    }

    /**
     * Send a {@link #WARN} log message.
     *
     * @param tag Used to identify the source of a log message.  It usually identifies
     *            the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static int w(String tag, String msg) {
        int ret = android.util.Log.w(tag, msg);
        if (mInitialized) {
            addEntryToStack(WARN, tag, msg);
        }
        return ret;
    }

    /**
     * Send a {@link #WARN} log message and log the exception.
     *
     * @param tag Used to identify the source of a log message.  It usually identifies
     *            the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr  An exception to log
     */
    public static int w(String tag, String msg, Throwable tr) {
        int ret = android.util.Log.w(tag, msg, tr);
        if (mInitialized) {
            addEntryToStack(WARN, tag, msg, tr);
        }
        return ret;
    }

    /**
     * Send a {@link #WARN} log message and log the exception.
     *
     * @param tag Used to identify the source of a log message.  It usually identifies
     *            the class or activity where the log call occurs.
     * @param tr  An exception to log
     */
    public static int w(String tag, Throwable tr) {
        int ret = android.util.Log.w(tag, tr);
        if (mInitialized) {
            addEntryToStack(WARN, tag, tr);
        }
        return ret;
    }

    /**
     * Send an {@link #ERROR} log message.
     *
     * @param tag Used to identify the source of a log message.  It usually identifies
     *            the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static int e(String tag, String msg) {
        int ret = android.util.Log.e(tag, msg);
        if (mInitialized) {
            addEntryToStack(ERROR, tag, msg);
        }
        return ret;
    }

    /**
     * Send a {@link #ERROR} log message and log the exception.
     *
     * @param tag Used to identify the source of a log message.  It usually identifies
     *            the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr  An exception to log
     */
    public static int e(String tag, String msg, Throwable tr) {
        int ret = android.util.Log.e(tag, msg, tr);
        if (mInitialized) {
            addEntryToStack(ERROR, tag, msg, tr);
        }
        return ret;
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
     * @param tag      Used to identify the source of a log message.  It usually identifies
     *                 the class or activity where the log call occurs.
     * @param msg      The message you would like logged.
     * @return The number of bytes written.
     */
    public static int println(int priority, String tag, String msg) {
        int ret = android.util.Log.println(priority, tag, msg);
        if (mInitialized) {
            addEntryToStack(priority, tag, msg);
        }
        return ret;
    }

    /**
     * Get a reference to the log as a File.
     *
     * @return A reference to the log as a File.
     */
    public static File getLogFile() {
        if (mInitialized) {
            // Return a File representing the FILENAME in the getFileStreamPath() directory
            return mContext.getFileStreamPath(FILENAME);
        } else {
            return null;
        }
    }

    /**
     * Gets a String representation of the log.
     *
     * @return The contents of the log as a String. If an IOException occurs, returns what could be
     * read, plus the text from the IOException. If Log.init has not been called, returns an empty
     * String.
     */
    public static String readLog() {
        if (mInitialized) {
            // Give the StringBuilder an approximate size of the file
            StringBuilder stringBuilder = new StringBuilder(CIRCULAR_BUFFER_SIZE * Entry.APPROXIMATE_LENGTH_PER_ENTRY);

            mFileLock.lock();
            try {
                try {
                    // Read the entire file and append it to the StringBuilder
                    BufferedReader bufferedReader = getBufferedReader(FILENAME);

                    String currentLine;
                    // Step through the file line by line and add each line to the StringBuilder
                    while ((currentLine = bufferedReader.readLine()) != null) {
                        stringBuilder.append(currentLine).append(mNewLine);
                    }

                    bufferedReader.close();
                } catch (IOException ioException) {
                    // If there was a failure in reading the log file
                    // Return what we have so far plus the stack trace
                    return stringBuilder.append(getStackTraceString(ioException)).toString();
                }
            } finally {
                mFileLock.unlock();
            }

            // Return the built String
            return stringBuilder.toString();
        } else {
            // Return and empty String if Log is not initialized
            return "";
        }
    }

    /**
     * Clears the log.
     *
     * @return True if the request to clear the log was processed, false otherwise (if Log.init()
     * wasn't called)
     */
    public static boolean clearLog() {
        if (mInitialized) {
            // Make mRequestedClearLog true while we clear the queue. This causes the write thread to
            // cancel writing what it has received from the queue.
            mRequestedClearLog.set(true);
            mEntryQueue.clear();

            // Start the write thread if it's not already started
            startWriteThread();
        }

        return mInitialized;
    }

    /**
     * This method will block the current thread until the writing thread has caught up by clearing
     * its buffer of entries to add. NOTE: this method waits until the write thread has been killed,
     * which does not happen until some time after the last write operation. This is because the
     * write thread has a timeout time that prevents the creation of a new thread for a Log call
     * that might happen a short time later.
     */
    static void waitUntilFinishedWriting() {
        if (mInitialized) {
            if (mWriteThread != null && mWriteThreadRunning.get()) {
                try {
                    mWriteThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Get a Reader to represent the specified file.
     *
     * @param fileName The name of the file to open.
     * @return A Reader representing the file.
     * @throws FileNotFoundException If the file could not be opened or if Log is not initialized
     */
    static Reader getReader(String fileName) throws FileNotFoundException {
        if (mInitialized) {
            return new InputStreamReader(mContext.openFileInput(fileName));
        } else {
            throw new FileNotFoundException("Log not initialized");
        }
    }

    /**
     * Get a BufferedReader to represent the specified file.
     *
     * @param fileName The name of the file to open.
     * @return A BufferedReader representing the file.
     * @throws FileNotFoundException If the file could not be opened or if Log is not initialized
     */
    private static BufferedReader getBufferedReader(String fileName) throws FileNotFoundException {
        return new BufferedReader(getReader(fileName));
    }

    /**
     * Get a Writer to represent the specified file.
     *
     * @param fileName The name of the file to open.
     * @return A Writer representing the file.
     * @throws FileNotFoundException If the file could not be opened or if Log is not initialized
     */
    static Writer getWriter(String fileName) throws FileNotFoundException {
        if (mInitialized) {
            return new OutputStreamWriter(mContext.openFileOutput(fileName, Context.MODE_APPEND));
        } else {
            throw new FileNotFoundException("Log not initialized");
        }
    }

    /**
     * Get a BufferedWriter to represent the specified file.
     *
     * @param fileName The name of the file to open.
     * @return A BufferedWriter representing the file.
     * @throws FileNotFoundException If the file could not be opened or if Log is not initialized
     */
    private static BufferedWriter getBufferedWriter(String fileName) throws FileNotFoundException {
        return new BufferedWriter(getWriter(fileName));
    }

    /**
     * This method starts the write thread if it is stopped. This method should be called if the
     * write thread needs to be started to execute the items that have been queued up for it.
     */
    private static void startWriteThread() {
        // If the executor has not been created yet or if it has been terminated
        if (mWriteThread == null || !mWriteThread.isAlive()) {
            // Wait until we have the lock to prevent multiple threads from running this at the same time
            mFileLock.lock();
            try {
                // Check again after we acquire the lock
                if (mWriteThread == null || !mWriteThread.isAlive()) {
                    mWriteThreadRunning.set(true);
                    mWriteThread = new WriteThread();
                    mWriteThread.start();
                }
            } finally {
                mFileLock.unlock();
            }
        }
    }

    /**
     * Handles adding new entries to the stack to be written to the log file by the write thread.
     *
     * @param priority The priority/type of this log message.
     * @param tag      Used to identify the source of a log message.  It usually identifies
     *                 the class or activity where the log call occurs.
     * @param msg      The message you would like logged.
     */
    private static void addEntryToStack(int priority, String tag, String msg) {
        addEntryToStack(priority, tag, msg, null);
    }

    /**
     * Handles adding new entries to the stack to be written to the log file by the write thread.
     *
     * @param priority The priority/type of this log message.
     * @param tag      Used to identify the source of a log message.  It usually identifies
     *                 the class or activity where the log call occurs.
     * @param tr       An exception to log.
     */
    private static void addEntryToStack(int priority, String tag, Throwable tr) {
        addEntryToStack(priority, tag, null, tr);
    }

    /**
     * Handles adding new entries to the stack to be written to the log file by the write thread.
     *
     * @param priority The priority/type of this log message.
     * @param tag      Used to identify the source of a log message.  It usually identifies
     *                 the class or activity where the log call occurs.
     * @param msg      The message you would like logged.
     * @param tr       An exception to log.
     */
    private static void addEntryToStack(int priority, String tag, String msg, Throwable tr) {
        long now = System.currentTimeMillis();

        Entry currentEntry = new Entry(now, priority, tag, msg, tr);

        // Add the entry to the queue to be written
        mEntryQueue.add(currentEntry);

        // Start the write thread if it's not already started
        startWriteThread();
    }

    /**
     * This method handles writing new entries to the log file. This method must only be called from
     * a thread that has acquired the mFileLock.
     */
    private static void writeToFile(String currentEntries) {
        try {
            // If we've been provided with a context and we've successfully initialized
            if (mContext != null && mInitialized && !"".equals(currentEntries)) {
                // Open the file to write to
                // Will create a file if it's not found
                BufferedWriter bufferedWriter = getBufferedWriter(FILENAME);
                bufferedWriter.write(currentEntries);

                // We don't need a newLine() here because we're already appending a newline after
                // calling buildEntry()
                bufferedWriter.close();
            }
        } catch (IOException ioException) {
            // We've already made sure that init() was successful, which requires the log
            // file to be opened, so we can ignore this exception.
        }
    }

    /**
     * Handles trimming the log file to the correct size to keep the circular buffer intact. This
     * method must only be called from a thread that has acquired the mFileLock.
     */
    private static void trimFileToSize() {
        try {
            // Open the log file to read the number of lines
            LineNumberReader lineNumberReader = new LineNumberReader(getReader(FILENAME));

            // Skip by CIRCULAR_BUFFER_SIZE lines
            // While there's still stuff to skip
            while (lineNumberReader.skip(CIRCULAR_BUFFER_SIZE) > 0) ;

            // Get the number of lines
            int numLines = lineNumberReader.getLineNumber();
            lineNumberReader.close();

            // If we exceed CIRCULAR_BUFFER_SIZE, trim it down to be below CIRCULAR_BUFFER_SIZE
            if (numLines >= CIRCULAR_BUFFER_SIZE) {
                // Determine how many extra lines we have
                // Our target # of lines is 1 chunk less than CIRCULAR_BUFFER_SIZE
                int diff = numLines - CIRCULAR_BUFFER_SIZE;

                removeLines(diff);
            }
        } catch (IOException ioException) {
            // We shouldn't do anything here, since the IOException gives no feedback on whether
            // we could access the file system - it only tells us "FileNotFound". Thus we don't know
            // if the file does not exist or if we don't have access to the file system.
        }
    }

    /**
     * Removes numLines lines from the beginning of the file with the name FILENAME.
     * To do this, a temporary file is used.
     * <p/>
     * This method must only be called from a thread that has acquired the mFileLock.
     */
    private static void removeLines(int numLinesToRemove) {
        try {
            boolean eofEarly = false;

            // Create a BufferedReader to read the existing file, and a BufferedWriter to write to
            // a temporary file
            BufferedReader bufferedReader = getBufferedReader(FILENAME);

            // Read and discard the first numLines lines
            for (int i = 0; i < numLinesToRemove && !eofEarly; i++) {
                // If we get to the end of the file while still discarding lines
                if (bufferedReader.readLine() == null) {
                    // We reached the end of the file, so we won't need to read any lines later
                    // Don't do any more reading/writing operations, just delete the whole file
                    eofEarly = true;
                }
            }

            BufferedWriter bufferedWriter = getBufferedWriter(TEMP_FILENAME);

            // If we didn't reach the end of file when we were ignoring lines
            if (!eofEarly) {
                String currentLine;
                // Step through the rest of the file and write each line to the temporary file
                while ((currentLine = bufferedReader.readLine()) != null) {
                    bufferedWriter.write(currentLine);
                    bufferedWriter.newLine();
                }
            }

            // Close the writer and reader
            bufferedWriter.close();
            bufferedReader.close();
        } catch (IOException ioException) {
            // Do nothing here, since we can't do anything if we fail in reading from the files
        }

        // Create a File representation of the temp file and the permanent file
        File tempFile = mContext.getFileStreamPath(TEMP_FILENAME);
        String parent = tempFile.getParent();
        File permanentFile = new File(parent != null ? parent : "", FILENAME);

        mContext.deleteFile(FILENAME);
        // Rename the temp file to the permanent file
        tempFile.renameTo(permanentFile);
    }

    /**
     * This method performs the file operations to clear the log file. This method must only be
     * called from a thread that has acquired the mFileLock.
     */
    private static void clearFile() {
        mContext.deleteFile(FILENAME);

        try {
            // Re-create the file, but leave it empty
            BufferedWriter bufferedWriter = getBufferedWriter(FILENAME);
            bufferedWriter.close();
        } catch (IOException ioException) {
            // Do nothing here because if there was an error in re-creating the file
            // then there's nothing we can do
        }
    }

    /**
     * Stores the information about each log entry so that a StringBuilder can build the entry later
     * (from the write thread). By doing this, it eliminates the use of StringBuilder on the
     * caller's thread.
     */
    private static final class Entry {

        static final int APPROXIMATE_LENGTH_PER_ENTRY = 50;

        private long timestamp;
        private int priority;
        private String tag;
        private String msg;
        private Throwable tr;

        public Entry(long timestamp, int priority, String tag, String msg, Throwable tr) {
            this.timestamp = timestamp;
            this.priority = priority;
            this.tag = tag;
            this.msg = msg;
            this.tr = tr;
        }

        /**
         * Append all of the information stored in this Entry to the StringBuilder parameter.
         *
         * @param stringBuilder The StringBuilder that all of the information contained
         *                      in this Entry will be appended to.
         */
        public void appendToStringBuilder(StringBuilder stringBuilder) {
            // Append each piece of information
            stringBuilder.append(mSimpleDateFormat.format(new Date(timestamp)));

            // Append the priority
            stringBuilder.append(" [");
            switch (priority) {
                case VERBOSE:
                    stringBuilder.append("VERBOSE");
                    break;
                case DEBUG:
                    stringBuilder.append("DEBUG");
                    break;
                case INFO:
                    stringBuilder.append("INFO");
                    break;
                default:
                case WARN:
                    stringBuilder.append("WARNING");
                    break;
                case ERROR:
                    stringBuilder.append("ERROR");
                    break;
                case ASSERT:
                    stringBuilder.append("ASSERT");
                    break;
            }
            stringBuilder.append(']');

            // Append the tag, message and throwable
            if (tag != null) {
                stringBuilder.append(' ').append(tag);
            }
            if (msg != null) {
                stringBuilder.append(' ').append(msg);
            }
            if (tr != null) {
                // As per android.util.Log format
                stringBuilder.append(mNewLine).append(getStackTraceString(tr));
            }
        }
    }

    /**
     * A separate thread to handle writing to the log file. When the thread has had no work to do
     * for THREAD_KEEP_ALIVE_MILLIS milliseconds, it kills itself.
     */
    private static final class WriteThread extends Thread {

        /**
         * The number of milliseconds after not receiving any new logs before the thread kills
         * itself. This will affect the amount of taken before waitUntilFinishedWriting() returns,
         * so this shouldn't be too long of a delay.
         */
        private static final long THREAD_KEEP_ALIVE_MILLIS = 1000;

        /**
         * The number of milliseconds between file trims to make sure that the file does not get
         * too big.
         */
        private static final long TRIM_FILE_MILLIS = 1000;

        /**
         * The amount of time to sleep for before polling for work to do if there is currently no
         * work to be done. The larger this value is, the more this thread will write in chunks
         */
        private static final long NO_WORK_SLEEP_MILLIS = 200;

        /**
         * The time, in terms of System.currentTimeMillis() that the thread started
         */
        private long mLastTrimTime = 0;

        /**
         * The time, in terms of System.currentTimeMillis() of the last write to the log file.
         */
        private long mLastWriteTime = 0;

        /**
         * Record the time that the thread starts for last trim and write time
         */
        @Override
        public synchronized void start() {
            super.start();

            mLastTrimTime = System.currentTimeMillis();

            // Initialize the time of the last action to be the start time, so that the time
            // difference in run() is < THREAD_KEEP_ALIVE_MILLIS
            mLastWriteTime = mLastTrimTime;
        }

        /**
         * The main method of the thread
         */
        @Override
        public void run() {
            // While we haven't been interrupted and we still want to keep this thread alive
            while (!isInterrupted() && mWriteThreadRunning.get()) {
                // Trim the file to size every TRIM_FILE_MILLIS while the thread runs
                if (System.currentTimeMillis() - mLastTrimTime > TRIM_FILE_MILLIS) {
                    trimFileToSize();
                    mLastTrimTime = System.currentTimeMillis();
                }

                if (mRequestedClearLog.get()) {
                    // We need to wait for the lock before we can clear the file
                    mFileLock.lock();
                    try {
                        // Clear the log file
                        clearFile();
                    } finally {
                        mFileLock.unlock();
                    }

                    // The clear request is done
                    mRequestedClearLog.set(false);
                } else if (!mEntryQueue.isEmpty()) {
                    // If we still have entries to write

                    // Give the StringBuilder an approximate size
                    StringBuilder stringBuilder = new StringBuilder(mEntryQueue.size() * Entry.APPROXIMATE_LENGTH_PER_ENTRY);
                    Entry currentEntry;

                    boolean requestedClearLog;
                    // If the log isn't requested to be cleared and we have more entries to take off
                    while (!(requestedClearLog = mRequestedClearLog.get()) &&
                            (currentEntry = mEntryQueue.poll()) != null) {
                        // Keep appending entries from the queue
                        currentEntry.appendToStringBuilder(stringBuilder);
                        stringBuilder.append(mNewLine);
                    }

                    // Write to the file as long as the polling ended successfully (didn't end due
                    // to a clear request). This way, there are no unexpected writes to a file that
                    // has just been cleared
                    if (!requestedClearLog) {
                        mFileLock.lock();
                        try {
                            // Write the entry to file
                            writeToFile(stringBuilder.toString());
                        } finally {
                            mFileLock.unlock();
                        }

                        mLastWriteTime = System.currentTimeMillis();
                    }
                } else if ((System.currentTimeMillis() - mLastWriteTime) >
                        THREAD_KEEP_ALIVE_MILLIS) {
                    // Make sure that when we finish writing to the file, it's the correct size
                    trimFileToSize();
                    // If we don't have anything to do on this thread and we timed out
                    mWriteThreadRunning.set(false);
                } else {
                    // Sleep for a little while so that when there is nothing to do we don't hog
                    // the CPU
                    try {
                        Thread.sleep(NO_WORK_SLEEP_MILLIS);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }

            // If the thread was interrupted, trim the file to size before terminating the thread
            if (isInterrupted()) {
                trimFileToSize();
            }
        }
    }
}
