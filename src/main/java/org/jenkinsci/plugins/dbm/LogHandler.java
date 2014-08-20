package org.jenkinsci.plugins.dbm;

import hudson.model.TaskListener;

import java.net.UnknownHostException;

public class LogHandler {

    public static String CRITICAL = "FATAL ERROR";
    public static String ERROR = "ERROR";
    public static String WARNING = "WARNING";
    public static String SUCCESS = "SUCCESS";
    public static String INFO = "INFO";
    public static String DEBUG = "DEBUG";

    private static String generateStackString(final Exception e) {
        String stack = "";
        for (final StackTraceElement elem : e.getStackTrace()) {
            stack = stack.concat("* " + elem.getFileName() + "in " + elem.getClassName() + "(" + elem.getMethodName()
                    + ")" + "Line: " + elem.getLineNumber() + "\n");
        }
        return stack;
    }

    public static void printStackTrace(final String inClass, final String inMethod, final Exception e) {
        logPrint(null, inClass, inMethod, LogHandler.CRITICAL,
                e.getMessage() + "\n" + LogHandler.generateStackString(e));

        e.printStackTrace();
    }

    private static void logPrint(final TaskListener listener, final String inClass, final String inMethod,
            final String type, final String message) {
        LogHandler.logPrint(listener, inClass, inMethod, type, message, "");
    }

    private static void logPrint(final TaskListener listener, final String inClass, final String inMethod,
            final String type, final String message, final String threadID) {
        String stringToPrint = (threadID.isEmpty() ? "" : "<" + threadID + "> ") + "[" + type + "] ";

        if (!inClass.isEmpty() || !inMethod.isEmpty()) {
            stringToPrint = stringToPrint.concat(inClass + "-" + inMethod);
        }

        stringToPrint = stringToPrint.concat(": " + message);

        if (listener != null) {
            if (type.equals(LogHandler.ERROR)) {
                listener.error(stringToPrint);
            } else if (type.equals(LogHandler.CRITICAL)) {
                listener.fatalError(stringToPrint);
            } else {
                listener.getLogger().println(stringToPrint);
            }
        }
    }
}
