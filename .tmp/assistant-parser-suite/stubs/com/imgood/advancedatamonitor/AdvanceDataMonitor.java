package com.imgood.advancedatamonitor;
public final class AdvanceDataMonitor {
    public static final Logger LOG = new Logger();
    public static final class Logger {
        public void error(String message, Throwable throwable) {}
        public void warn(String message, Throwable throwable) {}
    }
}