package com.imgood.textech.webae.auth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    /**
     * Guest allowlist of stable network keys. {@code null} = legacy all nets;
     * empty = none; non-empty = allowlist.
     */
    public final List<String> allowedNetworkKeys;

    public WebAuthSession(String token, String type, String ownerUuid, String actorUuid, String actorName) {
        this(token, type, ownerUuid, actorUuid, actorName, null);
    }

    public WebAuthSession(String token, String type, String ownerUuid, String actorUuid, String actorName,
        List<String> allowedNetworkKeys) {
        this.token = token;
        this.type = type != null && !type.isEmpty() ? type : TYPE_OWNER;
        this.ownerUuid = ownerUuid;
        this.actorUuid = actorUuid != null && !actorUuid.isEmpty() ? actorUuid : ownerUuid;
        this.actorName = actorName != null ? actorName : "";
        if (allowedNetworkKeys == null) {
            this.allowedNetworkKeys = null;
        } else if (allowedNetworkKeys.isEmpty()) {
            this.allowedNetworkKeys = Collections.emptyList();
        } else {
            this.allowedNetworkKeys = Collections.unmodifiableList(new ArrayList<String>(allowedNetworkKeys));
        }
    }

    public boolean isGuest() {
        return TYPE_GUEST.equals(type);
    }
}
