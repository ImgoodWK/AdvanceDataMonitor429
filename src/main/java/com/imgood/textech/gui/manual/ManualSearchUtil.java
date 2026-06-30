package com.imgood.textech.gui.manual;

import java.lang.reflect.Method;

import com.imgood.textech.AdvanceDataMonitor;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import me.towdium.pinin.PinIn;

/**
 * Manual search matching with literal text and Chinese pinyin (PinIn).
 * When NotEnoughCharacters is installed, delegates to {@code NecharUtils.contain}
 * so behavior stays aligned with NEI pinyin search.
 */
@SideOnly(Side.CLIENT)
public final class ManualSearchUtil {

    private static final PinIn PIN_IN = new PinIn();
    private static Method necharContain;
    private static boolean necharChecked;

    private ManualSearchUtil() {}

    public static boolean isQueryEmpty(String query) {
        return query == null || query.trim()
            .isEmpty();
    }

    public static String[] splitTokens(String query) {
        if (isQueryEmpty(query)) {
            return new String[0];
        }
        return query.trim()
            .toLowerCase()
            .split("\\s+");
    }

    public static boolean textMatches(String haystack, String query) {
        if (isQueryEmpty(query)) {
            return true;
        }
        if (haystack == null || haystack.isEmpty()) {
            return false;
        }
        String[] tokens = splitTokens(query);
        for (String token : tokens) {
            if (!tokenMatches(haystack, token)) {
                return false;
            }
        }
        return true;
    }

    public static boolean tokenMatches(String haystack, String token) {
        if (token == null || token.isEmpty()) {
            return true;
        }
        if (haystack == null || haystack.isEmpty()) {
            return false;
        }
        if (haystack.toLowerCase()
            .contains(token)) {
            return true;
        }
        if (!containsChinese(haystack) && isAsciiLetters(token)) {
            return false;
        }
        return pinyinContains(haystack, token);
    }

    public static boolean containsChinese(CharSequence text) {
        if (text == null) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= 0x3000 && c < 0xA000) || (c >= 0xE900 && c < 0xEA00)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAsciiLetters(String token) {
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if ((c < 'a' || c > 'z') && (c < '0' || c > '9')) {
                return false;
            }
        }
        return true;
    }

    private static boolean pinyinContains(String haystack, String token) {
        Method method = getNecharContain();
        if (method != null) {
            try {
                Object result = method.invoke(null, haystack, token, Boolean.FALSE);
                if (result instanceof Boolean && (Boolean) result) {
                    return true;
                }
            } catch (Exception e) {
                AdvanceDataMonitor.LOG.debug("NecharUtils.contain failed, falling back to PinIn: {}", e.toString());
            }
        }
        return PIN_IN.contains(haystack, token);
    }

    private static Method getNecharContain() {
        if (necharChecked) {
            return necharContain;
        }
        necharChecked = true;
        try {
            Class<?> clazz = Class.forName("net.moecraft.nechar.NecharUtils");
            necharContain = clazz.getMethod("contain", String.class, String.class, Boolean.class);
        } catch (Exception ignored) {
            necharContain = null;
        }
        return necharContain;
    }
}
