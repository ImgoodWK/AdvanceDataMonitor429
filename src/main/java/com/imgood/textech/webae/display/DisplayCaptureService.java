package com.imgood.textech.webae.display;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.TeXTechDataDir;

/**
 * Serves published-dashboard JPEG frames for in-game monitors.
 * Preferred source (no MCEF): browser tab push ({@code browser-jpeg} via
 * {@link #putBrowserFrame}). Fallback: system Chromium/Chrome/Edge headless capture of
 * {@code /embed/dashboard}. Compact {@code render.html} is debug-only / last-resort.
 * Runs off the Minecraft tick thread.
 */
public final class DisplayCaptureService {

    public static final String SOURCE_BROWSER_JPEG = "browser-jpeg";
    public static final String SOURCE_SPA_JPEG = "spa-jpeg";
    public static final String SOURCE_SERVER_HTML = "server-html";

    private static final DisplayCaptureService INSTANCE = new DisplayCaptureService();
    private static final int MAX_ACTIVE = 1;
    private static final long STALE_MS = 60_000L;
    /** Hold browser-pushed frames against host embed overwrite. */
    private static final long BROWSER_FRAME_HOLD_MS = 25_000L;
    private static final int MAX_BROWSER_JPEG_BYTES = 2_500_000;
    /** Minimum time between accepted Chrome captures per display (headless is expensive). */
    private static final long MIN_INTERVAL_MS = 2500L;
    /** Virtual time for SPA boot + capture=1 settle (~2.5s) + paint. */
    private static final long SPA_VIRTUAL_TIME_MS = 45_000L;
    private static final long SPA_WAIT_MS = 70_000L;
    private static final long SPA_READY_POLL_MS = 50_000L;
    private static final long SPA_READY_DUMP_BUDGET_MS = 25_000L;
    private static final long SPA_READY_DUMP_WAIT_MS = 45_000L;
    private static final int SPA_SCREENSHOT_ATTEMPTS = 2;
    /** Static server HTML needs little virtual time (no React). */
    private static final long FALLBACK_VIRTUAL_TIME_MS = 3_000L;
    private static final long FALLBACK_WAIT_MS = 20_000L;
    private static final Object CAPTURE_LOCK = new Object();
    private static final int BLANK_VARIANCE_MAX = 12;

    private final ConcurrentHashMap<String, CachedFrame> frames = new ConcurrentHashMap<String, CachedFrame>();
    private final ConcurrentHashMap<String, Long> lastTouch = new ConcurrentHashMap<String, Long>();
    private final ConcurrentHashMap<String, Boolean> inFlight = new ConcurrentHashMap<String, Boolean>();
    private final ConcurrentHashMap<String, String> lastErrors = new ConcurrentHashMap<String, String>();
    private final AtomicInteger activeCaptures = new AtomicInteger(0);

