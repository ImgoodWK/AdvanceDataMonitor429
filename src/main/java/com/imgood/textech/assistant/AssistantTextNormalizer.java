package com.imgood.textech.assistant;

import java.util.List;

public final class AssistantTextNormalizer {

    private AssistantTextNormalizer() {}

    public static String lower(String text) {
        return text == null ? ""
            : text.trim()
                .toLowerCase();
    }

    public static String stripCommon(String text) {
        return removeWords(text == null ? "" : text, AssistantLexicon.get().commonWords).trim();
    }

    public static String stripModalParticles(String text) {
        return removeWords(text, AssistantLexicon.get().modalParticles).trim();
    }

    public static String stripPunctuation(String text) {
        if (text == null) {
            return "";
        }
        return AssistantLexicon.edgePunctuationPattern()
            .matcher(text)
            .replaceAll("")
            .trim();
    }

    public static String removeWords(String text, List<String> words) {
        String result = text == null ? "" : text;
        if (words == null) {
            return result;
        }
        for (String word : words) {
            if (word != null && !word.isEmpty()) {
                result = result.replace(unescape(word), "");
            }
        }
        return result;
    }

    public static String removeWords(String text, String... words) {
        String result = text == null ? "" : text;
        if (words == null) {
            return result;
        }
        for (String word : words) {
            if (word != null && !word.isEmpty()) {
                result = result.replace(unescape(word), "");
            }
        }
        return result;
    }

    public static boolean containsAny(String text, List<String> values) {
        if (text == null || values == null) {
            return false;
        }
        String normalized = text.toLowerCase();
        for (String value : values) {
            if (value != null && !value.isEmpty() && normalized.contains(unescape(value).toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    public static boolean containsAny(String text, String... values) {
        if (text == null) {
            return false;
        }
        String normalized = text.toLowerCase();
        for (String value : values) {
            if (value != null && !value.isEmpty() && normalized.contains(unescape(value).toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    public static String unescape(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (i + 5 < text.length() && text.charAt(i) == '\\' && text.charAt(i + 1) == 'u') {
                try {
                    builder.append((char) Integer.parseInt(text.substring(i + 2, i + 6), 16));
                    i += 5;
                    continue;
                } catch (NumberFormatException ignored) {}
            }
            builder.append(text.charAt(i));
        }
        return builder.toString();
    }
}
