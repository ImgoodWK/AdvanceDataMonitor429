package com.imgood.textech.webae;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.TeXTechDataDir;
import com.imgood.textech.webae.api.WebApiRouter;
import com.imgood.textech.webae.api.handler.AuthExchangeHandler;
import com.imgood.textech.webae.api.handler.DisplayHandler;
import com.imgood.textech.webae.api.handler.WebUiDefaultsHandler;
import com.imgood.textech.webae.auth.WebAuthMiddleware;
import com.imgood.textech.webae.auth.WebAuthSession;
import com.imgood.textech.webae.cache.SnapshotCache;
import com.imgood.textech.webae.cache.SnapshotScheduler;
import com.imgood.textech.webae.metric.NetworkMetricSampler;
import com.imgood.textech.webae.power.PowerSampler;

import fi.iki.elonen.NanoHTTPD;

public class WebConsoleServer extends NanoHTTPD {

    private static final String UI_DIR_NAME = "ui";

    private final String bindAddress;
    private final WebApiRouter apiRouter;
    private final WebAuthMiddleware authMiddleware;
    private ExecutorService httpExecutor;

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
        initThreadPool();
    }

    /**
     * Replace NanoHTTPD's default per-request thread with a bounded thread pool.
     * Without this, a burst of concurrent WebAE API requests would create one
     * daemon thread per request, wasting memory and adding GC pressure.
     */
    private void initThreadPool() {
        final int poolSize = Math.max(
            16,
            Runtime.getRuntime()
                .availableProcessors() * 2);
        final AtomicInteger counter = new AtomicInteger(0);
        httpExecutor = Executors.newFixedThreadPool(poolSize, new java.util.concurrent.ThreadFactory() {

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "WebAE-HTTP-" + counter.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        });
        setAsyncRunner(new AsyncRunner() {

            @Override
            public void closeAll() {
                httpExecutor.shutdownNow();
            }

            @Override
            public void closed(ClientHandler clientHandler) {
                // no-op: thread pooling handles cleanup internally
            }

            @Override
            public void exec(ClientHandler clientHandler) {
                httpExecutor.execute(clientHandler);
            }
        });
    }

    public void startServer() {
        try {
            start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
            AdvanceDataMonitor.LOG.info("[WebAE] HTTP server started on {}:{}", bindAddress, Config.webConsolePort);
            File externalIndex = new File(TeXTechDataDir.webAeDir(UI_DIR_NAME), "index.html");
            if (externalIndex.isFile()) {
                AdvanceDataMonitor.LOG.info("[WebAE] UI assets loaded from {}", externalIndex.getParentFile());
            } else if (getClass().getResource("/assets/textech/webae/index.html") == null) {
                AdvanceDataMonitor.LOG.warn(
                    "[WebAE] UI bundle is not installed. Extract the optional *-webae.zip into the instance root.");
            }
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
            if ("/api/ui-defaults".equals(uri)) {
                return WebUiDefaultsHandler.handleGet();
            }
            // Public display reads (layout / frame) authenticated by viewToken query.
            Response publicDisplay = DisplayHandler.handlePublic(uri, session.getMethod(), session.getParms());
            if (publicDisplay != null) {
                return publicDisplay;
            }
            WebAuthMiddleware.AuthResult authResult = authMiddleware.authenticate(session);
            if (!authResult.success) {
                // Allow display viewToken as a read-only guest session for live embed data APIs.
                WebAuthSession displaySession = tryDisplayViewSession(session);
                if (displaySession != null) {
                    return apiRouter.route(session, displaySession);
                }
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

    private static WebAuthSession tryDisplayViewSession(IHTTPSession session) {
        String token = null;
        String authHeader = session.getHeaders()
            .get("authorization");
        if (authHeader != null && authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            token = authHeader.substring(7)
                .trim();
        }
        if (token == null || token.isEmpty()) {
            java.util.Map<String, String> parms = session.getParms();
            if (parms != null) {
                token = parms.get("token");
                if (token == null || token.isEmpty()) token = parms.get("viewToken");
            }
        }
        return DisplayHandler.sessionFromViewToken(token);
    }

    private Response serveStatic(String uri) {
        // External icon pack files (Phase 3.1): /icons/<pack>/<itemId>.png
        // → TeXTech/WebAE/icons/<pack>/<itemId>.png
        if (uri.startsWith("/icons/")) {
            return serveIconFile(uri);
        }
        if (!isSafeUiUri(uri)) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Rejected static UI path traversal: {}", uri);
            return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain", "403 Forbidden");
        }
        if ("/".equals(uri) || uri.isEmpty()) {
            uri = "/index.html";
        }
        // SPA fallback for embed dashboard routes (no react-router on disk).
        if (uri.startsWith("/embed/")) {
            uri = "/index.html";
        }
        InputStream stream = openExternalUiFile(uri);
        if (stream == null) {
            stream = getClass().getResourceAsStream("/assets/textech/webae" + uri);
        }
        if (stream == null && !uri.equals("/index.html")) {
            // Hash-router / deep-link fallback for built assets
            if (!uri.contains(".") || uri.endsWith("/")) {
                stream = openExternalUiFile("/index.html");
                if (stream == null) {
                    stream = getClass().getResourceAsStream("/assets/textech/webae/index.html");
                }
                if (stream != null) {
                    Response response = newChunkedResponse(Response.Status.OK, "text/html", stream);
                    response.addHeader("Cache-Control", "no-cache");
                    return response;
                }
            }
        }
        if (stream == null) {
            String message = "/index.html".equals(uri)
                ? "WebAE UI bundle is not installed. Extract the optional *-webae.zip into the Minecraft instance root."
                : "404 Not Found";
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain; charset=utf-8", message);
        }
        String mimeType = getMimeType(uri);
        try {
            Response response = newChunkedResponse(Response.Status.OK, mimeType, stream);
            response.addHeader(
                "Cache-Control",
                "/index.html".equals(uri) ? "no-cache" : "public, max-age=31536000, immutable");
            return response;
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("[WebAE] Failed to serve static file: {}", uri, e);
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "500 Internal Server Error");
        }
    }

    /** Open an optional UI bundle file from {@code TeXTech/WebAE/ui/}. */
    private InputStream openExternalUiFile(String uri) {
        File baseDir = TeXTechDataDir.webAeDir(UI_DIR_NAME);
        File target = new File(baseDir, uri.startsWith("/") ? uri.substring(1) : uri);
        try {
            String canonicalBase = baseDir.getCanonicalPath();
            String canonicalTarget = target.getCanonicalPath();
            if (!canonicalTarget.startsWith(canonicalBase + File.separator)) {
                return null;
            }
            return target.isFile() ? new FileInputStream(target) : null;
        } catch (IOException e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to open external UI asset {}", uri, e);
            return null;
        }
    }

    private static boolean isSafeUiUri(String uri) {
        if (uri == null || uri.indexOf('\0') >= 0 || uri.indexOf('\\') >= 0) {
            return false;
        }
        String lower = uri.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("%2e") || lower.contains("%5c")) {
            return false;
        }
        String[] segments = uri.split("/");
        for (String segment : segments) {
            if ("..".equals(segment)) return false;
        }
        return true;
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
