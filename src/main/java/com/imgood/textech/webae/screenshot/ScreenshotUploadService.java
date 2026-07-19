package com.imgood.textech.webae.screenshot;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import net.minecraft.entity.player.EntityPlayerMP;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.webae.chat.ChatMessage;
import com.imgood.textech.webae.chat.ChatMessageStore;
import com.imgood.textech.webae.network.PacketScreenshotUpload;
import com.imgood.textech.webae.network.PacketScreenshotUploadAck;
import com.imgood.textech.webae.qqbot.QqBotService;
import com.imgood.textech.webae.qqbot.QqBotService.ManualSendResult;
import com.imgood.textech.webae.screenshot.ScreenshotAttachmentStore.StoredAttachment;

import cpw.mods.fml.common.network.simpleimpl.IMessage;

/**
 * Bounded server ingress for client screenshots. No tick work is installed: stale sessions are cleaned only when a
 * new chunk arrives, while image inspection, disk I/O, and QQ work run on bounded background queues.
 */
public final class ScreenshotUploadService {

    private static final ScreenshotUploadService INSTANCE = new ScreenshotUploadService();
    private static final long SESSION_TTL_MS = 180000L;
    private static final int ABSOLUTE_MAX_CHUNKS = 512;

    private final Map<String, UploadSession> sessions = new LinkedHashMap<String, UploadSession>();
    private final Map<String, Long> lastStarts = new LinkedHashMap<String, Long>();
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(
        1,
        1,
        30L,
        TimeUnit.SECONDS,
        new ArrayBlockingQueue<Runnable>(8),
        new ThreadFactory() {

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "WebAE-Screenshot-Worker");
                thread.setDaemon(true);
                return thread;
            }
        },
        new ThreadPoolExecutor.AbortPolicy());
    private int processing;

    private ScreenshotUploadService() {}

    public static ScreenshotUploadService instance() {
        return INSTANCE;
    }

    /** Accept one sequential chunk and return an immediate terminal error when validation fails. */
    public synchronized IMessage accept(EntityPlayerMP player, PacketScreenshotUpload message) {
        String uploadId = message == null ? "" : safe(message.uploadId);
        try {
            long now = System.currentTimeMillis();
            cleanup(now);
            String playerUuid = player.getUniqueID().toString();
            String key = playerUuid + ":" + uploadId;
            if (!Config.webScreenshotEnabled || !Config.webConsoleEnabled) {
                return fail(uploadId, "Screenshot sharing is disabled on this server.");
            }
            if (message == null || message.malformed || !validUploadId(uploadId)) {
                return fail(uploadId, "Malformed screenshot packet.");
            }
            int maxBytes = Math.max(64, Config.webScreenshotMaxUploadKB) * 1024;
            int expectedChunks = Math.max(1,
                (message.totalBytes + PacketScreenshotUpload.MAX_CHUNK_BYTES - 1)
                    / PacketScreenshotUpload.MAX_CHUNK_BYTES);
            if (message.totalBytes <= 0 || message.totalBytes > maxBytes || message.totalChunks <= 0
                || message.totalChunks > ABSOLUTE_MAX_CHUNKS || message.totalChunks != expectedChunks
                || message.chunkIndex < 0 || message.chunkIndex >= message.totalChunks || message.chunk == null
                || message.chunk.length <= 0 || message.chunk.length > PacketScreenshotUpload.MAX_CHUNK_BYTES) {
                sessions.remove(key);
                return fail(uploadId, "Screenshot size or chunk metadata exceeds server limits.");
            }

            UploadSession session = sessions.get(key);
            if (message.chunkIndex == 0) {
                if (session != null) sessions.remove(key);
                if (!validDestination(message.destination)) return fail(uploadId, "Unknown screenshot destination.");
                if ("qq".equals(message.destination)
                    && !player.canCommandSenderUseCommand(2, "admweb")) {
                    return fail(uploadId, "OP permission is required for QQ screenshot delivery.");
                }
                if ("qq".equals(message.destination)
                    && (!("group".equals(message.targetType) || "c2c".equals(message.targetType))
                        || safe(message.targetId).isEmpty())) {
                    return fail(uploadId, "QQ target must be a group or c2c openid.");
                }
                Long last = lastStarts.get(playerUuid);
                long cooldown = Math.max(0L, Config.webScreenshotUploadCooldownSeconds) * 1000L;
                if (last != null && now - last.longValue() < cooldown) {
                    return fail(uploadId, "Screenshot upload cooldown is active.");
                }
                if (activeForPlayer(playerUuid) || sessions.size() + processing >= maxConcurrent()) {
                    return fail(uploadId, "Screenshot upload capacity is busy; try again later.");
                }
                if (message.width <= 0 || message.height <= 0 || message.width > Config.webScreenshotMaxWidth
                    || message.height > Config.webScreenshotMaxHeight) {
                    return fail(uploadId, "Screenshot dimensions exceed server limits.");
                }
                session = new UploadSession(player, message, now);
                sessions.put(key, session);
                lastStarts.put(playerUuid, Long.valueOf(now));
            } else if (session == null) {
                return fail(uploadId, "Screenshot upload session was not found.");
            }

            if (session.nextChunk != message.chunkIndex || session.totalChunks != message.totalChunks
                || session.totalBytes != message.totalBytes) {
                sessions.remove(key);
                return fail(uploadId, "Screenshot chunks arrived out of order.");
            }
            if (session.buffer.size() + message.chunk.length > session.totalBytes) {
                sessions.remove(key);
                return fail(uploadId, "Screenshot payload exceeds declared size.");
            }
            session.buffer.write(message.chunk, 0, message.chunk.length);
            session.nextChunk++;
            session.lastTouchedMs = now;
            if (session.nextChunk < session.totalChunks) return null;

            sessions.remove(key);
            if (session.buffer.size() != session.totalBytes) {
                return fail(uploadId, "Screenshot payload is incomplete.");
            }
            final UploadSession complete = session;
            final byte[] bytes = session.buffer.toByteArray();
            session.buffer = null;
            if (processing >= maxConcurrent() || worker.getQueue().size() >= maxConcurrent()) {
                return fail(uploadId, "Screenshot processing queue is full.");
            }
            processing++;
            try {
                worker.execute(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            process(complete, bytes);
                        } finally {
                            synchronized (ScreenshotUploadService.this) {
                                processing = Math.max(0, processing - 1);
                            }
                        }
                    }
                });
            } catch (RuntimeException error) {
                processing--;
                return fail(uploadId, "Screenshot processing queue rejected the upload.");
            }
            return null;
        } catch (Throwable error) {
            AdvanceDataMonitor.LOG.warn("[Screenshot] Upload chunk rejected", error);
            return fail(uploadId, safeMessage(error));
        }
    }

    private void process(UploadSession session, byte[] bytes) {
        try {
            ImageInfo info = inspectJpeg(bytes);
            if (info.width != session.width || info.height != session.height || info.width > Config.webScreenshotMaxWidth
                || info.height > Config.webScreenshotMaxHeight) {
                throw new IllegalArgumentException("JPEG dimensions do not match the upload metadata");
            }
            String attachmentId = "";
            if ("web".equals(session.destination)) {
                StoredAttachment stored = ScreenshotAttachmentStore.instance()
                    .saveJpeg(bytes, info.width, info.height, session.fileName);
                attachmentId = stored.id;
                ChatMessageStore.instance().appendAttachment(
                    session.playerUuid,
                    session.playerName,
                    session.caption,
                    System.currentTimeMillis(),
                    ChatMessage.SOURCE_GAME,
                    stored.id,
                    stored.fileName,
                    stored.mimeType,
                    stored.width,
                    stored.height,
                    stored.bytes);
            } else {
                ManualSendResult result = QqBotService.instance().sendManualImage(
                    session.targetType,
                    session.targetId,
                    session.caption,
                    bytes,
                    session.fileName);
                if (!result.success) throw new IllegalStateException(result.error);
            }
            sendAck(session.player, new PacketScreenshotUploadAck(
                session.uploadId,
                true,
                "qq".equals(session.destination) ? "Queued for QQ delivery." : "Published to WebAE chat.",
                attachmentId));
        } catch (Throwable error) {
            AdvanceDataMonitor.LOG.warn(
                "[Screenshot] Failed to process upload {} from {}",
                session.uploadId,
                session.playerName,
                error);
            sendAck(session.player, new PacketScreenshotUploadAck(session.uploadId, false, safeMessage(error), ""));
        }
    }

    private static ImageInfo inspectJpeg(byte[] bytes) throws Exception {
        ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes));
        if (input == null) throw new IllegalArgumentException("invalid image stream");
        ImageReader reader = null;
        try {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("unsupported image format");
            reader = readers.next();
            String format = reader.getFormatName();
            if (!("jpeg".equalsIgnoreCase(format) || "jpg".equalsIgnoreCase(format))) {
                throw new IllegalArgumentException("only JPEG screenshots are accepted");
            }
            reader.setInput(input, true, true);
            return new ImageInfo(reader.getWidth(0), reader.getHeight(0));
        } finally {
            if (reader != null) reader.dispose();
            input.close();
        }
    }

    private void cleanup(long now) {
        Iterator<Map.Entry<String, UploadSession>> iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            UploadSession session = iterator.next().getValue();
            if (now - session.lastTouchedMs > SESSION_TTL_MS) iterator.remove();
        }
        Iterator<Map.Entry<String, Long>> cooldowns = lastStarts.entrySet().iterator();
        while (cooldowns.hasNext()) {
            if (now - cooldowns.next().getValue().longValue() > 3600000L) cooldowns.remove();
        }
    }

    private boolean activeForPlayer(String playerUuid) {
        String prefix = playerUuid + ":";
        for (String key : sessions.keySet()) if (key.startsWith(prefix)) return true;
        return false;
    }

    private static int maxConcurrent() {
        return Math.max(1, Math.min(16, Config.webScreenshotMaxConcurrentUploads));
    }

    private static boolean validDestination(String value) {
        return "web".equals(value) || "qq".equals(value);
    }

    private static boolean validUploadId(String value) {
        if (value.length() != 32) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) return false;
        }
        return true;
    }

    private static PacketScreenshotUploadAck fail(String uploadId, String reason) {
        return new PacketScreenshotUploadAck(uploadId, false, reason, "");
    }

    private static void sendAck(EntityPlayerMP player, PacketScreenshotUploadAck ack) {
        try {
            if (player != null) AdvanceDataMonitor.ADMCHANEL.sendTo(ack, player);
        } catch (Throwable error) {
            AdvanceDataMonitor.LOG.debug("[Screenshot] Could not send upload acknowledgement", error);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String safeMessage(Throwable error) {
        String message = error == null ? "" : error.getMessage();
        if (message == null || message.isEmpty()) message = error == null ? "unknown" : error.getClass().getSimpleName();
        message = message.replace('\r', ' ').replace('\n', ' ').trim();
        return message.length() <= 240 ? message : message.substring(0, 240);
    }

    private static final class ImageInfo {

        final int width;
        final int height;

        ImageInfo(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    private static final class UploadSession {

        final EntityPlayerMP player;
        final String playerUuid;
        final String playerName;
        final String uploadId;
        final String destination;
        final String targetType;
        final String targetId;
        final String caption;
        final String fileName;
        final int width;
        final int height;
        final int totalChunks;
        final int totalBytes;
        ByteArrayOutputStream buffer;
        int nextChunk;
        long lastTouchedMs;

        UploadSession(EntityPlayerMP player, PacketScreenshotUpload message, long now) {
            this.player = player;
            this.playerUuid = player.getUniqueID().toString();
            this.playerName = player.getCommandSenderName();
            this.uploadId = message.uploadId;
            this.destination = message.destination;
            this.targetType = safe(message.targetType);
            this.targetId = safe(message.targetId);
            this.caption = bounded(message.caption, 256);
            this.fileName = bounded(message.fileName, 96);
            this.width = message.width;
            this.height = message.height;
            this.totalChunks = message.totalChunks;
            this.totalBytes = message.totalBytes;
            this.buffer = new ByteArrayOutputStream(Math.min(message.totalBytes, 128 * 1024));
            this.lastTouchedMs = now;
        }

        private static String bounded(String value, int max) {
            String safe = value == null ? "" : value.trim();
            return safe.length() <= max ? safe : safe.substring(0, max);
        }
    }
}
