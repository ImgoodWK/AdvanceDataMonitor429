package com.imgood.textech.webae.chat;

/**
 * Chat message DTO persisted/replicated by {@link ChatMessageStore} and surfaced
 * via the WebAE {@code /api/chat/*} endpoints.
 *
 * <p>
 * Fields:
 * </p>
 * <ul>
 * <li>{@code id} — monotonically increasing message id (server-assigned)</li>
 * <li>{@code senderUuid} — UUID of the sender (may be empty for system messages)</li>
 * <li>{@code senderName} — display name of the sender</li>
 * <li>{@code content} — message text</li>
 * <li>{@code timestamp} — epoch ms when the message was recorded</li>
 * <li>{@code source} — "game", "web", or "system"</li>
 * <li>{@code attachmentId} — optional server-side screenshot attachment id</li>
 * </ul>
 */
public class ChatMessage {

    public static final String SOURCE_GAME = "game";
    public static final String SOURCE_WEB = "web";
    public static final String SOURCE_SYSTEM = "system";

    public long id;
    public String senderUuid;
    public String senderName;
    public String content;
    public long timestamp;
    public String source;
    public String attachmentId;
    public String attachmentName;
    public String attachmentMime;
    public int attachmentWidth;
    public int attachmentHeight;
    public int attachmentBytes;

    public ChatMessage() {}

    public ChatMessage(long id, String senderUuid, String senderName, String content, long timestamp, String source) {
        this.id = id;
        this.senderUuid = senderUuid;
        this.senderName = senderName;
        this.content = content;
        this.timestamp = timestamp;
        this.source = source;
    }

    public ChatMessage withAttachment(String id, String name, String mime, int width, int height, int bytes) {
        this.attachmentId = id;
        this.attachmentName = name;
        this.attachmentMime = mime;
        this.attachmentWidth = width;
        this.attachmentHeight = height;
        this.attachmentBytes = bytes;
        return this;
    }
}
