package com.imgood.textech.webae.chat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.TeXTechDataDir;
import com.imgood.textech.webae.util.AsyncJsonFileSaver;

/**
 * Server-side ring buffer of recent chat messages for the WebAE
 * {@code /api/chat/*} endpoints. Keeps the most recent {@code capacity}
 * messages (default 200) in memory and optionally persists to
 * {@code TeXTech/WebAE/web-chat.json} so history survives restarts.
 *
 * <p>
 * Thread-safety: all public methods are synchronized on this instance.
 * The HTTP handler threads and the ServerChatEvent handler thread both
 * access this store.
 * </p>
 */
public class ChatMessageStore {

    private static final ChatMessageStore INSTANCE = new ChatMessageStore();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .create();

    private static File storeFile() {
        return TeXTechDataDir.webAeFile("web-chat.json");
    }

    private static final int DEFAULT_CAPACITY = 200;

    private final Deque<ChatMessage> buffer = new ArrayDeque<ChatMessage>();
    private final int capacity;
    private long nextId = 1;
    private boolean loaded;

    /** Async file saver that writes JSON to disk on a background daemon thread. */
    private static final AsyncJsonFileSaver fileSaver = new AsyncJsonFileSaver("ChatMessages");

    private ChatMessageStore() {
        this.capacity = DEFAULT_CAPACITY;
    }

    public static ChatMessageStore instance() {
        return INSTANCE;
    }

    /** Append a new message and return the stored copy (with assigned id/timestamp). */
    public synchronized ChatMessage append(String senderUuid, String senderName, String content, long timestamp,
        String source) {
        ensureLoaded();
        ChatMessage msg = new ChatMessage(nextId++, senderUuid, senderName, content, timestamp, source);
        buffer.addLast(msg);
        while (buffer.size() > capacity) {
            buffer.pollFirst();
        }
        scheduleSave();
        return msg;
    }

    /** Append a message with one bounded screenshot attachment. */
    public synchronized ChatMessage appendAttachment(String senderUuid, String senderName, String content,
        long timestamp, String source, String attachmentId, String attachmentName, String attachmentMime,
        int attachmentWidth, int attachmentHeight, int attachmentBytes) {
        ensureLoaded();
        ChatMessage msg = new ChatMessage(nextId++, senderUuid, senderName, content, timestamp, source).withAttachment(
            attachmentId,
            attachmentName,
            attachmentMime,
            attachmentWidth,
            attachmentHeight,
            attachmentBytes);
        buffer.addLast(msg);
        while (buffer.size() > capacity) buffer.pollFirst();
        scheduleSave();
        return msg;
    }

    /** @return up to {@code limit} most recent messages, oldest-first. */
    public synchronized List<ChatMessage> getRecent(int limit) {
        ensureLoaded();
        if (limit <= 0) limit = DEFAULT_CAPACITY;
        List<ChatMessage> out = new ArrayList<ChatMessage>();
        int skip = Math.max(0, buffer.size() - limit);
        int i = 0;
        Iterator<ChatMessage> iter = buffer.iterator();
        while (iter.hasNext()) {
            ChatMessage msg = iter.next();
            if (i < skip) {
                i++;
                continue;
            }
            out.add(msg);
            i++;
        }
        return out;
    }

    /** @return all messages with {@code timestamp >= sinceTs}, oldest-first. */
    public synchronized List<ChatMessage> getSince(long sinceTs) {
        ensureLoaded();
        List<ChatMessage> out = new ArrayList<ChatMessage>();
        for (ChatMessage msg : buffer) {
            if (msg.timestamp >= sinceTs) {
                out.add(msg);
            }
        }
        return out;
    }

    /**
     * @return all messages with {@code id > afterId}, oldest-first. 用于前端按
     *         单调递增 id 增量拉取，避免 {@link #getSince(long)} 因时间戳回退或
     *         客户端时钟漂移导致重复返回历史消息。
     */
    public synchronized List<ChatMessage> getAfterId(long afterId) {
        ensureLoaded();
        List<ChatMessage> out = new ArrayList<ChatMessage>();
        for (ChatMessage msg : buffer) {
            if (msg.id > afterId) {
                out.add(msg);
            }
        }
        return out;
    }

    /** @return the most recent message id, or 0 if the buffer is empty. */
    public synchronized long latestId() {
        ChatMessage last = buffer.peekLast();
        return last != null ? last.id : 0L;
    }

    /** @return the most recent message timestamp, or 0 if the buffer is empty. */
    public synchronized long latestTimestamp() {
        ChatMessage last = buffer.peekLast();
        return last != null ? last.timestamp : 0L;
    }

    // ----- persistence -----

    private volatile boolean dirty;
    private long nextScheduledSaveAt;
    private static final long SAVE_DEBOUNCE_MS = 2000L;

    public synchronized void scheduleSave() {
        dirty = true;
        long now = System.currentTimeMillis();
        if (nextScheduledSaveAt == 0) {
            nextScheduledSaveAt = now + SAVE_DEBOUNCE_MS;
        }
    }

    public synchronized void tickSave(long now) {
        if (!dirty) return;
        if (now < nextScheduledSaveAt) return;
        saveNow();
        dirty = false;
        nextScheduledSaveAt = 0;
    }

    public synchronized void saveNow() {
        // Snapshot data on the main thread (fast, ~μs) and delegate file I/O
        // to the background daemon thread to avoid disk write stalls on the tick.
        List<ChatMessage> snapshot = new ArrayList<ChatMessage>(buffer);
        fileSaver.schedule(snapshot, storeFile());
    }

    private synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        if (!storeFile().isFile()) return;
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(storeFile()));
            List<ChatMessage> loaded = GSON.fromJson(reader, new TypeToken<List<ChatMessage>>() {}.getType());
            if (loaded != null) {
                long maxId = 0;
                for (ChatMessage msg : loaded) {
                    if (msg == null) continue;
                    buffer.addLast(msg);
                    if (msg.id > maxId) maxId = msg.id;
                }
                while (buffer.size() > capacity) {
                    buffer.pollFirst();
                }
                nextId = maxId + 1;
            }
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to load chat message store: {}", e.getMessage());
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {}
            }
        }
    }
}
