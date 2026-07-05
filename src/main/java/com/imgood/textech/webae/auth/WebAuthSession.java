package com.imgood.textech.webae.auth;

/**
 * Resolved authentication context for a WebAE API request.
 * {@link #ownerUuid} identifies the AE network owner; {@link #actorUuid}/{@link #actorName}
 * identify who is acting (owner or guest) for chat and audit.
 */
public final class WebAuthSession {

    public static final String TYPE_OWNER = "owner";
    public static final String TYPE_GUEST = "guest";

    public final String token;
    public final String type;
    public final String ownerUuid;
    public final String actorUuid;
    public final String actorName;

    public WebAuthSession(String token, String type, String ownerUuid, String actorUuid, String actorName) {
        this.token = token;
        this.type = type != null && !type.isEmpty() ? type : TYPE_OWNER;
        this.ownerUuid = ownerUuid;
        this.actorUuid = actorUuid != null && !actorUuid.isEmpty() ? actorUuid : ownerUuid;
        this.actorName = actorName != null ? actorName : "";
    }

    public boolean isGuest() {
        return TYPE_GUEST.equals(type);
    }
}
