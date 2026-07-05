package com.imgood.textech.webae.auth;

import java.util.Map;

import com.imgood.textech.Config;

import fi.iki.elonen.NanoHTTPD;

public class WebAuthMiddleware {

    public AuthResult authenticate(NanoHTTPD.IHTTPSession session) {
        Map<String, String> headers = session.getHeaders();
        String authHeader = headers.get("authorization");
        String token = null;
        if (authHeader != null && !authHeader.isEmpty()) {
            String prefix = "Bearer ";
            if (!authHeader.startsWith(prefix)) {
                return AuthResult.failure(
                    "{\"status\":\"error\",\"code\":\"invalid_format\",\"message\":\"Invalid Authorization format. Use: Authorization: Bearer <token>\"}");
            }
            token = authHeader.substring(prefix.length())
                .trim();
        } else {
            Map<String, String> parms = session.getParms();
            if (parms != null) {
                String qToken = parms.get("token");
                if (qToken == null || qToken.isEmpty()) {
                    qToken = parms.get("access_token");
                }
                if (qToken != null && !qToken.isEmpty()) {
                    token = qToken.trim();
                }
            }
        }
        if (token == null || token.isEmpty()) {
            return AuthResult.failure(
                "{\"status\":\"error\",\"code\":\"missing_token\",\"message\":\"Missing Authorization header. Use: Authorization: Bearer <token> or ?token=<token>\"}");
        }
        WebAuthSession sessionInfo = WebAuthToken.validateToken(token);
        if (sessionInfo == null) {
            String code = isTokenLifetimeEnabled() ? "token_expired" : "invalid_token";
            return AuthResult
                .failure("{\"status\":\"error\",\"code\":\"" + code + "\",\"message\":\"Invalid or expired token.\"}");
        }
        return AuthResult.success(sessionInfo);
    }

    private static boolean isTokenLifetimeEnabled() {
        return Config.webTokenLifetimeHours > 0;
    }

    public static class AuthResult {

        public final boolean success;
        public final WebAuthSession session;
        public final String errorBody;

        private AuthResult(boolean success, WebAuthSession session, String errorBody) {
            this.success = success;
            this.session = session;
            this.errorBody = errorBody;
        }

        public static AuthResult success(WebAuthSession session) {
            return new AuthResult(true, session, null);
        }

        public static AuthResult failure(String errorBody) {
            return new AuthResult(false, null, errorBody);
        }
    }
}
