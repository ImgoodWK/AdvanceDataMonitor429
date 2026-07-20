package com.imgood.textech.webae.display;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Iterator;
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
 * Captures published dashboards via system Chromium/Chrome/Edge headless screenshot.
 * Prefers the real React embed ({@code /embed/dashboard}) so in-game monitors look like
 * the browser without requiring MCEF. Does <b>not</b> serve the compact {@code render.html}
 * page as a successful monitor frame (that endpoint remains for debug only).
 * Runs off the Minecraft tick thread.
 */
public final class DisplayCaptureService {

    private static final DisplayCaptureService INSTANCE = new DisplayCaptureService();
    private static final int MAX_ACTIVE = 1;
    private static final long STALE_MS = 60_000L;
    /** Minimum time between accepted Chrome captures per display (headless is expensive). */
    private static final long MIN_INTERVAL_MS = 2500L;
    /** Virtual time for SPA boot + capture=1 settle (~2.5s) + paint. */
    private static final long SPA_VIRTUAL_TIME_MS = 22_000L;
    private static final long SPA_WAIT_MS = 55_000L;
    private static final long SPA_READY_POLL_MS = 28_000L;
    private static final long SPA_READY_DUMP_BUDGET_MS = 12_000L;
    private static final long SPA_READY_DUMP_WAIT_MS = 35_000L;
    private static final int SPA_SCREENSHOT_ATTEMPTS = 2;
    private static final Object CAPTURE_LOCK = new Object();
    private static final int BLANK_VARIANCE_MAX = 12;

