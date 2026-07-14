package com.imgood.textech.webae.auth;

import com.imgood.textech.webae.auth.WebAdminGrantStore.GrantEntry;

/**
 * Unified admin check: grants admin access when either
 * <ul>
 *   <li>a valid admin grant token (bound to session actorUuid) is present, OR</li>
 *   <li>the actor is online and has OP level >= 2</li>
 * </ul>
 *
 * <p>
 * This replaces direct calls to {@link WebAuthOpCheck#isOp(String)}
 * so that offline admin devices can perform sensitive operations
 * without requiring an online OP player.
 * </p>
 */
public final class WebAuthAdminCheck {

    private WebAuthAdminCheck() {}

    /**
     * Check whether the given session + admin header grants admin access.
     *
     * @param session     the authenticated WebAE session (must not be null)
     * @param adminHeader the value of the X-WebAE-Admin header (may be null)
     * @return true if the user is an admin
     */
    public static boolean isAdmin(WebAuthSession session, String adminHeader) {
        if (session == null) {
            return false;
        }
        // Admin grant token check
        if (adminHeader != null && !adminHeader.isEmpty()) {
            GrantEntry grant = WebAdminGrantStore.validate(adminHeader);
            if (grant != null && grant.boundActorUuid != null
                && grant.boundActorUuid.equals(session.actorUuid)) {
                return true;
            }
        }
        // Online OP fallback
        return WebAuthOpCheck.isOp(session.actorUuid);
    }

    /**
     * Check admin status using a raw token string (no session needed).
     * Only checks the admin grant; does NOT check online OP.
     *
     * @param adminToken the admin token value (may be null)
     * @param actorUuid  the actor UUID it must be bound to
     * @return true if the admin token is valid and bound to actorUuid
     */
    public static boolean isValidAdminToken(String adminToken, String actorUuid) {
        if (adminToken == null || adminToken.isEmpty() || actorUuid == null) {
            return false;
        }
        GrantEntry grant = WebAdminGrantStore.validate(adminToken);
        return grant != null && actorUuid.equals(grant.boundActorUuid);
    }

    /**
     * Get the raw admin token from the X-WebAE-Admin header.
     *
     * @param adminHeader the header value (may be null)
     * @return the trimmed token, or null
     */
    public static String parseAdminToken(String adminHeader) {
        if (adminHeader == null || adminHeader.isEmpty()) {
            return null;
        }
        return adminHeader.trim();
    }

    /**
     * Check admin using WebAuthSession + admin header from HTTP headers.
     */
    public static boolean isAdmin(WebAuthSession session, java.util.Map<String, String> headers) {
        String adminHeader = headers != null ? headers.get("x-webae-admin") : null;
        if (adminHeader == null || adminHeader.isEmpty()) {
            adminHeader = headers != null ? headers.get("X-WebAE-Admin") : null;
        }
        return isAdmin(session, adminHeader);
    }
}