    private final ExecutorService worker = Executors.newSingleThreadExecutor(new ThreadFactory() {

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "TeXTech-DisplayCapture");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        }
    });

    private volatile String browserPath;
    private volatile String browserError;
    /** Cache key for the last successful resolve (config|env) so path overrides take effect. */
    private volatile String browserResolveKey;

    private DisplayCaptureService() {}

    public static DisplayCaptureService instance() {
        return INSTANCE;
    }

    public void touch(String displayId, int width) {
        if (displayId == null || displayId.isEmpty()) return;
        lastTouch.put(displayId, Long.valueOf(System.currentTimeMillis()));
        DisplayRecord record = DisplayStore.getById(displayId);
        if (record != null) {
            scheduleCapture(record, normalizeWidth(width), false);
        }
    }

    public void invalidate(String displayId) {
        if (displayId == null) return;
        frames.remove(displayId);
        lastErrors.remove(displayId);
    }

    /** Last capture error code for a display (empty if last capture succeeded). */
    public String getLastError(String displayId) {
        if (displayId == null) return "";
        String err = lastErrors.get(displayId);
        if (err != null && !err.isEmpty()) return err;
        if (browserError != null && !browserError.isEmpty()) return browserError;
        return "";
    }

    public boolean hasCachedFrame(String displayId) {
        if (displayId == null) return false;
        CachedFrame cached = frames.get(displayId);
        return cached != null && cached.jpeg != null && cached.jpeg.length > 0;
    }

    public boolean isCaptureInFlight(String displayId) {
        return displayId != null && inFlight.containsKey(displayId);
    }

    /** Cached frame source label ({@code browser-jpeg} / {@code spa-jpeg} / {@code server-html}). */
    public String getCachedSource(String displayId) {
        if (displayId == null) return "";
        CachedFrame cached = frames.get(displayId);
        return cached != null && cached.source != null ? cached.source : "";
    }

    /**
     * Accept a JPEG captured in the user's real WebAE browser tab (WYSIWYG, no MCEF).
     * Preferred over host embed screenshots until {@link #BROWSER_FRAME_HOLD_MS} elapses.
     */
    public FrameResult putBrowserFrame(String displayId, byte[] jpeg, int width) {
        if (displayId == null || displayId.isEmpty()) {
            return FrameResult.error("missing_display");
        }
        if (jpeg == null || jpeg.length < 256) {
            return FrameResult.error("empty_frame");
        }
        if (jpeg.length > MAX_BROWSER_JPEG_BYTES) {
            return FrameResult.error("frame_too_large");
        }
        if ((jpeg[0] & 0xFF) != 0xFF || (jpeg[1] & 0xFF) != 0xD8) {
            return FrameResult.error("not_jpeg");
        }
        int w = normalizeWidth(width);
        String etag = "\"" + sha256Hex(jpeg) + "\"";
        frames.put(displayId, new CachedFrame(jpeg, etag, w, System.currentTimeMillis(), SOURCE_BROWSER_JPEG));
        lastErrors.remove(displayId);
        lastTouch.put(displayId, Long.valueOf(System.currentTimeMillis()));
        AdvanceDataMonitor.LOG.info(
            "[WebAE] Display {} browser-jpeg push ({} bytes, width={})",
            displayId,
            Integer.valueOf(jpeg.length),
            Integer.valueOf(w));
        return FrameResult.ok(jpeg, etag, SOURCE_BROWSER_JPEG);
    }

    public FrameResult getOrCapture(DisplayRecord record, int width, String ifNoneMatch) {
        if (record == null) {
            return FrameResult.error("missing_display");
        }
        int w = normalizeWidth(width);
        lastTouch.put(record.id, Long.valueOf(System.currentTimeMillis()));
        CachedFrame cached = frames.get(record.id);
        if (cached != null && cached.jpeg != null && cached.jpeg.length > 0) {
            boolean browserFresh = SOURCE_BROWSER_JPEG.equals(cached.source)
                && System.currentTimeMillis() - cached.capturedAt < BROWSER_FRAME_HOLD_MS;
            boolean sameWidthFresh = cached.width == w && System.currentTimeMillis() - cached.capturedAt
                < Math.max(MIN_INTERVAL_MS, refreshBudgetMs(record.id));
            if (browserFresh || sameWidthFresh) {
                if (ifNoneMatch != null && ifNoneMatch.equals(cached.etag)) {
                    return FrameResult.notModified(cached.etag);
                }
                // Fresh browser push: do not kick host Chrome yet.
                if (!browserFresh) {
                    scheduleCapture(record, w, false);
                }
                return FrameResult.ok(cached.jpeg, cached.etag, cached.source);
            }
        }
        // Never launch Chrome on the HTTP thread — that raced with the worker and
        // flooded localhost until headless got ERR_CONNECTION_REFUSED / blank frames.
        scheduleCapture(record, w, cached == null);
        if (cached != null && cached.jpeg != null && cached.jpeg.length > 0) {
            if (ifNoneMatch != null && ifNoneMatch.equals(cached.etag)) {
                return FrameResult.notModified(cached.etag);
            }
            return FrameResult.ok(cached.jpeg, cached.etag, cached.source);
        }
        CachedFrame waited = awaitFrame(record.id, w, 20_000L);
        if (waited != null && waited.jpeg != null && waited.jpeg.length > 0) {
            if (ifNoneMatch != null && ifNoneMatch.equals(waited.etag)) {
                return FrameResult.notModified(waited.etag);
            }
            return FrameResult.ok(waited.jpeg, waited.etag, waited.source);
        }
        String err = lastErrors.get(record.id);
        if (err == null || err.isEmpty()) {
            err = browserError != null ? browserError : "capture_pending";
        }
        return FrameResult.error(err);
    }

    private CachedFrame awaitFrame(String displayId, int width, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            CachedFrame cached = frames.get(displayId);
            if (cached != null && cached.width == width && cached.jpeg != null && cached.jpeg.length > 0) {
                return cached;
            }
            if (!inFlight.containsKey(displayId) && activeCaptures.get() == 0) {
                cached = frames.get(displayId);
                return cached;
            }
            try {
                Thread.sleep(150L);
            } catch (InterruptedException e) {
                Thread.currentThread()
                    .interrupt();
                break;
            }
        }
        return frames.get(displayId);
    }

    private long refreshBudgetMs(String displayId) {
        Long touch = lastTouch.get(displayId);
        if (touch == null) return 5000L;
        long age = System.currentTimeMillis() - touch.longValue();
        if (age < 5_000L) return 2500L;
        if (age < 30_000L) return 3000L;
        return 5000L;
    }

    private void scheduleCapture(final DisplayRecord record, final int width, boolean force) {
        if (record == null) return;
        if (!force) {
            CachedFrame cached = frames.get(record.id);
            if (cached != null && SOURCE_BROWSER_JPEG.equals(cached.source)
                && System.currentTimeMillis() - cached.capturedAt < BROWSER_FRAME_HOLD_MS) {
                return;
            }
            if (cached != null && System.currentTimeMillis() - cached.capturedAt < refreshBudgetMs(record.id)) {
                return;
            }
        }
        if (inFlight.putIfAbsent(record.id, Boolean.TRUE) != null) return;
        if (activeCaptures.get() >= MAX_ACTIVE) {
            inFlight.remove(record.id);
            return;
        }
        worker.execute(new Runnable() {

            @Override
            public void run() {
                try {
                    activeCaptures.incrementAndGet();
                    captureNow(record, width);
                } finally {
                    activeCaptures.decrementAndGet();
                    inFlight.remove(record.id);
                    pruneStale();
                }
            }
        });
    }

    private FrameResult captureNow(DisplayRecord record, int width) {
        synchronized (CAPTURE_LOCK) {
            return captureNowLocked(record, width);
        }
    }

    private FrameResult captureNowLocked(DisplayRecord record, int width) {
        CachedFrame existing = frames.get(record.id);
        if (existing != null && SOURCE_BROWSER_JPEG.equals(existing.source)
            && existing.jpeg != null
            && existing.jpeg.length > 0
            && System.currentTimeMillis() - existing.capturedAt < BROWSER_FRAME_HOLD_MS) {
            return FrameResult.ok(existing.jpeg, existing.etag, existing.source);
        }
        String browser = resolveBrowser();
        if (browser == null) {
            String err = browserError != null ? browserError : "browser_not_found";
            // Keep serving a stale browser push rather than failing hard.
            if (existing != null && existing.jpeg != null && existing.jpeg.length > 0) {
                return FrameResult.ok(existing.jpeg, existing.etag, existing.source);
            }
            lastErrors.put(record.id, err);
            return FrameResult.error(err);
        }
        File tmpDir = TeXTechDataDir.webAeDir("display-frames");
        if (!tmpDir.isDirectory() && !tmpDir.mkdirs()) {
            lastErrors.put(record.id, "frame_dir_failed");
            return FrameResult.error("frame_dir_failed");
        }
        int height = Math
            .max(64, (int) Math.round(width * (double) record.viewportHeight / Math.max(1, record.viewportWidth)));

        String spaUrl = buildSpaCaptureUrl(record);
        if (spaUrl == null || spaUrl.isEmpty()) {
            lastErrors.put(record.id, "spa_url_invalid");
            return FrameResult.error("spa_url_invalid");
        }
        boolean ready = waitForSpaCaptureReady(browser, spaUrl);
        if (!ready) {
            AdvanceDataMonitor.LOG
                .warn("[WebAE] Display {} spa-timeout waiting for data-webae-capture-ready @ {}", record.id, spaUrl);
            lastErrors.put(record.id, "spa_timeout");
        }

        FrameResult last = FrameResult.error(ready ? "spa-blank" : "spa_timeout");
        int attempts = ready ? SPA_SCREENSHOT_ATTEMPTS : SPA_SCREENSHOT_ATTEMPTS + 1;
        for (int i = 0; i < attempts; i++) {
            long budget = SPA_VIRTUAL_TIME_MS + (long) i * 4_000L;
            // After a ready-timeout, give the final attempt more virtual time to paint.
            if (!ready && i == attempts - 1) {
                budget = SPA_VIRTUAL_TIME_MS + 12_000L;
            }
            FrameResult spa = captureUrl(browser, record, width, height, tmpDir, spaUrl, budget, SPA_WAIT_MS);
            if (spa.jpeg != null && spa.jpeg.length > 0) {
                AdvanceDataMonitor.LOG.info(
                    "[WebAE] Display {} spa-ok (attempt {}, ready={})",
                    record.id,
                    Integer.valueOf(i + 1),
                    Boolean.valueOf(ready));
                lastErrors.remove(record.id);
                return spa;
            }
            last = spa;
            if (spa.error != null && !spa.error.isEmpty()) {
                lastErrors.put(record.id, spa.error);
            }
            AdvanceDataMonitor.LOG.warn(
                "[WebAE] Display {} spa-blank/failed attempt {} ({}): {}",
                record.id,
                Integer.valueOf(i + 1),
                spa.error != null ? spa.error : "blank",
                spaUrl);
            // Brief pause before retry so SPA can finish paint.
            try {
                Thread.sleep(500L);
            } catch (InterruptedException e) {
                Thread.currentThread()
                    .interrupt();
                break;
            }
        }

        // SPA React often stays blank under --virtual-time-budget (known Chrome timing).
        // Fall back to server-rendered render.html so the monitor is not a cyan empty frame.
        String finalErr = last.error != null ? last.error : (ready ? "capture_failed" : "spa_timeout");
        String fallbackUrl = buildFallbackRenderUrl(record);
        FrameResult fallback = captureUrl(
            browser,
            record,
            width,
            height,
            tmpDir,
            fallbackUrl,
            FALLBACK_VIRTUAL_TIME_MS,
            FALLBACK_WAIT_MS);
        if (fallback.jpeg != null && fallback.jpeg.length > 0) {
            AdvanceDataMonitor.LOG
                .warn("[WebAE] Display {} SPA failed ({}); using server-html fallback frame", record.id, finalErr);
            lastErrors.remove(record.id);
            CachedFrame cached = frames.get(record.id);
            if (cached != null) {
                frames.put(
                    record.id,
                    new CachedFrame(cached.jpeg, cached.etag, cached.width, cached.capturedAt, SOURCE_SERVER_HTML));
            }
            return FrameResult.ok(fallback.jpeg, fallback.etag, SOURCE_SERVER_HTML);
        }

        lastErrors.put(record.id, finalErr);
        AdvanceDataMonitor.LOG
            .warn("[WebAE] Display {} no-frame after SPA + server-html attempts (last={})", record.id, finalErr);
        return FrameResult.error(finalErr);
    }

    /**
     * Poll headless {@code --dump-dom} until the embed marks {@code data-webae-capture-ready},
     * or until the ready budget expires. Accepts {@code 1} (painted) or {@code error} (visible error UI).
     */
    private static boolean waitForSpaCaptureReady(String browser, String url) {
        long deadline = System.currentTimeMillis() + SPA_READY_POLL_MS;
        long budget = SPA_READY_DUMP_BUDGET_MS;
        String lastDom = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                String dom = runChromeDumpDom(browser, url, budget, SPA_READY_DUMP_WAIT_MS);
                lastDom = dom;
                if (domHasCaptureReady(dom)) {
                    return true;
                }
            } catch (Exception e) {
                AdvanceDataMonitor.LOG.debug("[WebAE] dump-dom ready poll failed: {}", e.toString());
            }
            budget = Math.min(budget + 6_000L, SPA_VIRTUAL_TIME_MS);
            try {
                Thread.sleep(400L);
            } catch (InterruptedException e) {
                Thread.currentThread()
                    .interrupt();
                return false;
            }
        }
        if (lastDom != null && !lastDom.isEmpty()) {
            AdvanceDataMonitor.LOG.warn(
                "[WebAE] SPA ready timeout; dump-dom length={} preview={}",
                Integer.valueOf(lastDom.length()),
                lastDom.length() > 240 ? lastDom.substring(0, 240)
                    .replace('\n', ' ') : lastDom.replace('\n', ' '));
        }
        return false;
    }

    private static boolean domHasCaptureReady(String dom) {
        if (dom == null || dom.isEmpty()) return false;
        return dom.indexOf("data-webae-capture-ready=\"1\"") >= 0 || dom.indexOf("data-webae-capture-ready='1'") >= 0
            || dom.indexOf("data-webae-capture-ready=\"error\"") >= 0
            || dom.indexOf("data-webae-capture-ready='error'") >= 0;
    }

    private FrameResult captureUrl(String browser, DisplayRecord record, int width, int height, File tmpDir, String url,
        long virtualTimeBudgetMs, long waitMs) {
        File pngFile = new File(tmpDir, record.id + "-" + width + "-" + System.nanoTime() + ".png");
        File latestPng = new File(tmpDir, record.id + "-" + width + ".png");
        try {
            ChromeShot shot = runChromeScreenshot(browser, url, pngFile, width, height, virtualTimeBudgetMs, waitMs);
            if (shot.image == null) {
                return FrameResult.error("screenshot_missing");
            }
            int variance = sampleLumaVariance(shot.image);
            copyLatest(pngFile, latestPng);
            if (variance < BLANK_VARIANCE_MAX) {
                return FrameResult.error("capture_blank");
            }
            byte[] jpeg = toJpeg(scaleIfNeeded(shot.image, width, height), 0.85F);
            if (jpeg == null || jpeg.length == 0) return FrameResult.error("jpeg_failed");
            String etag = "\"" + sha256Hex(jpeg) + "\"";
            CachedFrame hold = frames.get(record.id);
            if (hold != null && SOURCE_BROWSER_JPEG.equals(hold.source)
                && System.currentTimeMillis() - hold.capturedAt < BROWSER_FRAME_HOLD_MS) {
                return FrameResult.ok(hold.jpeg, hold.etag, hold.source);
            }
            String source = url != null && url.indexOf("/render.html") >= 0 ? SOURCE_SERVER_HTML : SOURCE_SPA_JPEG;
            frames.put(record.id, new CachedFrame(jpeg, etag, width, System.currentTimeMillis(), source));
            return FrameResult.ok(jpeg, etag, source);
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Display capture failed for {} @ {}: {}", record.id, url, e.toString());
            return FrameResult.error("capture_failed");
        }
    }

    private static void copyLatest(File from, File to) {
        if (from == null || to == null || !from.isFile()) return;
        try {
            java.nio.file.Files.copy(from.toPath(), to.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {}
    }

    private static ChromeShot runChromeScreenshot(String browser, String url, File pngFile, int width, int height,
        long virtualTimeBudgetMs, long waitMs) throws Exception {
        ArrayList<String> cmd = new ArrayList<String>();
        cmd.add(browser);
        addChromeHeadlessArgs(cmd);
        cmd.add("--window-size=" + width + "," + height);
        cmd.add("--screenshot=" + pngFile.getAbsolutePath());
        cmd.add("--virtual-time-budget=" + virtualTimeBudgetMs);
        cmd.add(url);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String stderrPreview = "";
        try {
            stderrPreview = readPreview(process.getInputStream(), 400);
            boolean finished = waitFor(process, waitMs);
            int exit = finished ? process.exitValue() : -1;
            if (!finished) {
                process.destroy();
                return ChromeShot.missing(exit, stderrPreview);
            }
            if (!pngFile.isFile()) return ChromeShot.missing(exit, stderrPreview);
            java.io.FileInputStream in = new java.io.FileInputStream(pngFile);
            try {
                BufferedImage image = ImageIO.read(in);
                return new ChromeShot(image, exit, stderrPreview);
            } finally {
                in.close();
            }
        } finally {
            try {
                process.destroy();
            } catch (Exception ignored) {}
        }
    }

    /** Headless dump-dom used to wait for embed {@code data-webae-capture-ready}. */
    private static String runChromeDumpDom(String browser, String url, long virtualTimeBudgetMs, long waitMs)
        throws Exception {
        ArrayList<String> cmd = new ArrayList<String>();
        cmd.add(browser);
        addChromeHeadlessArgs(cmd);
        cmd.add("--virtual-time-budget=" + virtualTimeBudgetMs);
        cmd.add("--dump-dom");
        cmd.add(url);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        try {
            String dom = readAllLimited(process.getInputStream(), 2_000_000);
            boolean finished = waitFor(process, waitMs);
            if (!finished) {
                process.destroy();
            }
            return dom;
        } finally {
            try {
                process.destroy();
            } catch (Exception ignored) {}
        }
    }

    /**
     * Shared Chromium flags. Prefer SwiftShader over {@code --disable-gpu} so dark/blank
     * headless frames are less common on Windows.
     */
    private static void addChromeHeadlessArgs(ArrayList<String> cmd) {
        cmd.add("--headless=new");
        cmd.add("--hide-scrollbars");
        cmd.add("--no-first-run");
        cmd.add("--no-default-browser-check");
        cmd.add("--disable-extensions");
        cmd.add("--disable-dev-shm-usage");
        cmd.add("--allow-insecure-localhost");
        cmd.add("--ignore-certificate-errors");
        cmd.add("--run-all-compositor-stages-before-draw");
        cmd.add("--use-angle=swiftshader");
        cmd.add("--enable-unsafe-swiftshader");
    }

    private static String readAllLimited(InputStream in, int maxBytes) {
        if (in == null) return "";
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int total = 0;
            int n;
            while ((n = in.read(buf)) >= 0) {
                if (total < maxBytes) {
                    int write = Math.min(n, maxBytes - total);
                    if (write > 0) {
                        baos.write(buf, 0, write);
                        total += write;
                    }
                }
            }
            return new String(baos.toByteArray(), "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private static String readPreview(InputStream in, int max) {
        try {
            byte[] buf = new byte[max];
            int n = in.read(buf);
            if (n <= 0) return "";
            // Drain the rest so the process can exit.
            byte[] sink = new byte[4096];
            while (in.read(sink) >= 0) {
                // discard
            }
            return new String(buf, 0, n, "UTF-8").replace('\r', ' ')
                .replace('\n', ' ');
        } catch (Exception e) {
            return "";
        }
    }

    /** Low variance ≈ near-solid fill (blank Login shell / unpainted SPA). */
    private static int sampleLumaVariance(BufferedImage image) {
        if (image == null) return 0;
        int w = image.getWidth();
        int h = image.getHeight();
        if (w < 4 || h < 4) return 0;
        long sum = 0L;
        long sumSq = 0L;
        int n = 0;
        for (int y = h / 8; y < h; y += Math.max(1, h / 16)) {
            for (int x = w / 8; x < w; x += Math.max(1, w / 16)) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int luma = (r * 3 + g * 4 + b) / 8;
                sum += luma;
                sumSq += (long) luma * luma;
                n++;
            }
        }
        if (n < 4) return 0;
        long mean = sum / n;
        long var = sumSq / n - mean * mean;
        return var < 0L ? 0 : (int) Math.min(255L, var);
    }

    private static final class ChromeShot {

        final BufferedImage image;
        final int exitCode;
        final String stderrPreview;

        ChromeShot(BufferedImage image, int exitCode, String stderrPreview) {
            this.image = image;
            this.exitCode = exitCode;
            this.stderrPreview = stderrPreview == null ? "" : stderrPreview;
        }

        static ChromeShot missing(int exitCode, String stderrPreview) {
            return new ChromeShot(null, exitCode, stderrPreview);
        }
    }

    /** Real SPA URL used for WYSIWYG in-game frames (lazy path; no MCEF). */
    private static String buildSpaCaptureUrl(DisplayRecord record) {
        return "http://127.0.0.1:" + Config.webConsolePort
            + "/embed/dashboard/"
            + record.id
            + "?token="
            + record.viewToken
            + "&capture=1";
    }

    /** Compact HTML debug URL (not used as a successful monitor frame). */
    static String buildFallbackRenderUrl(DisplayRecord record) {
        return "http://127.0.0.1:" + Config.webConsolePort
            + "/api/display/"
            + record.id
            + "/render.html?token="
            + record.viewToken;
    }

    private String resolveBrowser() {
        String configured = trimToNull(Config.webDisplayChromePath);
        String envPath = trimToNull(envOr("WEBAE_CHROME_PATH", null));
        String resolveKey = String.valueOf(configured) + "|" + String.valueOf(envPath);
        if (browserPath != null && resolveKey.equals(browserResolveKey)) {
            return browserPath;
        }
        synchronized (this) {
            if (browserPath != null && resolveKey.equals(browserResolveKey)) {
                return browserPath;
            }
            ArrayList<String> candidates = new ArrayList<String>();
            LinkedHashSet<String> seen = new LinkedHashSet<String>();
            addCandidate(candidates, seen, configured);
            addCandidate(candidates, seen, envPath);
            String[] auto = candidateBrowsers();
            for (int i = 0; i < auto.length; i++) {
                addCandidate(candidates, seen, auto[i]);
            }
            for (int i = 0; i < candidates.size(); i++) {
                String path = candidates.get(i);
                File file = new File(path);
                if (file.isFile()) {
                    browserPath = file.getAbsolutePath();
                    browserResolveKey = resolveKey;
                    browserError = null;
                    AdvanceDataMonitor.LOG.info("[WebAE] Display capture browser: {}", browserPath);
                    return browserPath;
                }
            }
            browserPath = null;
            browserResolveKey = resolveKey;
            browserError = "browser_not_found";
            AdvanceDataMonitor.LOG.warn(
                "[WebAE] No Chrome/Edge/Chromium found for display capture. Set webConsole.webDisplayChromePath in textech.cfg or WEBAE_CHROME_PATH. Tried {} candidate path(s).",
                Integer.valueOf(candidates.size()));
            if (AdvanceDataMonitor.LOG.isDebugEnabled()) {
                for (int i = 0; i < candidates.size(); i++) {
                    AdvanceDataMonitor.LOG.debug("[WebAE]   candidate[{}]={}", Integer.valueOf(i), candidates.get(i));
                }
            }
            return null;
        }
    }

    /**
     * Auto-detect Chromium-family browsers. Order: fixed install dirs → Windows registry /
     * App Paths → PATH ({@code where}/{@code which}).
     */
    private static String[] candidateBrowsers() {
        String os = System.getProperty("os.name", "")
            .toLowerCase(Locale.ROOT);
        ArrayList<String> list = new ArrayList<String>();
        LinkedHashSet<String> seen = new LinkedHashSet<String>();
        if (os.contains("win")) {
            addWindowsFixedCandidates(list, seen);
            addCandidate(list, seen, readWindowsAppPath("chrome.exe"));
            addCandidate(list, seen, readWindowsAppPath("msedge.exe"));
            addCandidate(list, seen, readWindowsAppPath("chromium.exe"));
            addCandidate(list, seen, readWindowsAppPath("brave.exe"));
            addWindowsUninstallChrome(list, seen);
            addWhereResults(list, seen, new String[] { "chrome", "msedge", "chromium", "brave" });
        } else if (os.contains("mac")) {
            addCandidate(list, seen, "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome");
            addCandidate(list, seen, "/Applications/Google Chrome Beta.app/Contents/MacOS/Google Chrome Beta");
            addCandidate(list, seen, "/Applications/Chromium.app/Contents/MacOS/Chromium");
            addCandidate(list, seen, "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge");
            addCandidate(list, seen, "/Applications/Brave Browser.app/Contents/MacOS/Brave Browser");
            addWhichResults(list, seen, new String[] { "google-chrome", "chromium", "msedge", "brave" });
        } else {
            addCandidate(list, seen, "/usr/bin/google-chrome");
            addCandidate(list, seen, "/usr/bin/google-chrome-stable");
            addCandidate(list, seen, "/usr/bin/chromium");
            addCandidate(list, seen, "/usr/bin/chromium-browser");
            addCandidate(list, seen, "/snap/bin/chromium");
            addCandidate(list, seen, "/usr/bin/microsoft-edge");
            addCandidate(list, seen, "/usr/bin/microsoft-edge-stable");
            addCandidate(list, seen, "/usr/bin/brave-browser");
            addWhichResults(
                list,
                seen,
                new String[] { "google-chrome", "google-chrome-stable", "chromium", "chromium-browser",
                    "microsoft-edge", "brave-browser" });
        }
        return list.toArray(new String[list.size()]);
    }

    private static void addWindowsFixedCandidates(ArrayList<String> list, LinkedHashSet<String> seen) {
        // ProgramW6432 is the real 64-bit Program Files when the JVM is 32-bit.
        String[] roots = new String[] { System.getenv("ProgramW6432"), System.getenv("ProgramFiles"),
            System.getenv("ProgramFiles(x86)"), System.getenv("LOCALAPPDATA"), "C:\\Program Files",
            "C:\\Program Files (x86)", };
        String[] relative = new String[] { "Google\\Chrome\\Application\\chrome.exe",
            "Google\\Chrome Beta\\Application\\chrome.exe", "Google\\Chrome SxS\\Application\\chrome.exe",
            "Chromium\\Application\\chrome.exe", "Microsoft\\Edge\\Application\\msedge.exe",
            "Microsoft\\Edge Beta\\Application\\msedge.exe", "BraveSoftware\\Brave-Browser\\Application\\brave.exe", };
        for (int r = 0; r < roots.length; r++) {
            String root = roots[r];
            if (root == null || root.isEmpty()) continue;
            for (int i = 0; i < relative.length; i++) {
                addCandidate(list, seen, root + "\\" + relative[i]);
            }
        }
        // Edge sometimes only ships a versioned msedge.exe; pick the newest Application\*\msedge.exe.
        addNewestEdgeUnder(list, seen, System.getenv("ProgramFiles(x86)"));
        addNewestEdgeUnder(list, seen, System.getenv("ProgramW6432"));
        addNewestEdgeUnder(list, seen, System.getenv("ProgramFiles"));
    }

    private static void addNewestEdgeUnder(ArrayList<String> list, LinkedHashSet<String> seen, String programFiles) {
        if (programFiles == null || programFiles.isEmpty()) return;
        File appDir = new File(programFiles, "Microsoft\\Edge\\Application");
        if (!appDir.isDirectory()) return;
        File[] children = appDir.listFiles();
        if (children == null) return;
        File best = null;
        for (int i = 0; i < children.length; i++) {
            File child = children[i];
            if (!child.isDirectory()) continue;
            File exe = new File(child, "msedge.exe");
            if (!exe.isFile()) continue;
            if (best == null || child.getName()
                .compareTo(
                    best.getParentFile()
                        .getName())
                > 0) {
                best = exe;
            }
        }
        if (best != null) {
            addCandidate(list, seen, best.getAbsolutePath());
        }
    }

    private static void addWindowsUninstallChrome(ArrayList<String> list, LinkedHashSet<String> seen) {
        String[] keys = new String[] { "HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\Google Chrome",
            "HKLM\\SOFTWARE\\WOW6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\Google Chrome",
            "HKCU\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\Google Chrome",
            "HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\Microsoft Edge",
            "HKLM\\SOFTWARE\\WOW6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\Microsoft Edge", };
        for (int i = 0; i < keys.length; i++) {
            String icon = queryRegValue(keys[i], "DisplayIcon");
            String cleaned = cleanWindowsDisplayIcon(icon);
            addCandidate(list, seen, cleaned);
            String loc = queryRegValue(keys[i], "InstallLocation");
            if (loc != null && !loc.isEmpty()) {
                addCandidate(list, seen, loc + "\\chrome.exe");
                addCandidate(list, seen, loc + "\\msedge.exe");
            }
        }
    }

    private static String readWindowsAppPath(String exeName) {
        String[] keys = new String[] { "HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\App Paths\\" + exeName,
            "HKLM\\SOFTWARE\\WOW6432Node\\Microsoft\\Windows\\CurrentVersion\\App Paths\\" + exeName,
            "HKCU\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\App Paths\\" + exeName, };
        for (int i = 0; i < keys.length; i++) {
            String path = queryRegDefault(keys[i]);
            if (path != null && !path.isEmpty()) {
                return path;
            }
        }
        return null;
    }

    private static String queryRegDefault(String key) {
        return queryRegValue(key, null);
    }

    private static String queryRegValue(String key, String valueName) {
        if (key == null || key.isEmpty()) return null;
        try {
            ArrayList<String> cmd = new ArrayList<String>();
            cmd.add("reg");
            cmd.add("query");
            cmd.add(key);
            if (valueName == null || valueName.isEmpty()) {
                cmd.add("/ve");
            } else {
                cmd.add("/v");
                cmd.add(valueName);
            }
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = readAllLimited(process.getInputStream(), 8192);
            waitFor(process, 3000L);
            return parseRegSzPath(output);
        } catch (Exception e) {
            return null;
        }
    }

    /** Parse the first REG_SZ path from {@code reg query} output. */
    static String parseRegSzPath(String output) {
        if (output == null || output.isEmpty()) return null;
        String marker = "REG_SZ";
        int idx = output.indexOf(marker);
        if (idx < 0) {
            marker = "REG_EXPAND_SZ";
            idx = output.indexOf(marker);
        }
        if (idx < 0) return null;
        String rest = output.substring(idx + marker.length())
            .trim();
        int end = rest.indexOf('\r');
        if (end < 0) end = rest.indexOf('\n');
        if (end >= 0) {
            rest = rest.substring(0, end)
                .trim();
        }
        return cleanWindowsDisplayIcon(rest);
    }

    static String cleanWindowsDisplayIcon(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        if (s.startsWith("\"") && s.indexOf('"', 1) > 1) {
            s = s.substring(1, s.indexOf('"', 1));
        }
        int comma = s.lastIndexOf(',');
        if (comma > 2 && s.substring(comma + 1)
            .trim()
            .matches("-?\\d+")) {
            s = s.substring(0, comma)
                .trim();
        }
        return s.isEmpty() ? null : s;
    }

    private static void addWhereResults(ArrayList<String> list, LinkedHashSet<String> seen, String[] names) {
        for (int i = 0; i < names.length; i++) {
            try {
                ProcessBuilder pb = new ProcessBuilder("where.exe", names[i]);
                pb.redirectErrorStream(true);
                Process process = pb.start();
                String output = readAllLimited(process.getInputStream(), 4096);
                waitFor(process, 3000L);
                addPathsFromLines(list, seen, output);
            } catch (Exception ignored) {}
        }
    }

    private static void addWhichResults(ArrayList<String> list, LinkedHashSet<String> seen, String[] names) {
        for (int i = 0; i < names.length; i++) {
            try {
                ProcessBuilder pb = new ProcessBuilder("which", names[i]);
                pb.redirectErrorStream(true);
                Process process = pb.start();
                String output = readAllLimited(process.getInputStream(), 2048);
                waitFor(process, 3000L);
                addPathsFromLines(list, seen, output);
            } catch (Exception ignored) {}
        }
    }

    private static void addPathsFromLines(ArrayList<String> list, LinkedHashSet<String> seen, String output) {
        if (output == null || output.isEmpty()) return;
        String[] lines = output.split("\r?\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            if (line.indexOf("INFO:") >= 0 || line.indexOf("Could not") >= 0) continue;
            addCandidate(list, seen, line);
        }
    }

    private static void addCandidate(ArrayList<String> list, LinkedHashSet<String> seen, String path) {
        if (path == null) return;
        String trimmed = path.trim();
        if (trimmed.isEmpty()) return;
        if (!seen.add(trimmed)) return;
        list.add(trimmed);
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private static String envOr(String key, String fallback) {
        String v = System.getenv(key);
        return v != null && !v.isEmpty() ? v : fallback;
    }

    private void pruneStale() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> it = lastTouch.entrySet()
            .iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> e = it.next();
            if (now - e.getValue()
                .longValue() > STALE_MS) {
                it.remove();
                frames.remove(e.getKey());
            }
        }
    }

    private static int normalizeWidth(int width) {
        if (width <= 256) return 256;
        if (width <= 512) return 512;
        return 1024;
    }

    private static BufferedImage scaleIfNeeded(BufferedImage src, int width, int height) {
        if (src.getWidth() == width && src.getHeight() == height) return src;
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static byte[] toJpeg(BufferedImage image, float quality) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
            if (!writers.hasNext()) {
                ImageIO.write(image, "jpg", baos);
                return baos.toByteArray();
            }
            ImageWriter writer = writers.next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality);
            }
            ImageOutputStream ios = ImageIO.createImageOutputStream(baos);
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
            ios.close();
            writer.dispose();
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", Integer.valueOf(b & 0xff)));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(data.length);
        }
    }

    private static void drainQuietly(InputStream in) {
        if (in == null) return;
        byte[] buf = new byte[2048];
        try {
            while (in.read(buf) >= 0) {
                // discard
            }
        } catch (Exception ignored) {}
    }

    private static boolean waitFor(Process process, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                process.exitValue();
                return true;
            } catch (IllegalThreadStateException e) {
                Thread.sleep(100L);
            }
        }
        return false;
    }

    private static final class CachedFrame {

        final byte[] jpeg;
        final String etag;
        final int width;
        final long capturedAt;
        final String source;

        CachedFrame(byte[] jpeg, String etag, int width, long capturedAt) {
            this(jpeg, etag, width, capturedAt, SOURCE_SPA_JPEG);
        }

        CachedFrame(byte[] jpeg, String etag, int width, long capturedAt, String source) {
            this.jpeg = jpeg;
            this.etag = etag;
            this.width = width;
            this.capturedAt = capturedAt;
            this.source = source == null || source.isEmpty() ? SOURCE_SPA_JPEG : source;
        }
    }

    public static final class FrameResult {

        public final byte[] jpeg;
        public final String etag;
        public final boolean notModified;
        public final String error;
        public final String source;

        private FrameResult(byte[] jpeg, String etag, boolean notModified, String error, String source) {
            this.jpeg = jpeg;
            this.etag = etag;
            this.notModified = notModified;
            this.error = error;
            this.source = source;
        }

        public static FrameResult ok(byte[] jpeg, String etag) {
            return ok(jpeg, etag, SOURCE_SPA_JPEG);
        }

        public static FrameResult ok(byte[] jpeg, String etag, String source) {
            return new FrameResult(jpeg, etag, false, null, source);
        }

        public static FrameResult notModified(String etag) {
            return new FrameResult(null, etag, true, null, null);
        }

        public static FrameResult error(String error) {
            return new FrameResult(null, null, false, error, null);
        }
    }
}
