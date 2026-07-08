package com.imgood.textech.webae;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.webae.api.WebApiRouter;
import com.imgood.textech.webae.api.handler.AuthExchangeHandler;
import com.imgood.textech.webae.auth.WebAuthMiddleware;
import com.imgood.textech.webae.cache.SnapshotCache;
import com.imgood.textech.webae.cache.SnapshotScheduler;
import com.imgood.textech.webae.metric.NetworkMetricSampler;
import com.imgood.textech.webae.power.PowerSampler;

import fi.iki.elonen.NanoHTTPD;

public class WebConsoleServer extends NanoHTTPD {

    private final String bindAddress;
    private final WebApiRouter apiRouter;
    private final WebAuthMiddleware authMiddleware;

    public WebConsoleServer() {
        super(Config.webConsoleBindAddress, Config.webConsolePort);
        this.bindAddress = Config.webConsoleBindAddress;
        this.apiRouter = new WebApiRouter();
        this.authMiddleware = new WebAuthMiddleware();
        SnapshotScheduler.setSnapshotCache(SnapshotCache.instance());
        PowerSampler.getInstance()
            .setSnapshotCache(SnapshotCache.instance());
        NetworkMetricSampler.getInstance()
            .setSnapshotCache(SnapshotCache.instance());
    }

    public void startServer() {
        try {
            start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
            AdvanceDataMonitor.LOG.info("[WebAE] HTTP server started on {}:{}", bindAddress, Config.webConsolePort);
        } catch (IOException e) {
            AdvanceDataMonitor.LOG
                .error("[WebAE] Failed to start HTTP server on {}:{}", bindAddress, Config.webConsolePort, e);
        }
    }

    public void stopServer() {
        stop();
        AdvanceDataMonitor.LOG.info("[WebAE] HTTP server stopped.");
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();

        if (uri.startsWith("/api/")) {
            return handleApi(session);
        }

        return serveStatic(uri);
    }

    private Response handleApi(IHTTPSession session) {
        try {
            String uri = session.getUri();
            if ("/api/auth/exchange".equals(uri)) {
                return AuthExchangeHandler.handle(session);
            }
            WebAuthMiddleware.AuthResult authResult = authMiddleware.authenticate(session);
            if (!authResult.success) {
                return newFixedLengthResponse(Response.Status.UNAUTHORIZED, "application/json", authResult.errorBody);
            }
            return apiRouter.route(session, authResult.session);
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("[WebAE] API error", e);
            return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                "{\"status\":\"error\",\"message\":\"Internal server error\"}");
        }
    }

    private Response serveStatic(String uri) {
        // External icon pack files (Phase 3.1): /icons/<pack>/<itemId>.png
        // → TeXTech/WebAE/icons/<pack>/<itemId>.png
        if (uri.startsWith("/icons/")) {
            return serveIconFile(uri);
        }
        if ("/".equals(uri) || uri.isEmpty()) {
            uri = "/index.html";
        }
        String resourcePath = "/assets/textech/webae" + uri;
        InputStream stream = getClass().getResourceAsStream(resourcePath);
        if (stream == null) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "404 Not Found");
        }
        String mimeType = getMimeType(uri);
        try {
            return newChunkedResponse(Response.Status.OK, mimeType, stream);
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("[WebAE] Failed to serve static file: {}", uri, e);
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "500 Internal Server Error");
        }
    }

    /**
     * Serve a PNG from the external icon pack directory. Applies path-traversal
     * protection by canonicalizing the resolved path against the base directory.
     */
    private Response serveIconFile(String uri) {
        File baseDir = com.imgood.textech.webae.icon.IconStore.instance()
            .getBaseDir();
        if (!baseDir.isDirectory()) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "404 Not Found");
        }
        String relative = uri.substring("/icons/".length());
        File target = new File(baseDir, relative);
        try {
            String canonicalBase = baseDir.getCanonicalPath();
            String canonicalTarget = target.getCanonicalPath();
            if (!canonicalTarget.startsWith(canonicalBase + File.separator) && !canonicalTarget.equals(canonicalBase)) {
                AdvanceDataMonitor.LOG.warn("[WebAE] Rejected icon path traversal: {}", uri);
                return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain", "403 Forbidden");
            }
        } catch (java.io.IOException e) {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "500 Internal Server Error");
        }
        if (!target.isFile()) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "404 Not Found");
        }
        String mimeType = getMimeType(target.getName());
        try {
            FileInputStream fis = new FileInputStream(target);
            Response resp = newChunkedResponse(Response.Status.OK, mimeType, fis);
            resp.addHeader("Cache-Control", "max-age=86400");
            return resp;
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("[WebAE] Failed to serve icon file: {}", uri, e);
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "500 Internal Server Error");
        }
    }

    private static String getMimeType(String uri) {
        if (uri.endsWith(".html") || uri.endsWith(".htm")) return "text/html";
        if (uri.endsWith(".css")) return "text/css";
        if (uri.endsWith(".js")) return "application/javascript";
        if (uri.endsWith(".json")) return "application/json";
        if (uri.endsWith(".png")) return "image/png";
        if (uri.endsWith(".jpg") || uri.endsWith(".jpeg")) return "image/jpeg";
        if (uri.endsWith(".gif")) return "image/gif";
        if (uri.endsWith(".svg")) return "image/svg+xml";
        if (uri.endsWith(".webmanifest") || uri.endsWith("manifest.json")) return "application/manifest+json";
        if (uri.endsWith(".ico")) return "image/x-icon";
        return "text/plain";
    }
}
