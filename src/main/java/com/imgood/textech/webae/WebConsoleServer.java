package com.imgood.textech.webae;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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
    private static final int HTTP_QUEUE_CAPACITY = 128;
    private static final int HTTP_MAX_THREADS = 32;
    private final Object lifecycleLock = new Object();
    private final Set<ClientHandler> activeClients = Collections
        .newSetFromMap(new ConcurrentHashMap<ClientHandler, Boolean>());
    private volatile ThreadPoolExecutor httpExecutor;
    private volatile boolean started;
    private volatile String startFailure;

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
        configureAsyncRunner();
    }

    /**
     * Replace NanoHTTPD's default per-request thread with a bounded thread pool.
     * The queue is deliberately bounded as well as the worker count: a slow
     * client burst must not turn into an unbounded heap allocation.
     */
    private void configureAsyncRunner() {
        setAsyncRunner(new AsyncRunner() {

            @Override
            public void closeAll() {
                closeActiveClients();
                shutdownHttpExecutor();
            }

            @Override
            public void closed(ClientHandler clientHandler) {
                activeClients.remove(clientHandler);
            }

            @Override
            public void exec(ClientHandler clientHandler) {
                submitClientHandler(clientHandler);
            }
        });
    }

    private void submitClientHandler(ClientHandler clientHandler) {
        synchronized (lifecycleLock) {
            if (!started || httpExecutor == null || httpExecutor.isShutdown()) {
                clientHandler.close();
                return;
            }
            activeClients.add(clientHandler);
            try {
                httpExecutor.execute(clientHandler);
            } catch (RejectedExecutionException e) {
                // AbortPolicy keeps admission bounded. Close the socket
                // explicitly so an overloaded server does not leak a
                // connection while the caller's accept loop continues.
                activeClients.remove(clientHandler);
                clientHandler.close();
                AdvanceDataMonitor.LOG.warn("[WebAE] HTTP request rejected: worker queue is full");
            }
        }
    }

    private void closeActiveClients() {
        for (ClientHandler clientHandler : activeClients) {
            if (clientHandler != null) {
                clientHandler.close();
            }
        }
        activeClients.clear();
    }

    private ThreadPoolExecutor ensureHttpExecutor() {
        synchronized (lifecycleLock) {
            if (httpExecutor == null || httpExecutor.isShutdown() || httpExecutor.isTerminated()) {
                httpExecutor = createHttpExecutor();
            }
            return httpExecutor;
        }
    }

    private ThreadPoolExecutor createHttpExecutor() {
        final int availableProcessors = Math.max(
            1,
            Runtime.getRuntime()
                .availableProcessors());
        final int poolSize = Math.min(HTTP_MAX_THREADS, Math.max(4, availableProcessors * 2));
        final AtomicInteger counter = new AtomicInteger(0);
        ThreadFactory threadFactory = new ThreadFactory() {

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "WebAE-HTTP-" + counter.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        };
        return new ThreadPoolExecutor(
            poolSize,
            poolSize,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<Runnable>(HTTP_QUEUE_CAPACITY),
            threadFactory,
            new ThreadPoolExecutor.AbortPolicy());
    }

    private void shutdownHttpExecutor() {
        ThreadPoolExecutor executor;
        synchronized (lifecycleLock) {
            executor = httpExecutor;
            httpExecutor = null;
        }
        if (executor == null) return;

        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(2L, TimeUnit.SECONDS)) {
                AdvanceDataMonitor.LOG.warn("[WebAE] HTTP worker pool did not terminate within 2 seconds");
            }
        } catch (InterruptedException e) {
            Thread.currentThread()
                .interrupt();
            AdvanceDataMonitor.LOG.warn("[WebAE] Interrupted while stopping HTTP worker pool");
        }
    }

    /**
     * Start the server and report the actual bind result to the caller.
     * NanoHTTPD throws on bind failure; do not expose a successful state after
     * that exception and make the executor reusable for a later retry.
     */
    public boolean startServer() {
        synchronized (lifecycleLock) {
            if (started) return true;
            startFailure = null;
            ensureHttpExecutor();
            try {
                start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
                started = true;
            } catch (IOException | RuntimeException e) {
                started = false;
                startFailure = e.getClass()
                    .getSimpleName() + ": "
                    + String.valueOf(e.getMessage());
                try {
                    stop();
                } catch (Throwable stopFailure) {
                    AdvanceDataMonitor.LOG.warn("[WebAE] Failed to clean up after HTTP start failure", stopFailure);
                } finally {
                    shutdownHttpExecutor();
                }
                AdvanceDataMonitor.LOG
                    .error("[WebAE] Failed to start HTTP server on {}:{}", bindAddress, Config.webConsolePort, e);
                return false;
            }

            AdvanceDataMonitor.LOG.info("[WebAE] HTTP server started on {}:{}", bindAddress, Config.webConsolePort);
            File externalIndex = new File(TeXTechDataDir.webAeDir(UI_DIR_NAME), "index.html");
            if (externalIndex.isFile()) {
                AdvanceDataMonitor.LOG.info("[WebAE] UI assets loaded from {}", externalIndex.getParentFile());
            } else if (getClass().getResource("/assets/textech/webae/index.html") == null) {
                AdvanceDataMonitor.LOG.warn(
                    "[WebAE] UI bundle is not installed. Extract the optional *-webae.zip into the instance root.");
            }
            return true;
        }
    }

    public boolean isStarted() {
        return started;
    }

    public String getStartFailure() {
        return startFailure;
    }

    public void stopServer() {
        boolean hadServer;
        synchronized (lifecycleLock) {
            hadServer = started;
            started = false;
        }
        try {
            if (hadServer) stop();
        } catch (Throwable e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to stop HTTP server", e);
        } finally {
            closeActiveClients();
            shutdownHttpExecutor();
        }
        if (hadServer) {
            AdvanceDataMonitor.LOG.info("[WebAE] HTTP server stopped.");
        }
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