    private final ConcurrentHashMap<String, CachedFrame> frames = new ConcurrentHashMap<String, CachedFrame>();
    private final ConcurrentHashMap<String, Long> lastTouch = new ConcurrentHashMap<String, Long>();
    private final ConcurrentHashMap<String, Boolean> inFlight = new ConcurrentHashMap<String, Boolean>();
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
    }

    public FrameResult getOrCapture(DisplayRecord record, int width, String ifNoneMatch) {
        if (record == null) {
            return FrameResult.error("missing_display");
        }
        int w = normalizeWidth(width);
        lastTouch.put(record.id, Long.valueOf(System.currentTimeMillis()));
        CachedFrame cached = frames.get(record.id);
        if (cached != null && cached.width == w && cached.jpeg != null && cached.jpeg.length > 0) {
            if (ifNoneMatch != null && ifNoneMatch.equals(cached.etag)) {
                return FrameResult.notModified(cached.etag);
            }
            if (System.currentTimeMillis() - cached.capturedAt
                < Math.max(MIN_INTERVAL_MS, refreshBudgetMs(record.id))) {
                return FrameResult.ok(cached.jpeg, cached.etag);
            }
        }
        // Never launch Chrome on the HTTP thread — that raced with the worker and
        // flooded localhost until headless got ERR_CONNECTION_REFUSED / blank frames.
        scheduleCapture(record, w, cached == null);
        if (cached != null && cached.jpeg != null && cached.jpeg.length > 0) {
            if (ifNoneMatch != null && ifNoneMatch.equals(cached.etag)) {
                return FrameResult.notModified(cached.etag);
            }
            return FrameResult.ok(cached.jpeg, cached.etag);
        }
        CachedFrame waited = awaitFrame(record.id, w, 20_000L);
        if (waited != null && waited.jpeg != null && waited.jpeg.length > 0) {
            if (ifNoneMatch != null && ifNoneMatch.equals(waited.etag)) {
                return FrameResult.notModified(waited.etag);
            }
            return FrameResult.ok(waited.jpeg, waited.etag);
        }
        return FrameResult.error(browserError != null ? browserError : "capture_pending");
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
        String browser = resolveBrowser();
        if (browser == null) {
            return FrameResult.error(browserError != null ? browserError : "browser_not_found");
        }
        File tmpDir = TeXTechDataDir.webAeDir("display-frames");
        if (!tmpDir.isDirectory() && !tmpDir.mkdirs()) {
            return FrameResult.error("frame_dir_failed");
        }
        int height = Math
            .max(64, (int) Math.round(width * (double) record.viewportHeight / Math.max(1, record.viewportWidth)));

        String spaUrl = buildSpaCaptureUrl(record);
        boolean ready = waitForSpaCaptureReady(browser, spaUrl);
        if (!ready) {
            AdvanceDataMonitor.LOG.warn(
                "[WebAE] Display {} spa-timeout waiting for data-webae-capture-ready @ {}",
                record.id,
                spaUrl);
        }

        FrameResult last = FrameResult.error("spa-blank");
        int attempts = ready ? SPA_SCREENSHOT_ATTEMPTS : SPA_SCREENSHOT_ATTEMPTS + 1;
        for (int i = 0; i < attempts; i++) {
            long budget = SPA_VIRTUAL_TIME_MS + (long) i * 4_000L;
            FrameResult spa = captureUrl(browser, record, width, height, tmpDir, spaUrl, budget, SPA_WAIT_MS);
            if (spa.jpeg != null && spa.jpeg.length > 0) {
                AdvanceDataMonitor.LOG.info(
                    "[WebAE] Display {} spa-ok (attempt {}, ready={})",
                    record.id,
                    Integer.valueOf(i + 1),
                    Boolean.valueOf(ready));
                return spa;
            }
            last = spa;
            AdvanceDataMonitor.LOG.warn(
                "[WebAE] Display {} spa-blank/failed attempt {} ({}): {}",
                record.id,
                Integer.valueOf(i + 1),
                spa.error != null ? spa.error : "blank",
                spaUrl);
        }

        // Do not return compact render.html as a "successful" monitor frame — that looks wrong.
        AdvanceDataMonitor.LOG.warn(
            "[WebAE] Display {} no-frame after SPA attempts (last={}); render.html not used as monitor frame",
            record.id,
            last.error != null ? last.error : "unknown");
        return FrameResult.error(last.error != null ? last.error : "capture_failed");
    }

    /**
     * Poll headless {@code --dump-dom} until the embed marks {@code data-webae-capture-ready=1},
     * or until the ready budget expires.
     */
    private static boolean waitForSpaCaptureReady(String browser, String url) {
        long deadline = System.currentTimeMillis() + SPA_READY_POLL_MS;
        long budget = SPA_READY_DUMP_BUDGET_MS;
        while (System.currentTimeMillis() < deadline) {
            try {
                String dom = runChromeDumpDom(browser, url, budget, SPA_READY_DUMP_WAIT_MS);
                if (dom != null && dom.indexOf("data-webae-capture-ready=\"1\"") >= 0) {
                    return true;
                }
                if (dom != null && dom.indexOf("data-webae-capture-ready='1'") >= 0) {
                    return true;
                }
            } catch (Exception e) {
                AdvanceDataMonitor.LOG.debug("[WebAE] dump-dom ready poll failed: {}", e.toString());
            }
            budget = Math.min(budget + 4_000L, SPA_VIRTUAL_TIME_MS);
            try {
                Thread.sleep(400L);
            } catch (InterruptedException e) {
                Thread.currentThread()
                    .interrupt();
                return false;
            }
        }
        return false;
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
            frames.put(record.id, new CachedFrame(jpeg, etag, width, System.currentTimeMillis()));
            return FrameResult.ok(jpeg, etag);
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
        ProcessBuilder pb = new ProcessBuilder(
            browser,
            "--headless=new",
            "--disable-gpu",
            "--hide-scrollbars",
            "--no-first-run",
            "--no-default-browser-check",
            "--disable-extensions",
            "--disable-dev-shm-usage",
            "--run-all-compositor-stages-before-draw",
            "--window-size=" + width + "," + height,
            "--screenshot=" + pngFile.getAbsolutePath(),
            "--virtual-time-budget=" + virtualTimeBudgetMs,
            url);
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
        ProcessBuilder pb = new ProcessBuilder(
            browser,
            "--headless=new",
            "--disable-gpu",
            "--no-first-run",
            "--no-default-browser-check",
            "--disable-extensions",
            "--disable-dev-shm-usage",
            "--virtual-time-budget=" + virtualTimeBudgetMs,
            "--dump-dom",
            url);
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
        if (browserPath != null) return browserPath;
        synchronized (this) {
            if (browserPath != null) return browserPath;
            String[] candidates = candidateBrowsers();
            for (String path : candidates) {
                if (path != null && new File(path).isFile()) {
                    browserPath = path;
                    browserError = null;
                    AdvanceDataMonitor.LOG.info("[WebAE] Display capture browser: {}", path);
                    return browserPath;
                }
            }
            browserError = "browser_not_found";
            return null;
        }
    }

    private static String[] candidateBrowsers() {
        String os = System.getProperty("os.name", "")
            .toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String pf = System.getenv("ProgramFiles");
            String pf86 = System.getenv("ProgramFiles(x86)");
            String local = System.getenv("LOCALAPPDATA");
            return new String[] { envOr("WEBAE_CHROME_PATH", null),
                pf != null ? pf + "\\Google\\Chrome\\Application\\chrome.exe" : null,
                pf86 != null ? pf86 + "\\Google\\Chrome\\Application\\chrome.exe" : null,
                local != null ? local + "\\Google\\Chrome\\Application\\chrome.exe" : null,
                pf != null ? pf + "\\Microsoft\\Edge\\Application\\msedge.exe" : null,
                pf86 != null ? pf86 + "\\Microsoft\\Edge\\Application\\msedge.exe" : null,
                "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
                "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe", };
        }
        if (os.contains("mac")) {
            return new String[] { envOr("WEBAE_CHROME_PATH", null),
                "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
                "/Applications/Chromium.app/Contents/MacOS/Chromium",
                "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge", };
        }
        return new String[] { envOr("WEBAE_CHROME_PATH", null), "/usr/bin/google-chrome",
            "/usr/bin/google-chrome-stable", "/usr/bin/chromium", "/usr/bin/chromium-browser", "/snap/bin/chromium", };
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

        CachedFrame(byte[] jpeg, String etag, int width, long capturedAt) {
            this.jpeg = jpeg;
            this.etag = etag;
            this.width = width;
            this.capturedAt = capturedAt;
        }
    }

    public static final class FrameResult {

        public final byte[] jpeg;
        public final String etag;
        public final boolean notModified;
        public final String error;

        private FrameResult(byte[] jpeg, String etag, boolean notModified, String error) {
            this.jpeg = jpeg;
            this.etag = etag;
            this.notModified = notModified;
            this.error = error;
        }

        public static FrameResult ok(byte[] jpeg, String etag) {
            return new FrameResult(jpeg, etag, false, null);
        }

        public static FrameResult notModified(String etag) {
            return new FrameResult(null, etag, true, null);
        }

        public static FrameResult error(String error) {
            return new FrameResult(null, null, false, error);
        }
    }
}
