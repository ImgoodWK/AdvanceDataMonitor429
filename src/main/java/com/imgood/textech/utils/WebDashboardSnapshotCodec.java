package com.imgood.textech.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Validates and compresses the bounded WebAE visual snapshot consumed by the in-game web-surface renderer.
 */
public final class WebDashboardSnapshotCodec {

    public static final String FORMAT = "textech-webae-display-snapshot";
    public static final int VERSION = 1;
    public static final int MAX_RAW_BYTES = 96 * 1024;
    public static final int MAX_COMPRESSED_BYTES = 24 * 1024;
    public static final int MAX_PRIMITIVES = 700;
    public static final int MAX_TEXT_LENGTH = 256;

    private static final int MAX_VIEWPORT_WIDTH = 1600;
    private static final int MAX_VIEWPORT_HEIGHT = 1200;
    private static final int MAX_POLYLINE_VALUES = 256;

    private WebDashboardSnapshotCodec() {}

    public static EncodedSnapshot encode(String json) throws SnapshotException {
        if (json == null) throw new SnapshotException("empty_json");
        byte[] raw = utf8(json);
        if (raw.length == 0 || raw.length > MAX_RAW_BYTES) throw new SnapshotException("raw_size");
        DecodedSnapshot decoded = parse(json, raw.length);
        byte[] compressed = gzip(raw);
        if (compressed.length > MAX_COMPRESSED_BYTES) throw new SnapshotException("compressed_size");
        return new EncodedSnapshot(compressed, sha256(raw), decoded);
    }

    public static DecodedSnapshot decode(byte[] compressed) throws SnapshotException {
        if (compressed == null || compressed.length == 0 || compressed.length > MAX_COMPRESSED_BYTES) {
            throw new SnapshotException("compressed_size");
        }
        byte[] raw = gunzip(compressed);
        String json = new String(raw, utf8Charset());
        DecodedSnapshot decoded = parse(json, raw.length);
        decoded.hash = sha256(raw);
        return decoded;
    }

    public static String computeHash(byte[] compressed) throws SnapshotException {
        return decode(compressed).hash;
    }

    private static DecodedSnapshot parse(String json, int rawBytes) throws SnapshotException {
        try {
            JsonElement rootElement = new JsonParser().parse(json);
            if (rootElement == null || !rootElement.isJsonObject()) throw new SnapshotException("root");
            JsonObject root = rootElement.getAsJsonObject();
            if (!FORMAT.equals(readString(root, "format", ""))) throw new SnapshotException("format");
            if (readInt(root, "version", -1) != VERSION) throw new SnapshotException("version");

            JsonObject viewport = object(root, "viewport");
            int width = boundedInt(viewport, "width", 64, MAX_VIEWPORT_WIDTH);
            int height = boundedInt(viewport, "height", 64, MAX_VIEWPORT_HEIGHT);
            int background = parseColor(readString(viewport, "background", "#FF08111F"));
            String title = boundedText(readString(root, "title", "WebAE Dashboard"), 96);

            JsonArray primitiveArray = array(root, "primitives");
            if (primitiveArray.size() > MAX_PRIMITIVES) throw new SnapshotException("primitive_count");
            List<Primitive> primitives = new ArrayList<Primitive>(primitiveArray.size());
            for (int i = 0; i < primitiveArray.size(); i++) {
                JsonElement element = primitiveArray.get(i);
                if (element == null || !element.isJsonObject()) throw new SnapshotException("primitive");
                primitives.add(parsePrimitive(element.getAsJsonObject()));
            }
            return new DecodedSnapshot(json, rawBytes, title, width, height, background, primitives);
        } catch (SnapshotException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new SnapshotException("invalid_json", e);
        }
    }

    private static Primitive parsePrimitive(JsonObject object) throws SnapshotException {
        String kind = readString(object, "kind", "");
        Primitive p = new Primitive(kind);
        if ("polyline".equals(kind)) {
            JsonArray points = array(object, "points");
            if (points.size() < 4 || points.size() > MAX_POLYLINE_VALUES || (points.size() & 1) != 0) {
                throw new SnapshotException("polyline_points");
            }
            p.points = new float[points.size()];
            for (int i = 0; i < points.size(); i++) {
                p.points[i] = finiteFloat(points.get(i).getAsDouble(), -2400.0F, 2400.0F);
            }
            p.color = parseColor(readString(object, "color", "#FFFFFFFF"));
            p.lineWidth = finiteFloat(readDouble(object, "lineWidth", 1.0D), 0.2F, 16.0F);
            return p;
        }

        if (!"rect".equals(kind) && !"ellipse".equals(kind) && !"text".equals(kind)) {
            throw new SnapshotException("primitive_kind");
        }
        p.x = finiteFloat(readDouble(object, "x", 0.0D), -2400.0F, 2400.0F);
        p.y = finiteFloat(readDouble(object, "y", 0.0D), -1800.0F, 1800.0F);
        p.width = finiteFloat(readDouble(object, "w", 0.0D), 0.1F, 2400.0F);
        p.height = finiteFloat(readDouble(object, "h", 0.0D), 0.1F, 1800.0F);

        if ("text".equals(kind)) {
            p.text = boundedText(readString(object, "text", ""), MAX_TEXT_LENGTH);
            if (p.text.isEmpty()) throw new SnapshotException("text_empty");
            p.color = parseColor(readString(object, "color", "#FFFFFFFF"));
            p.fontSize = finiteFloat(readDouble(object, "size", 14.0D), 5.0F, 96.0F);
            p.fontWeight = boundedInt(object, "weight", 100, 900);
            p.align = readString(object, "align", "left");
            if (!"left".equals(p.align) && !"center".equals(p.align) && !"right".equals(p.align)) {
                p.align = "left";
            }
            return p;
        }

        p.hasFill = object.has("fill") && !object.get("fill").isJsonNull();
        p.hasStroke = object.has("stroke") && !object.get("stroke").isJsonNull();
        if (p.hasFill) p.fill = parseColor(readString(object, "fill", "#00000000"));
        if (p.hasStroke) p.stroke = parseColor(readString(object, "stroke", "#00000000"));
        if (!p.hasFill && !p.hasStroke) throw new SnapshotException("shape_color");
        p.radius = finiteFloat(readDouble(object, "radius", 0.0D), 0.0F, 128.0F);
        p.lineWidth = finiteFloat(readDouble(object, "lineWidth", 1.0D), 0.2F, 16.0F);
        return p;
    }

