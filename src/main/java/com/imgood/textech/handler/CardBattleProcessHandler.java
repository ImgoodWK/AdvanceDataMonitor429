package com.imgood.textech.handler;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.cardbattle.CardBattleHttpServer;

/**
 * In-process Card Battle HTTP lifecycle (world/server start including singleplayer).
 * No external Node.js process — same model as {@link WebAeServerHandler}.
 */
public final class CardBattleProcessHandler {

    private static CardBattleHttpServer server;
    private static volatile boolean running;
    private static volatile String lastUrl = "";
    private static volatile String lastError = "";

    private CardBattleProcessHandler() {}

    public static synchronized boolean isRunning() {
        return running && server != null;
    }

    public static String getLastUrl() {
        return lastUrl;
    }

    public static String getLastError() {
        return lastError;
    }

    public static synchronized void startIfEnabled() {
        if (!Config.cardBattleEnabled) {
            AdvanceDataMonitor.LOG.info("[CardBattle] Disabled in config ([cardBattle] enabled=false).");
            return;
        }
        if (isRunning()) {
            AdvanceDataMonitor.LOG.info("[CardBattle] Already running at {}", lastUrl);
            return;
        }
        lastError = "";
        try {
            server = new CardBattleHttpServer();
            server.startServer();
            running = true;
            lastUrl = "http://127.0.0.1:" + Config.cardBattlePort + "/";
            AdvanceDataMonitor.LOG.info(
                "[CardBattle] Ready — open {} (WebAE Bearer token; optional [cardBattle] devToken={})",
                lastUrl,
                Config.cardBattleDevToken != null && Config.cardBattleDevToken.trim()
                    .length() > 0 ? "(set)" : "(empty)");
        } catch (Throwable t) {
            running = false;
            server = null;
            lastError = t.getMessage() != null ? t.getMessage() : t.getClass()
                .getSimpleName();
            AdvanceDataMonitor.LOG.warn("[CardBattle] Failed to start", t);
        }
    }

    public static synchronized void stopServer() {
        running = false;
        if (server != null) {
            try {
                server.stopServer();
            } catch (Throwable t) {
                AdvanceDataMonitor.LOG.warn("[CardBattle] Error stopping HTTP server", t);
            }
            server = null;
        }
        AdvanceDataMonitor.LOG.info("[CardBattle] Stopped.");
    }

    public static synchronized boolean restartServer() {
        stopServer();
        startIfEnabled();
        return isRunning();
    }
}
