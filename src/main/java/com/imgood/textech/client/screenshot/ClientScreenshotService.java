package com.imgood.textech.client.screenshot;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.nio.IntBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.gui.guiscreen.GuiScreenshotGallery;
import com.imgood.textech.webae.network.PacketScreenshotUpload;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Client-only framebuffer screenshots, local history, and throttled sharing.
 *
 * <p>
 * The capture source is Minecraft's framebuffer, never an operating-system desktop API. Pixel readback must run
 * on the render thread; resize/JPEG encoding, history cleanup, and file reads run on one bounded daemon worker.
 * </p>
 */
@SideOnly(Side.CLIENT)
public final class ClientScreenshotService {

    public static final int UPLOAD_CHUNK_BYTES = 24 * 1024;
    private static final long UPLOAD_TIMEOUT_MS = 180000L;
    private static final ClientScreenshotService INSTANCE = new ClientScreenshotService();
    private static final SimpleDateFormat FILE_TIME = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss.SSS");

    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(
        1,
        1,
        30L,
        TimeUnit.SECONDS,
        new ArrayBlockingQueue<Runnable>(2),
        new ThreadFactory() {

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "TeXTech-Screenshot-Client");
                thread.setDaemon(true);
                return thread;
            }
        },
        new ThreadPoolExecutor.AbortPolicy());

    private IntBuffer pixelBuffer;
    private int[] pixelValues;
    private volatile boolean captureBusy;
    private volatile boolean uploadPreparing;
    private volatile PendingUpload pendingUpload;

    private ClientScreenshotService() {}

    public static ClientScreenshotService instance() {
        return INSTANCE;
    }

    /** Capture the current rendered game window, including any open Minecraft GUI. */
    public void capture() {
        final Minecraft mc = Minecraft.getMinecraft();
        if (!Config.webScreenshotEnabled) {
            notify("adm.screenshot.disabled", EnumChatFormatting.RED);
            return;
        }
        if (mc == null || mc.getFramebuffer() == null || mc.displayWidth <= 0 || mc.displayHeight <= 0) {
            notify("adm.screenshot.capture.failed", EnumChatFormatting.RED, "framebuffer unavailable");
            return;
        }
        if (captureBusy) {
            notify("adm.screenshot.capture.busy", EnumChatFormatting.YELLOW);
            return;
        }
        captureBusy = true;
        final BufferedImage raw;
        try {
            raw = readFramebuffer(mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
        } catch (Throwable error) {
            captureBusy = false;
            AdvanceDataMonitor.LOG.warn("[Screenshot] Framebuffer capture failed", error);
            notify("adm.screenshot.capture.failed", EnumChatFormatting.RED, safeMessage(error));
            return;
        }
        try {
            worker.execute(new Runnable() {

                @Override
                public void run() {
                    encodeCapture(raw);
                }
            });
        } catch (RuntimeException error) {
            captureBusy = false;
            notify("adm.screenshot.capture.busy", EnumChatFormatting.YELLOW);
        }
    }

    public boolean isCaptureBusy() {
        return captureBusy;
    }

    public boolean isUploadBusy() {
        return uploadPreparing || pendingUpload != null;
    }

    public File historyDirectory() {
        Minecraft mc = Minecraft.getMinecraft();
        File instance = mc == null ? new File(".") : mc.mcDataDir;
        File dir = new File(new File(instance, "TeXTech"), "Screenshots");
        if (!dir.exists() && !dir.mkdirs()) {
            AdvanceDataMonitor.LOG.warn("[Screenshot] Failed to create local history {}", dir.getAbsolutePath());
        }
        return dir;
    }

    /** Newest first. */
    public List<File> listHistory() {
        File[] files = historyDirectory().listFiles();
        if (files == null || files.length == 0) return new ArrayList<File>();
        List<File> result = new ArrayList<File>();
        for (File file : files) {
            if (file.isFile() && file.getName()
                .startsWith("textech_")
                && file.getName()
                    .toLowerCase()
                    .endsWith(".jpg")) {
                result.add(file);
            }
        }
        java.util.Collections.sort(result, NEWEST_FIRST);
        return result;
    }

    /** Resolve a 1-based history index; 1 is the newest screenshot. */
    public File resolveHistory(int oneBasedIndex) {
        List<File> history = listHistory();
        int index = Math.max(1, oneBasedIndex) - 1;
        return index < history.size() ? history.get(index) : null;
    }

    public void openGallery(int oneBasedIndex) {
        List<File> history = listHistory();
        if (history.isEmpty()) {
            notify("adm.screenshot.history.empty", EnumChatFormatting.YELLOW);
            return;
        }
        int index = Math.max(0, Math.min(history.size() - 1, Math.max(1, oneBasedIndex) - 1));
        Minecraft.getMinecraft()
            .displayGuiScreen(new GuiScreenshotGallery(history, index));
    }

    /**
     * Queue one local screenshot for WebAE chat or QQ delivery. Only one upload may be active per client.
     */
    public void queueUpload(final String destination, final String targetType, final String targetId, int oneBasedIndex,
        final String caption) {
        File file = resolveHistory(oneBasedIndex);
        if (file == null) {
            notify("adm.screenshot.history.not_found", EnumChatFormatting.RED, Integer.valueOf(oneBasedIndex));
            return;
        }
        queueUpload(destination, targetType, targetId, file, caption);
    }

    /** Queue a specific history file, used by the gallery so a new capture cannot shift its selected index. */
    public void queueUpload(final String destination, final String targetType, final String targetId, final File file,
        final String caption) {
        if (!Config.webScreenshotEnabled) {
            notify("adm.screenshot.disabled", EnumChatFormatting.RED);
            return;
        }
        if (uploadPreparing || pendingUpload != null) {
            notify("adm.screenshot.upload.busy", EnumChatFormatting.YELLOW);
            return;
        }
        if (file == null || !file.isFile()
            || !file.getParentFile()
                .equals(historyDirectory())) {
            notify("adm.screenshot.history.invalid", EnumChatFormatting.RED);
            return;
        }
        uploadPreparing = true;
        try {
            worker.execute(new Runnable() {

                @Override
                public void run() {
                    prepareUpload(file, destination, targetType, targetId, caption);
                }
            });
            notify("adm.screenshot.upload.preparing", EnumChatFormatting.AQUA, file.getName());
        } catch (RuntimeException error) {
            uploadPreparing = false;
            notify("adm.screenshot.upload.busy", EnumChatFormatting.YELLOW);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        PendingUpload upload = pendingUpload;
        if (upload == null) return;
        long now = System.currentTimeMillis();
        if (now - upload.startedAtMs > UPLOAD_TIMEOUT_MS) {
            pendingUpload = null;
            notify("adm.screenshot.upload.timeout", EnumChatFormatting.RED);
            return;
        }
        int perTick = Math.max(1, Math.min(4, Config.webScreenshotUploadChunksPerTick));
        for (int sent = 0; sent < perTick && upload.nextChunk < upload.totalChunks; sent++) {
            int index = upload.nextChunk++;
            int offset = index * UPLOAD_CHUNK_BYTES;
            int length = Math.min(UPLOAD_CHUNK_BYTES, upload.bytes.length - offset);
            byte[] chunk = Arrays.copyOfRange(upload.bytes, offset, offset + length);
            AdvanceDataMonitor.ADMCHANEL.sendToServer(
                new PacketScreenshotUpload(
                    upload.uploadId,
                    index,
                    upload.totalChunks,
                    upload.bytes.length,
                    upload.destination,
                    upload.targetType,
                    upload.targetId,
                    upload.caption,
                    upload.fileName,
                    upload.width,
                    upload.height,
                    chunk));
        }
    }

    /** Called by the S→C acknowledgement packet. */
    public void onUploadAck(String uploadId, boolean success, String message, String attachmentId) {
        PendingUpload upload = pendingUpload;
        if (upload == null || !upload.uploadId.equals(uploadId)) return;
        pendingUpload = null;
        if (success) {
            notify(
                "adm.screenshot.upload.success",
                EnumChatFormatting.GREEN,
                "qq".equals(upload.destination) ? "QQ" : "WebAE",
                upload.fileName);
        } else {
            notify("adm.screenshot.upload.failed", EnumChatFormatting.RED, message == null ? "unknown" : message);
        }
    }

    private void encodeCapture(BufferedImage raw) {
        File target = null;
        try {
            BufferedImage image = scaleToLimits(raw, Config.webScreenshotMaxWidth, Config.webScreenshotMaxHeight);
            target = uniqueTarget();
            writeJpeg(image, target, Config.webScreenshotJpegQualityPercent / 100.0F);
            cleanupHistory();
            AdvanceDataMonitor.LOG.info(
                "[Screenshot] Saved local framebuffer capture {} ({}x{}, {} bytes)",
                target.getAbsolutePath(),
                image.getWidth(),
                image.getHeight(),
                target.length());
            notify(
                "adm.screenshot.capture.saved",
                EnumChatFormatting.GREEN,
                target.getName(),
                Integer.valueOf(image.getWidth()),
                Integer.valueOf(image.getHeight()),
                Long.valueOf(target.length() / 1024L));
        } catch (Throwable error) {
            AdvanceDataMonitor.LOG.warn("[Screenshot] JPEG encoding failed", error);
            notify("adm.screenshot.capture.failed", EnumChatFormatting.RED, safeMessage(error));
            if (target != null && target.isFile() && target.length() == 0L) target.delete();
        } finally {
            captureBusy = false;
        }
    }

    private void prepareUpload(File file, String destination, String targetType, String targetId, String caption) {
        try {
            long maxBytes = Math.max(64L, Config.webScreenshotMaxUploadKB) * 1024L;
            if (file.length() <= 0L || file.length() > maxBytes || file.length() > Integer.MAX_VALUE) {
                notify(
                    "adm.screenshot.upload.too_large",
                    EnumChatFormatting.RED,
                    Long.valueOf(file.length() / 1024L),
                    Integer.valueOf(Config.webScreenshotMaxUploadKB));
                return;
            }
            byte[] bytes = readFile(file);
            BufferedImage image = ImageIO.read(file);
            if (image == null) throw new IllegalStateException("invalid JPEG");
            String uploadId = UUID.randomUUID()
                .toString()
                .replace("-", "");
            int totalChunks = Math.max(1, (bytes.length + UPLOAD_CHUNK_BYTES - 1) / UPLOAD_CHUNK_BYTES);
            pendingUpload = new PendingUpload(
                uploadId,
                destination,
                targetType,
                targetId,
                bounded(caption, 256),
                file.getName(),
                image.getWidth(),
                image.getHeight(),
                bytes,
                totalChunks);
            notify(
                "adm.screenshot.upload.started",
                EnumChatFormatting.AQUA,
                file.getName(),
                Integer.valueOf(totalChunks));
        } catch (Throwable error) {
            AdvanceDataMonitor.LOG.warn("[Screenshot] Failed to prepare upload {}", file.getAbsolutePath(), error);
            notify("adm.screenshot.upload.failed", EnumChatFormatting.RED, safeMessage(error));
        } finally {
            uploadPreparing = false;
        }
    }

    private BufferedImage readFramebuffer(int requestedWidth, int requestedHeight, Framebuffer framebuffer) {
        int width = requestedWidth;
        int height = requestedHeight;
        if (OpenGlHelper.isFramebufferEnabled()) {
            width = framebuffer.framebufferTextureWidth;
            height = framebuffer.framebufferTextureHeight;
        }
        int pixels = width * height;
        if (pixelBuffer == null || pixelBuffer.capacity() < pixels) {
            pixelBuffer = BufferUtils.createIntBuffer(pixels);
            pixelValues = new int[pixels];
        }
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        pixelBuffer.clear();
        if (OpenGlHelper.isFramebufferEnabled()) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, framebuffer.framebufferTexture);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixelBuffer);
        } else {
            GL11.glReadPixels(0, 0, width, height, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixelBuffer);
        }
        pixelBuffer.get(pixelValues, 0, pixels);
        TextureUtil.func_147953_a(pixelValues, width, height);
        if (OpenGlHelper.isFramebufferEnabled()) {
            BufferedImage image = new BufferedImage(
                framebuffer.framebufferWidth,
                framebuffer.framebufferHeight,
                BufferedImage.TYPE_INT_RGB);
            int top = framebuffer.framebufferTextureHeight - framebuffer.framebufferHeight;
            for (int y = top; y < framebuffer.framebufferTextureHeight; y++) {
                image.setRGB(
                    0,
                    y - top,
                    framebuffer.framebufferWidth,
                    1,
                    pixelValues,
                    y * framebuffer.framebufferTextureWidth,
                    framebuffer.framebufferTextureWidth);
            }
            return image;
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, width, height, pixelValues, 0, width);
        return image;
    }

    private static BufferedImage scaleToLimits(BufferedImage source, int maxWidth, int maxHeight) {
        int boundedWidth = Math.max(320, maxWidth);
        int boundedHeight = Math.max(180, maxHeight);
        double scale = Math.min(
            1.0D,
            Math.min((double) boundedWidth / source.getWidth(), (double) boundedHeight / source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        if (width == source.getWidth() && height == source.getHeight()
            && source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return scaled;
    }

    private static void writeJpeg(BufferedImage image, File target, float quality) throws Exception {
        java.util.Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) throw new IllegalStateException("JPEG writer unavailable");
        ImageWriter writer = writers.next();
        FileImageOutputStream output = new FileImageOutputStream(target);
        try {
            writer.setOutput(output);
            ImageWriteParam params = writer.getDefaultWriteParam();
            if (params.canWriteCompressed()) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(Math.max(0.30F, Math.min(1.0F, quality)));
            }
            writer.write(null, new IIOImage(image, null, null), params);
        } finally {
            try {
                output.close();
            } finally {
                writer.dispose();
            }
        }
    }

    private File uniqueTarget() {
        File dir = historyDirectory();
        String base;
        synchronized (FILE_TIME) {
            base = "textech_" + FILE_TIME.format(new Date());
        }
        File target = new File(dir, base + ".jpg");
        int suffix = 2;
        while (target.exists()) target = new File(dir, base + "_" + suffix++ + ".jpg");
        return target;
    }

    private void cleanupHistory() {
        List<File> files = listHistory();
        int maxFiles = Math.max(1, Config.webScreenshotClientHistoryMaxFiles);
        long maxBytes = Math.max(16L, Config.webScreenshotClientHistoryMaxMB) * 1024L * 1024L;
        long keptBytes = 0L;
        for (int i = 0; i < files.size(); i++) {
            File file = files.get(i);
            boolean keep = i < maxFiles && keptBytes + file.length() <= maxBytes;
            if (keep) keptBytes += file.length();
            else if (!file.delete()) AdvanceDataMonitor.LOG.warn("[Screenshot] Failed to prune {}", file);
        }
    }

    private static byte[] readFile(File file) throws Exception {
        byte[] result = new byte[(int) file.length()];
        FileInputStream input = new FileInputStream(file);
        try {
            int offset = 0;
            while (offset < result.length) {
                int read = input.read(result, offset, result.length - offset);
                if (read < 0) break;
                offset += read;
            }
            if (offset != result.length) throw new IllegalStateException("short file read");
            return result;
        } finally {
            input.close();
        }
    }

    private static void notify(final String key, final EnumChatFormatting color, final Object... args) {
        final Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return;
        mc.func_152344_a(new Runnable() {

            @Override
            public void run() {
                if (mc.thePlayer == null) return;
                ChatComponentTranslation text = new ChatComponentTranslation(key, args);
                text.getChatStyle()
                    .setColor(color);
                mc.thePlayer.addChatMessage(text);
            }
        });
    }

    private static String bounded(String value, int max) {
        String safe = value == null ? "" : value.trim();
        return safe.length() <= max ? safe : safe.substring(0, max);
    }

    private static String safeMessage(Throwable error) {
        String value = error == null ? "" : error.getMessage();
        if (value == null || value.isEmpty()) return error == null ? "unknown"
            : error.getClass()
                .getSimpleName();
        return bounded(
            value.replace('\r', ' ')
                .replace('\n', ' '),
            160);
    }

    private static final Comparator<File> NEWEST_FIRST = new Comparator<File>() {

        @Override
        public int compare(File left, File right) {
            if (left.lastModified() == right.lastModified()) return right.getName()
                .compareTo(left.getName());
            return left.lastModified() < right.lastModified() ? 1 : -1;
        }
    };

    private static final class PendingUpload {

        final String uploadId;
        final String destination;
        final String targetType;
        final String targetId;
        final String caption;
        final String fileName;
        final int width;
        final int height;
        final byte[] bytes;
        final int totalChunks;
        final long startedAtMs = System.currentTimeMillis();
        int nextChunk;

        PendingUpload(String uploadId, String destination, String targetType, String targetId, String caption,
            String fileName, int width, int height, byte[] bytes, int totalChunks) {
            this.uploadId = uploadId;
            this.destination = destination;
            this.targetType = targetType;
            this.targetId = targetId;
            this.caption = caption;
            this.fileName = fileName;
            this.width = width;
            this.height = height;
            this.bytes = bytes;
            this.totalChunks = totalChunks;
        }
    }
}