    private static JsonObject object(JsonObject parent, String key) throws SnapshotException {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonObject()) throw new SnapshotException(key);
        return value.getAsJsonObject();
    }

    private static JsonArray array(JsonObject parent, String key) throws SnapshotException {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonArray()) throw new SnapshotException(key);
        return value.getAsJsonArray();
    }

    private static String readString(JsonObject object, String key, String fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsString();
    }

    private static int readInt(JsonObject object, String key, int fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsInt();
    }

    private static double readDouble(JsonObject object, String key, double fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsDouble();
    }

    private static int boundedInt(JsonObject object, String key, int min, int max) throws SnapshotException {
        int value = readInt(object, key, min - 1);
        if (value < min || value > max) throw new SnapshotException(key);
        return value;
    }

    private static float finiteFloat(double value, float min, float max) throws SnapshotException {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < min || value > max) {
            throw new SnapshotException("number_range");
        }
        return (float) value;
    }

    private static String boundedText(String text, int max) throws SnapshotException {
        String value = text == null ? "" : text.trim();
        if (value.length() > max) throw new SnapshotException("text_length");
        return value;
    }

    private static int parseColor(String color) throws SnapshotException {
        if (color == null || !color.matches("#[0-9A-Fa-f]{8}")) throw new SnapshotException("color");
        try {
            long parsed = Long.parseLong(color.substring(1), 16);
            return (int) parsed;
        } catch (NumberFormatException e) {
            throw new SnapshotException("color", e);
        }
    }

    private static byte[] gzip(byte[] raw) throws SnapshotException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(raw.length, MAX_COMPRESSED_BYTES));
        try {
            GZIPOutputStream gzip = new GZIPOutputStream(output);
            gzip.write(raw);
            gzip.finish();
            gzip.close();
            return output.toByteArray();
        } catch (IOException e) {
            throw new SnapshotException("gzip", e);
        }
    }

    private static byte[] gunzip(byte[] compressed) throws SnapshotException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(MAX_RAW_BYTES, compressed.length * 4));
        try {
            GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed));
            byte[] buffer = new byte[4096];
            int read;
            while ((read = gzip.read(buffer)) >= 0) {
                if (read == 0) continue;
                if (output.size() + read > MAX_RAW_BYTES) throw new SnapshotException("raw_size");
                output.write(buffer, 0, read);
            }
            gzip.close();
            return output.toByteArray();
        } catch (SnapshotException e) {
            throw e;
        } catch (IOException e) {
            throw new SnapshotException("gunzip", e);
        }
    }

    private static String sha256(byte[] raw) throws SnapshotException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] value = digest.digest(raw);
            StringBuilder out = new StringBuilder(value.length * 2);
            for (byte b : value) out.append(String.format("%02x", b & 0xFF));
            return out.toString();
        } catch (Exception e) {
            throw new SnapshotException("hash", e);
        }
    }

    private static byte[] utf8(String text) throws SnapshotException {
        try {
            return text.getBytes("UTF-8");
        } catch (Exception e) {
            throw new SnapshotException("utf8", e);
        }
    }

    private static java.nio.charset.Charset utf8Charset() throws SnapshotException {
        try {
            return java.nio.charset.Charset.forName("UTF-8");
        } catch (Exception e) {
            throw new SnapshotException("utf8", e);
        }
    }

    public static final class EncodedSnapshot {

        public final byte[] compressed;
        public final String hash;
        public final DecodedSnapshot decoded;

        private EncodedSnapshot(byte[] compressed, String hash, DecodedSnapshot decoded) {
            this.compressed = compressed;
            this.hash = hash;
            this.decoded = decoded;
            this.decoded.hash = hash;
        }
    }

    public static final class DecodedSnapshot {

        public final String rawJson;
        public final int rawBytes;
        public final String title;
        public final int width;
        public final int height;
        public final int background;
        public final List<Primitive> primitives;
        public String hash = "";

        private DecodedSnapshot(String rawJson, int rawBytes, String title, int width, int height, int background,
            List<Primitive> primitives) {
            this.rawJson = rawJson;
            this.rawBytes = rawBytes;
            this.title = title;
            this.width = width;
            this.height = height;
            this.background = background;
            this.primitives = primitives;
        }
    }

    public static final class Primitive {

        public final String kind;
        public float x;
        public float y;
        public float width;
        public float height;
        public boolean hasFill;
        public boolean hasStroke;
        public int fill;
        public int stroke;
        public int color;
        public float radius;
        public float lineWidth;
        public String text = "";
        public float fontSize;
        public int fontWeight;
        public String align = "left";
        public float[] points;

        private Primitive(String kind) {
            this.kind = kind;
        }
    }

    public static class SnapshotException extends Exception {

        private static final long serialVersionUID = 1L;

        public SnapshotException(String message) {
            super(message);
        }

        public SnapshotException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
