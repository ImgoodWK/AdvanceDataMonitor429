package com.imgood.textech.handler;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.webae.WebConsoleServer;

/**
 * WebAE HTTP server lifecycle: shutdown hook, restart, and status queries.
 * The server runs in-process (no separate console window); logs go to {@code logs/latest.log}.
 */
public final class WebAeServerHandler {

    private static WebConsoleServer server;
    private static volatile boolean running;
    private static Thread shutdownHook;

    private WebAeServerHandler() {}

    public static synchronized void setServer(WebConsoleServer srv) {
        server = srv;
    }

    public static synchronized WebConsoleServer getServer() {
        return server;
    }

    public static synchronized boolean isRunning() {
        return running && server != null;
    }

    public static synchronized void startIfEnabled() {
        if (!Config.webConsoleEnabled) {
            AdvanceDataMonitor.LOG.info("[WebAE] Web Console is disabled in config.");
            return;
        }
        if (server == null) {
            server = new WebConsoleServer();
        }
        if (running) {
            AdvanceDataMonitor.LOG.info(
                "[WebAE] HTTP server already running on {}:{}",
                Config.webConsoleBindAddress,
                Config.webConsolePort);
            return;
        }
        registerShutdownHook();
        server.startServer();
        running = true;
        AdvanceDataMonitor.LOG.info(
            "[WebAE] Web console ready — open http://{}:{} in your browser (in-game: /admweb issue)",
            Config.webConsoleBindAddress,
            Config.webConsolePort);
    }

    public static synchronized void stopServer() {
        running = false;
        if (server != null) {
            try {
                server.stopServer();
            } catch (Throwable t) {
                AdvanceDataMonitor.LOG.warn("[WebAE] Error stopping HTTP server", t);
            }
            server = null;
        }
        unregisterShutdownHook();
    }

    /** Stop and start the in-process HTTP server (OP command). */
    public static synchronized boolean restartServer() {
        if (!Config.webConsoleEnabled) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Cannot restart: web console disabled in config.");
            return false;
        }
        AdvanceDataMonitor.LOG.info("[WebAE] Restarting HTTP server...");
        running = false;
        if (server != null) {
            try {
                server.stopServer();
            } catch (Throwable t) {
                AdvanceDataMonitor.LOG.warn("[WebAE] Error during restart stop", t);
            }
        }
        server = new WebConsoleServer();
        registerShutdownHook();
        server.startServer();
        running = true;
        AdvanceDataMonitor.LOG
            .info("[WebAE] HTTP server restarted on {}:{}", Config.webConsoleBindAddress, Config.webConsolePort);
        return true;
    }

    private static void registerShutdownHook() {
        if (shutdownHook != null) return;
        shutdownHook = new Thread(new Runnable() {

            @Override
            public void run() {
                AdvanceDataMonitor.LOG.info("[WebAE] JVM shutdown hook — stopping HTTP server.");
                stopServer();
            }
        }, "WebAE-ShutdownHook");
        try {
            Runtime.getRuntime()
                .addShutdownHook(shutdownHook);
        } catch (IllegalStateException e) {
            // Shutdown already in progress — stop directly.
            stopServer();
        }
    }

    private static void unregisterShutdownHook() {
        if (shutdownHook == null) return;
        try {
            Runtime.getRuntime()
                .removeShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {
            // JVM is shutting down.
        }
        shutdownHook = null;
    }
}
