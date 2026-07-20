package com.imgood.textech.webae.display;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.imgood.textech.webae.cache.SnapshotCache;
import com.imgood.textech.webae.dto.StorageDto;
import com.imgood.textech.webae.player.PlayerOnlineSampler;

/**
 * Server-rendered HTML for headless Chrome capture. Avoids the SPA module bootstrap that
 * {@code --virtual-time-budget} exits before React can paint {@code #root}.
 */
public final class DisplayCapturePage {

    private DisplayCapturePage() {}

    public static String render(DisplayRecord record) {
        if (record == null) {
            return "<!DOCTYPE html><html><body>missing display</body></html>";
        }
        int networkId = firstNetworkId(record.layout);
        StorageDto storage = SnapshotCache.instance()
            .getStale(record.ownerUuid, networkId, "storage");
        int online = PlayerOnlineSampler.instance()
            .currentOnlineCount();
        String updated = new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date());

        StringBuilder sb = new StringBuilder(8192);
        sb.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"/>");
        sb.append("<title>")
            .append(esc(record.title))
            .append("</title>");
        sb.append("<style>");
        sb.append("*{box-sizing:border-box;margin:0;padding:0}");
        sb.append("html,body{width:100%;height:100%;background:#0f1419;color:#e6edf3;");
        sb.append("font-family:Segoe UI,Arial,sans-serif}");
        sb.append(".wrap{padding:16px;min-height:100%}");
        sb.append("h1{font-size:22px;margin-bottom:4px}");
        sb.append(".meta{opacity:.65;font-size:12px;margin-bottom:14px}");
        sb.append(".grid{display:grid;grid-template-columns:repeat(12,1fr);gap:12px}");
        sb.append(".card{background:#1a2332;border:1px solid #2d3a4d;border-radius:10px;");
        sb.append("padding:12px 14px;min-height:72px;display:flex;flex-direction:column;");
        sb.append("justify-content:center}");
        sb.append(".card .t{font-size:12px;opacity:.7;margin-bottom:6px}");
        sb.append(".card .v{font-size:28px;font-weight:700;letter-spacing:.02em}");
        sb.append(".card .s{font-size:12px;opacity:.55;margin-top:4px}");
        sb.append(".bar{height:10px;background:#243044;border-radius:6px;overflow:hidden;margin-top:8px}");
        sb.append(".bar>i{display:block;height:100%;background:#3b82f6}");
        sb.append("</style></head><body><div class=\"wrap\">");
        sb.append("<h1>")
            .append(esc(nz(record.title, "WebAE Dashboard")))
            .append("</h1>");
        sb.append("<div class=\"meta\">live render · net ")
            .append(networkId)
            .append(" · ")
            .append(updated)
            .append("</div>");
        sb.append("<div class=\"grid\">");

        JsonArray widgets = widgetsOf(record.layout);
        if (widgets == null || widgets.size() == 0) {
            sb.append(cardHtml(1, 1, 4, 2, "empty", "No widgets", ""));
        } else {
            for (int i = 0; i < widgets.size(); i++) {
                JsonObject w = widgets.get(i)
                    .getAsJsonObject();
                appendWidget(sb, w, storage, online);
            }
        }

        sb.append("</div></div></body></html>");
        return sb.toString();
    }

    private static void appendWidget(StringBuilder sb, JsonObject w, StorageDto storage, int online) {
        int x = asInt(w, "x", 0);
        int y = asInt(w, "y", 0);
        int width = Math.max(1, asInt(w, "width", 2));
        int height = Math.max(1, asInt(w, "height", 2));
        String type = asString(w, "type", "statCard");
        String ds = asString(w, "dataSource", "");
        String title = asString(w, "title", ds.isEmpty() ? type : ds);

        if ("progressBar".equals(type) || "bytesPercent".equals(ds)) {
            double pct = bytesPercent(storage);
            sb.append(cardHtml(x, y, width, height, title, formatPct(pct), barHtml(pct)));
            return;
        }
        if ("lineChart".equals(type)) {
            String value = valueFor(ds, storage, online);
            sb.append(cardHtml(x, y, width, height, title, value, "trend"));
            return;
        }
        String value = valueFor(ds, storage, online);
        sb.append(cardHtml(x, y, width, height, title, value, type));
    }

    private static String valueFor(String ds, StorageDto storage, int online) {
        if ("itemCount".equals(ds)) {
            return storage != null && storage.items != null ? String.valueOf(storage.items.size()) : "—";
        }
        if ("fluidCount".equals(ds)) {
            return storage != null && storage.fluids != null ? String.valueOf(storage.fluids.size()) : "—";
        }
        if ("bytesPercent".equals(ds)) {
            return formatPct(bytesPercent(storage));
        }
        if ("playerOnlineTrend".equals(ds) || "playerOnline".equals(ds)) {
            return String.valueOf(online);
        }
        if (storage == null) return "—";
        return nz(ds, "ok");
    }

    private static double bytesPercent(StorageDto storage) {
        if (storage == null || storage.bytesMax <= 0L) return 0.0D;
        return 100.0D * ((double) storage.bytesUsed / (double) storage.bytesMax);
    }

    private static String formatPct(double pct) {
        return String.format(Locale.ROOT, "%.1f%%", Double.valueOf(pct));
    }

    private static String barHtml(double pct) {
        int w = (int) Math.max(0, Math.min(100, Math.round(pct)));
        return "<div class=\"bar\"><i style=\"width:" + w + "%\"></i></div>";
    }

    private static String cardHtml(int x, int y, int width, int height, String title, String value, String sub) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"card\" style=\"grid-column:")
            .append(x + 1)
            .append(" / span ")
            .append(width)
            .append(";grid-row:")
            .append(y + 1)
            .append(" / span ")
            .append(height)
            .append("\">");
        sb.append("<div class=\"t\">")
            .append(esc(title))
            .append("</div>");
        sb.append("<div class=\"v\">")
            .append(esc(value))
            .append("</div>");
        if (sub != null && !sub.isEmpty() && sub.startsWith("<")) {
            sb.append(sub);
        } else if (sub != null && !sub.isEmpty()) {
            sb.append("<div class=\"s\">")
                .append(esc(sub))
                .append("</div>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    private static JsonArray widgetsOf(JsonObject layout) {
        if (layout == null || !layout.has("widgets")
            || !layout.get("widgets")
                .isJsonArray()) {
            return null;
        }
        return layout.getAsJsonArray("widgets");
    }

    private static int firstNetworkId(JsonObject layout) {
        JsonArray widgets = widgetsOf(layout);
        if (widgets == null) return 0;
        for (int i = 0; i < widgets.size(); i++) {
            JsonObject w = widgets.get(i)
                .getAsJsonObject();
            if (w.has("networkId") && w.get("networkId")
                .isJsonPrimitive()) {
                try {
                    return w.get("networkId")
                        .getAsInt();
                } catch (Exception ignored) {}
            }
        }
        return 0;
    }

    private static int asInt(JsonObject o, String key, int def) {
        if (o == null || !o.has(key)) return def;
        try {
            return o.get(key)
                .getAsInt();
        } catch (Exception e) {
            return def;
        }
    }

    private static String asString(JsonObject o, String key, String def) {
        if (o == null || !o.has(key)
            || o.get(key)
                .isJsonNull()) {
            return def;
        }
        try {
            return o.get(key)
                .getAsString();
        } catch (Exception e) {
            return def;
        }
    }

    private static String nz(String s, String def) {
        return s == null || s.trim()
            .isEmpty() ? def : s;
    }

    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&':
                    out.append("&amp;");
                    break;
                case '<':
                    out.append("&lt;");
                    break;
                case '>':
                    out.append("&gt;");
                    break;
                case '"':
                    out.append("&quot;");
                    break;
                case '\'':
                    out.append("&#39;");
                    break;
                default:
                    out.append(c);
            }
        }
        return out.toString();
    }
}
