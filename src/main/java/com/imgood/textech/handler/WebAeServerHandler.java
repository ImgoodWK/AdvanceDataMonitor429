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
        running = srv != null && srv.isStarted();
    }

    public static synchronized WebConsoleServer getServer() {
        return server;
    }

    public static synchronized boolean isRunning() {
        return running && server != null && server.isStarted();
    }

    public static synchronized void startIfEnabled() {
        if (!Config.webConsoleEnabled) {
            AdvanceDataMonitor.LOG.info("[WebAE] Web Console is disabled in config.");
            return;
        }
        if (server == null) {
            server = new WebConsoleServer();
        }
        if (isRunning()) {
            AdvanceDataMonitor.LOG.info(
                "[WebAE] HTTP server already running on {}:{}",
                Config.webConsoleBindAddress,
                Config.webConsolePort);
            return;
        }

        WebConsoleServer serverToStart = server;
        if (!serverToStart.startServer()) {
            String failure = serverToStart.getStartFailure();
            running = false;
            server = null;
            unregisterShutdownHook();
            try {
                serverToStart.stopServer();
            } catch (Throwable t) {
                AdvanceDataMonitor.LOG.warn("[WebAE] Error cleaning failed HTTP server start", t);
            }
            stopIntegrations();
            AdvanceDataMonitor.LOG.error("[WebAE] Web console did not start: {}", failure);
            return;
        }

        running = true;
        if (!registerShutdownHook()) {
            stopServer();
            return;
        }
        com.imgood.textech.webae.qqbot.QqBotService.instance()
            .start();
        AdvanceDataMonitor.LOG.info(
            "[WebAE] Web console ready - open http://{}:{} in your browser (in-game: /admweb issue)",
            Config.webConsoleBindAddress,
            Config.webConsolePort);
    }

    public static synchronized void stopServer() {
        running = false;
        try {
            // Persist bounded CPU history before either a full server stop or
            // an in-process WebAE restart. Do not change active job state:
            // an HTTP restart does not stop the AE game server.
            com.imgood.textech.webae.cpu.CpuHistoryService.instance()
                .flushAll();
        } catch (Throwable t) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to flush CPU history on server stop", t);
        }
        com.imgood.textech.webae.worldmap.WorldMapCaptureCoordinator.instance()
            .clear();
        WebConsoleServer serverToStop = server;
        server = null;
        unregisterShutdownHook();
        stopIntegrations();
        if (serverToStop != null) {
            try {
                serverToStop.stopServer();
            } catch (Throwable t) {
                AdvanceDataMonitor.LOG.warn("[WebAE] Error stopping HTTP server", t);
            }
        }
    }

    /** Stop and start the in-process HTTP server (OP command). */
    public static synchronized boolean restartServer() {
        if (!Config.webConsoleEnabled) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Cannot restart: web console disabled in config.");
            return false;
        }
        AdvanceDataMonitor.LOG.info("[WebAE] Restarting HTTP server...");
        stopServer();

        WebConsoleServer serverToStart = new WebConsoleServer();
        server = serverToStart;
        if (!serverToStart.startServer()) {
            String failure = serverToStart.getStartFailure();
            server = null;
            serverToStart.stopServer();
            AdvanceDataMonitor.LOG.error("[WebAE] HTTP server restart failed: {}", failure);
            return false;
        }

        running = true;
        if (!registerShutdownHook()) {
            stopServer();
            return false;
        }
        com.imgood.textech.webae.qqbot.QqBotService.instance()
            .start();
        AdvanceDataMonitor.LOG
            .info("[WebAE] HTTP server restarted on {}:{}", Config.webConsoleBindAddress, Config.webConsolePort);
        return true;
    }

    private static void stopIntegrations() {
        com.imgood.textech.webae.alerts.WebhookDispatcher.instance()
            .shutdown();
        com.imgood.textech.webae.qqbot.QqBotService.instance()
            .shutdown();
    }

    private static boolean registerShutdownHook() {
        if (shutdownHook != null) return true;
        shutdownHook = new Thread(new Runnable() {

            @Override
            public void run() {
                AdvanceDataMonitor.LOG.info("[WebAE] JVM shutdown hook - stopping HTTP server.");
                stopServer();
            }
        }, "WebAE-ShutdownHook");
        try {
            Runtime.getRuntime()
                .addShutdownHook(shutdownHook);
            return true;
        } catch (IllegalStateException | SecurityException e) {
            // Shutdown already in progress; the caller must clean up without
            // claiming that the server started successfully.
            shutdownHook = null;
            AdvanceDataMonitor.LOG.warn("[WebAE] JVM shutdown is already in progress; HTTP server was not started");
            return false;
        }
    }

    private static void unregisterShutdownHook() {
        if (shutdownHook == null) return;
        try {
            Runtime.getRuntime()
                .removeShutdownHook(shutdownHook);
        } catch (IllegalStateException | SecurityException ignored) {
            // JVM is shutting down or hook management is unavailable.
        }
        shutdownHook = null;
    }
}
