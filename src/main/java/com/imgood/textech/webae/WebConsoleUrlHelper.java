package com.imgood.textech.webae;

import com.imgood.textech.Config;

/**
 * Builds WebAE console URLs on the server (mirrors client {@code WebConsoleClientChat#resolveAccessUrl}).
 */
public final class WebConsoleUrlHelper {

    private WebConsoleUrlHelper() {}

    public static String resolveAccessUrl() {
        return resolveAccessUrl(Config.webConsolePort, Config.webConsoleBindAddress);
    }

    public static String resolveAccessUrl(int port, String bindAddress) {
        String bind = bindAddress != null ? bindAddress.trim() : "";
        if (bind.isEmpty() || "127.0.0.1".equals(bind) || "localhost".equalsIgnoreCase(bind)) {
            return "http://127.0.0.1:" + port;
        }
        if ("0.0.0.0".equals(bind)) {
            return "http://127.0.0.1:" + port;
        }
        return "http://" + bind + ":" + port;
    }

    public static String tokenLoginUrl(String token) {
        if (token == null || token.isEmpty()) {
            return resolveAccessUrl();
        }
        return resolveAccessUrl() + "/?token=" + token;
    }
}
