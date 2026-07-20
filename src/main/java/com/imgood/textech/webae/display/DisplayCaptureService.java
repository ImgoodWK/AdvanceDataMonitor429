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
 * Captures published embed dashboards via system Chromium/Chrome/Edge headless screenshot.
 * Runs off the Minecraft tick thread; shares one capture worker and caches JPEG frames.
 */
public final class DisplayCaptureService {

    private static final DisplayCaptureService INSTANCE = new DisplayCaptureService();
    private static final int MAX_ACTIVE = 4;
    private static final long STALE_MS = 60_000L;
    private static final long MIN_INTERVAL_MS = 500L;

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
            if (System.currentTimeMillis() - cached.capturedAt < Math.max(MIN_INTERVAL_MS, refreshBudgetMs(record.id))) {
                return FrameResult.ok(cached.jpeg, cached.etag);
            }
        }
        scheduleCapture(record, w, true);
        if (cached != null && cached.jpeg != null && cached.jpeg.length > 0) {
            if (ifNoneMatch != null && ifNoneMatch.equals(cached.etag)) {
                return FrameResult.notModified(cached.etag);
            }
            return FrameResult.ok(cached.jpeg, cached.etag);
        }
        // First frame: try synchronous capture with short wait via direct call on worker-less path
        FrameResult sync = captureNow(record, w);
        if (sync.jpeg != null) return sync;
        return FrameResult.error(sync.error != null ? sync.error : (browserError != null ? browserError : "capture_pending"));
    }

    private long refreshBudgetMs(String displayId) {
        Long touch = lastTouch.get(displayId);
        if (touch == null) return 5000L;
        long age = System.currentTimeMillis() - touch.longValue();
        if (age < 5_000L) return 500L;
        if (age < 30_000L) return 1000L;
        return 2500L;
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
        String browser = resolveBrowser();
        if (browser == null) {
            return FrameResult.error(browserError != null ? browserError : "browser_not_found");
        }
        File tmpDir = TeXTechDataDir.webAeDir("display-frames");
        if (!tmpDir.isDirectory() && !tmpDir.mkdirs()) {
            return FrameResult.error("frame_dir_failed");
        }
        File pngFile = new File(tmpDir, record.id + "-" + width + ".png");
        String url = buildEmbedUrl(record);
        int height = Math.max(64, (int) Math.round(width * (double) record.viewportHeight / Math.max(1, record.viewportWidth)));
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(
                browser,
                "--headless=new",
                "--disable-gpu",
                "--hide-scrollbars",
                "--no-first-run",
                "--no-default-browser-check",
                "--disable-extensions",
                "--window-size=" + width + "," + height,
                "--screenshot=" + pngFile.getAbsolutePath(),
                "--virtual-time-budget=8000",
                url);
            pb.redirectErrorStream(true);
            process = pb.start();
            drainQuietly(process.getInputStream());
            boolean finished = waitFor(process, 25_000L);
            if (!finished) {
                process.destroy();
                return FrameResult.error("capture_timeout");
            }
            if (!pngFile.isFile()) {
                return FrameResult.error("screenshot_missing");
            }
            BufferedImage image = ImageIO.read(pngFile);
            if (image == null) return FrameResult.error("screenshot_unreadable");
            byte[] jpeg = toJpeg(scaleIfNeeded(image, width, height), 0.72F);
            if (jpeg == null || jpeg.length == 0) return FrameResult.error("jpeg_failed");
            String etag = "\"" + sha256Hex(jpeg) + "\"";
            frames.put(record.id, new CachedFrame(jpeg, etag, width, System.currentTimeMillis()));
            return FrameResult.ok(jpeg, etag);
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Display capture failed for {}: {}", record.id, e.toString());
            return FrameResult.error("capture_failed");
        } finally {
            if (process != null) {
                try {
                    process.destroy();
                } catch (Exception ignored) {}
            }
            if (pngFile.isFile()) {
                // keep last png for debug; delete old ones lazily
            }
        }
    }

    private static String buildEmbedUrl(DisplayRecord record) {
        String host = Config.webConsoleBindAddress;
        if (host == null || host.isEmpty() || "0.0.0.0".equals(host)) {
            host = "127.0.0.1";
        }
        return "http://" + host + ":" + Config.webConsolePort + "/embed/dashboard/" + record.id + "?token="
            + record.viewToken + "&capture=1";
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
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String pf = System.getenv("ProgramFiles");
            String pf86 = System.getenv("ProgramFiles(x86)");
            String local = System.getenv("LOCALAPPDATA");
            return new String[] {
                envOr("WEBAE_CHROME_PATH", null),
                pf != null ? pf + "\\Google\\Chrome\\Application\\chrome.exe" : null,
                pf86 != null ? pf86 + "\\Google\\Chrome\\Application\\chrome.exe" : null,
                local != null ? local + "\\Google\\Chrome\\Application\\chrome.exe" : null,
                pf != null ? pf + "\\Microsoft\\Edge\\Application\\msedge.exe" : null,
                pf86 != null ? pf86 + "\\Microsoft\\Edge\\Application\\msedge.exe" : null,
                "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
                "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
            };
        }
        if (os.contains("mac")) {
            return new String[] {
                envOr("WEBAE_CHROME_PATH", null),
                "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
                "/Applications/Chromium.app/Contents/MacOS/Chromium",
                "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge",
            };
        }
        return new String[] {
            envOr("WEBAE_CHROME_PATH", null),
            "/usr/bin/google-chrome",
            "/usr/bin/google-chrome-stable",
            "/usr/bin/chromium",
            "/usr/bin/chromium-browser",
            "/snap/bin/chromium",
        };
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
