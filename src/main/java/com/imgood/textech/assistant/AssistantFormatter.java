package com.imgood.textech.assistant;

import java.util.List;

public final class AssistantFormatter {

    private AssistantFormatter() {}

    public static String candidates(String title, List<CraftingCandidate> candidates) {
        return candidates(title, candidates, "No matching candidates were found.");
    }

    public static String candidates(String title, List<CraftingCandidate> candidates, String emptyMessage) {
        if (candidates == null || candidates.isEmpty()) {
            return emptyMessage == null || emptyMessage.isEmpty() ? "No matching candidates were found." : emptyMessage;
        }
        int count = candidates.size();
        StringBuilder builder = new StringBuilder(
            title == null || title.isEmpty() ? "Candidates; say a number to confirm:" : title);
        builder.append(" (")
            .append(count)
            .append(" results)");
        int indexWidth = Math.max(
            2,
            String.valueOf(count)
                .length());
        int perLine = count <= 20 ? 2 : 3;
        int entryWidth = perLine == 2 ? 38 : 27;
        for (int i = 0; i < candidates.size(); i++) {
            if (i % perLine == 0) {
                builder.append("\n");
            } else {
                builder.append("  ");
            }
            CraftingCandidate candidate = candidates.get(i);
            String formattedAmount = candidate.amount >= 1000 ? formatCount(candidate.amount)
                : String.valueOf(candidate.amount);
            String entry = padLeft(candidate.index, indexWidth) + ". "
                + truncateForDisplay(candidate.displayName, entryWidth - indexWidth - 7)
                + " x"
                + formattedAmount;
            builder.append(padRight(entry, entryWidth));
        }
        return builder.toString();
    }

    public static String numberedEntries(String title, List<String> entries) {
        if (entries == null || entries.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(title == null ? "" : title);
        if (builder.length() > 0) {
            builder.append(":");
        }
        int count = entries.size();
        int indexWidth = Math.max(
            2,
            String.valueOf(count)
                .length());
        int perLine = count <= 20 ? 2 : 3;
        int entryWidth = perLine == 2 ? 38 : 27;
        for (int i = 0; i < entries.size(); i++) {
            if (i % perLine == 0) {
                builder.append("\n");
            } else {
                builder.append("  ");
            }
            String entry = padLeft(i + 1, indexWidth) + ". "
                + truncateForDisplay(entries.get(i), entryWidth - indexWidth - 3);
            builder.append(padRight(entry, entryWidth));
        }
        return builder.toString();
    }

    private static String padLeft(Object value, int width) {
        String text = String.valueOf(value);
        if (text.length() >= width) {
            return text;
        }
        StringBuilder padded = new StringBuilder(width);
        for (int i = text.length(); i < width; i++) {
            padded.append(' ');
        }
        padded.append(text);
        return padded.toString();
    }

    private static String padRight(Object value, int width) {
        String text = String.valueOf(value);
        if (text.length() >= width) {
            return text;
        }
        StringBuilder padded = new StringBuilder(width);
        padded.append(text);
        for (int i = text.length(); i < width; i++) {
            padded.append(' ');
        }
        return padded.toString();
    }

    private static String truncateForDisplay(String text, int maxLen) {
        if (text == null) return "";
        if (maxLen <= 1) return ".";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen - 1) + ".";
    }

    public static String batchOrderLines(String title, List<AssistantOrderLine> lines) {
        return batchOrderLines(title, lines, "No batch order lines were found.", "no candidate");
    }

    public static String batchOrderLines(String title, List<AssistantOrderLine> lines, String emptyMessage,
        String noCandidateText) {
        if (lines == null || lines.isEmpty()) {
            return emptyMessage == null || emptyMessage.isEmpty() ? "No batch order lines were found." : emptyMessage;
        }
        StringBuilder builder = new StringBuilder(
            title == null || title.isEmpty() ? "Batch order candidates; say confirm to submit all:" : title);
        int indexWidth = Math.max(
            2,
            String.valueOf(lines.size())
                .length());
        builder.append("\n```");
        for (AssistantOrderLine line : lines) {
            builder.append("\n")
                .append(padLeft(line.lineIndex, indexWidth))
                .append(". ")
                .append(line.target)
                .append(" x")
                .append(line.amount);
            CraftingCandidate candidate = line.selectedOrFirstCandidate();
            if (candidate == null) {
                builder.append(" -> ")
                    .append(noCandidateText == null || noCandidateText.isEmpty() ? "no candidate" : noCandidateText);
            } else {
                builder.append(" -> ")
                    .append(candidate.displayName);
            }
        }
        builder.append("\n```");
        return builder.toString();
    }

    public static String formatBytes(long value) {
        if (value < 0) return "??";
        double current = value;
        String[] units = { "B", "KB", "MB", "GB", "TB" };
        int unit = 0;
        while (current >= 1024D && unit + 1 < units.length) {
            current /= 1024D;
            unit++;
        }
        return String.format("%.1f%s", current, units[unit]);
    }

    /**
     * AE2-style large number formatting: 1234 â†?1.2K, 1234567 â†?1.2M, etc.
     * Supports K (thousand), M (million), G (billion), T (trillion), P (quadrillion).
     */
    public static String formatCount(long value) {
        if (value < 0) return "??";
        if (value < 1000) return String.valueOf(value);
        double current = value;
        String[] units = { "", "K", "M", "G", "T", "P" };
        int unit = 0;
        while (current >= 1000D && unit + 1 < units.length) {
            current /= 1000D;
            unit++;
        }
        if (current >= 100D || unit == 0) {
            return String.format("%.0f%s", current, units[unit]);
        }
        if (current >= 10D) {
            return String.format("%.1f%s", current, units[unit]);
        }
        return String.format("%.2f%s", current, units[unit]);
    }

    public static String teleportDestinations(String title, List<TeleportDestination> destinations) {
        return teleportDestinations(title, destinations, "No teleport destinations found.");
    }

    public static String teleportDestinations(String title, List<TeleportDestination> destinations,
        String emptyMessage) {
        if (destinations == null || destinations.isEmpty()) {
            return emptyMessage == null || emptyMessage.isEmpty() ? "No teleport destinations found." : emptyMessage;
        }
        int count = destinations.size();
        StringBuilder builder = new StringBuilder(
            title == null || title.isEmpty() ? "Teleport destinations; say a number or name to confirm:" : title);
        builder.append(" (")
            .append(count)
            .append(" results)");
        int indexWidth = Math.max(
            2,
            String.valueOf(count)
                .length());
        for (int i = 0; i < destinations.size(); i++) {
            TeleportDestination dest = destinations.get(i);
            builder.append("\n")
                .append(padLeft(dest.index, indexWidth))
                .append(". ")
                .append(dest.name)
                .append(" [")
                .append(dest.dimensionName.isEmpty() ? String.valueOf(dest.dimensionId) : dest.dimensionName)
                .append("] ")
                .append(dest.x)
                .append(", ")
                .append(dest.y)
                .append(", ")
                .append(dest.z);
        }
        return builder.toString();
    }
}
