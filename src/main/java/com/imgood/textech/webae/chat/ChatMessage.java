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

    public ChatMessage() {}

    public ChatMessage(long id, String senderUuid, String senderName, String content, long timestamp, String source) {
        this.id = id;
        this.senderUuid = senderUuid;
        this.senderName = senderName;
        this.content = content;
        this.timestamp = timestamp;
        this.source = source;
    }
}
