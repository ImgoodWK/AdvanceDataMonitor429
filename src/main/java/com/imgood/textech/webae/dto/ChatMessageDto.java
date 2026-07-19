package com.imgood.textech.webae.dto;

/**
 * Chat message DTO returned by the WebAE {@code /api/chat/*} endpoints. Mirrors
 * {@link com.imgood.textech.webae.chat.ChatMessage}.
 */
public class ChatMessageDto {

    public long id;
    public String senderUuid;
    public String senderName;
    public String content;
    public long timestamp;
    /** "game", "web", or "system". */
    public String source;
    public String attachmentId;
    public String attachmentName;
    public String attachmentMime;
    public int attachmentWidth;
    public int attachmentHeight;
    public int attachmentBytes;

    public ChatMessageDto() {}

    public ChatMessageDto(long id, String senderUuid, String senderName, String content, long timestamp,
        String source) {
        this.id = id;
        this.senderUuid = senderUuid;
        this.senderName = senderName;
        this.content = content;
        this.timestamp = timestamp;
        this.source = source;
    }

    public ChatMessageDto withAttachment(String attachmentId, String attachmentName, String attachmentMime,
        int attachmentWidth, int attachmentHeight, int attachmentBytes) {
        this.attachmentId = attachmentId;
        this.attachmentName = attachmentName;
        this.attachmentMime = attachmentMime;
        this.attachmentWidth = attachmentWidth;
        this.attachmentHeight = attachmentHeight;
        this.attachmentBytes = attachmentBytes;
        return this;
    }
}
