package android.util;

/**
 * A mock class to do nothing for android.util.Log methods
 */
public class Log {

    public static int v(String tag, String msg) {
        return 0;
    }

    public static int v(String tag, String msg, Throwable tr) {
        return 0;
    }

    public static int d(String tag, String msg) {
        return 0;
    }

    public static int d(String tag, String msg, Throwable tr) {
        return 0;
    }

    public static int i(String tag, String msg) {
        return 0;
    }

    public static int i(String tag, String msg, Throwable tr) {
        return 0;
    }

    public static int w(String tag, String msg) {
        return 0;
    }

    public static int w(String tag, String msg, Throwable tr) {
        return 0;
    }

    public static boolean isLoggable(String tag, int level) {
        return false;
    }

    public static int w(String tag, Throwable tr) {
        return 0;
    }

    public static int e(String tag, String msg) {
        return 0;
    }

    public static int e(String tag, String msg, Throwable tr) {
        return 0;
    }

    public static String getStackTraceString(Throwable tr) {
        return "";
    }

    public static int println(int priority, String tag, String msg) {
        return 0;
    }
}
