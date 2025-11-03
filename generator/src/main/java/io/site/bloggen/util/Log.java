package io.site.bloggen.util;

public final class Log {
    private static volatile boolean verbose = false;
    private Log() {}
    public static void setVerbose(boolean v) { verbose = v; }
    public static boolean isVerbose() { return verbose; }
    public static void info(String msg) { System.out.println(msg); }
    public static void warn(String msg) { System.err.println("WARN: " + msg); }
    public static void error(String msg) { System.err.println("ERROR: " + msg); }
    public static void debug(String msg) { if (verbose) System.out.println(msg); }
}

