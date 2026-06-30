package com.imgood.textech.assistant;

import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.imgood.textech.assistant.AssistantLexicon.LexiconData;

public final class AssistantTimeParser {

    private AssistantTimeParser() {}

    public static Result parse(String rawText, String fallbackTitle) {
        LexiconData lexicon = AssistantLexicon.get();
        String text = rawText == null ? "" : rawText;
        long dueAt = -1L;
        String cleaned = fallbackTitle == null || fallbackTitle.trim()
            .isEmpty() ? text : fallbackTitle;
        Matcher minutes = relativePattern(lexicon.timeMinuteUnits, lexicon.timeAfterWords, lexicon).matcher(text);
        if (minutes.find()) {
            dueAt = System.currentTimeMillis() + parseNumber(minutes.group(1), lexicon) * 60L * 1000L;
            cleaned = remove(cleaned, minutes.group(0));
        } else {
            Matcher hours = relativePattern(lexicon.timeHourUnits, lexicon.timeAfterWords, lexicon).matcher(text);
            if (hours.find()) {
                dueAt = System.currentTimeMillis() + parseNumber(hours.group(1), lexicon) * 60L * 60L * 1000L;
                cleaned = remove(cleaned, hours.group(0));
            } else {
                Matcher clock = clockPattern(lexicon).matcher(text);
                if (clock.find()) {
                    dueAt = clockTime(text, clock, lexicon);
                    cleaned = remove(cleaned, clock.group(0));
                }
            }
        }
        cleaned = AssistantTextNormalizer.removeWords(cleaned, lexicon.timeStripWords);
        cleaned = AssistantTextNormalizer.removeWords(cleaned, lexicon.timeDayWords);
        cleaned = AssistantTextNormalizer.stripPunctuation(AssistantTextNormalizer.stripModalParticles(cleaned));
        if (cleaned.isEmpty()) {
            cleaned = AssistantTextNormalizer.stripPunctuation(text);
        }
        return new Result(dueAt, cleaned);
    }

    private static Pattern relativePattern(java.util.List<String> units, java.util.List<String> afterWords,
        LexiconData lexicon) {
        return Pattern.compile(
            "([0-9" + chineseNumberRegex(
                lexicon) + "]+)\\s*(?:" + regexAlternation(units) + ")\\s*(?:" + regexAlternation(afterWords) + ")");
    }

    private static Pattern clockPattern(LexiconData lexicon) {
        String digits = "[0-9" + chineseNumberRegex(lexicon) + "]";
        return Pattern.compile(
            "(" + digits
                + "{1,3})(?:[:：]([0-9]{1,2})|\\s*(?:"
                + regexAlternation(lexicon.timePointWords)
                + ")(?:\\s*("
                + digits
                + "{1,3})\\s*(?:"
                + regexAlternation(lexicon.timeMinuteWords)
                + ")?)?)");
    }

    private static String chineseNumberRegex(LexiconData lexicon) {
        StringBuilder builder = new StringBuilder();
        appendChars(builder, lexicon.chineseNumbers);
        appendChars(builder, lexicon.alternateTwoWords);
        return builder.toString();
    }

    private static void appendChars(StringBuilder builder, java.util.List<String> values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            String unescaped = AssistantTextNormalizer.unescape(value);
            for (int i = 0; i < unescaped.length(); i++) {
                String ch = String.valueOf(unescaped.charAt(i));
                if (builder.indexOf(ch) < 0) {
                    builder.append(Pattern.quote(ch));
                }
            }
        }
    }

    private static long clockTime(String fullText, Matcher matcher, LexiconData lexicon) {
        int hour = parseNumber(matcher.group(1), lexicon);
        int minute = 0;
        if (matcher.group(2) != null) {
            minute = parseNumber(matcher.group(2), lexicon);
        } else if (matcher.group(3) != null) {
            minute = parseNumber(matcher.group(3), lexicon);
        }
        if (AssistantTextNormalizer.containsAny(fullText, lexicon.timeAfternoonWords) && hour < 12) {
            hour += 12;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.set(Calendar.HOUR_OF_DAY, Math.max(0, Math.min(23, hour)));
        calendar.set(Calendar.MINUTE, Math.max(0, Math.min(59, minute)));
        if (AssistantTextNormalizer.containsAny(fullText, lexicon.timeTomorrowWords)) {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        } else if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }
        return calendar.getTimeInMillis();
    }

    private static int parseNumber(String value, LexiconData lexicon) {
        if (value == null || value.trim()
            .isEmpty()) {
            return 0;
        }
        String text = value.trim();
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ignored) {}
        String ten = lexicon.chineseNumbers.size() >= 10
            ? AssistantTextNormalizer.unescape(lexicon.chineseNumbers.get(9))
            : "ten";
        int tenIndex = text.indexOf(ten);
        if (tenIndex >= 0) {
            int left = tenIndex == 0 ? 1 : chineseDigit(text.substring(0, tenIndex), lexicon);
            int right = tenIndex + ten.length() >= text.length() ? 0
                : chineseDigit(text.substring(tenIndex + ten.length()), lexicon);
            return left * 10 + right;
        }
        return chineseDigit(text, lexicon);
    }

    private static int chineseDigit(String text, LexiconData lexicon) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        if (lexicon.alternateTwoWords != null) {
            for (String word : lexicon.alternateTwoWords) {
                if (AssistantTextNormalizer.unescape(word)
                    .equals(text)) {
                    return 2;
                }
            }
        }
        for (int i = 0; i < lexicon.chineseNumbers.size(); i++) {
            if (AssistantTextNormalizer.unescape(lexicon.chineseNumbers.get(i))
                .equals(text)) {
                return i + 1;
            }
        }
        return 0;
    }

    private static String remove(String text, String value) {
        if (text == null || value == null || value.isEmpty()) {
            return text == null ? "" : text;
        }
        return text.replace(value, "");
    }

    private static String regexAlternation(java.util.List<String> values) {
        StringBuilder builder = new StringBuilder();
        if (values != null) {
            for (String value : values) {
                if (value == null || value.isEmpty()) {
                    continue;
                }
                if (builder.length() > 0) {
                    builder.append("|");
                }
                builder.append(Pattern.quote(AssistantTextNormalizer.unescape(value)));
            }
        }
        return builder.length() == 0 ? "(?!)" : builder.toString();
    }

    public static final class Result {

        public final long dueAt;
        public final String cleanedTitle;

        private Result(long dueAt, String cleanedTitle) {
            this.dueAt = dueAt;
            this.cleanedTitle = cleanedTitle == null ? "" : cleanedTitle.trim();
        }
    }
}
