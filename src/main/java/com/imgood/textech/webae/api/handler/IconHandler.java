package com.imgood.textech.webae.api.handler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.webae.auth.WebAuthAdminCheck;
import com.imgood.textech.webae.auth.WebAuthSession;
import com.imgood.textech.webae.events.EventStreamHub;
import com.imgood.textech.webae.icon.IconDirectCaptureBridge;
import com.imgood.textech.webae.icon.IconMissingQueue;
import com.imgood.textech.webae.icon.IconRenderMode;
import com.imgood.textech.webae.icon.IconStore;
import com.imgood.textech.webae.icon.IconStore.PackInfo;
import com.imgood.textech.webae.icon.IconStore.SyncManifest;

import fi.iki.elonen.NanoHTTPD;

/**
 * REST handler for the item/fluid icon cache.
 *
 * GET /api/icon?item=<itemId>&pack=<pack>&meta=<int>&size=<16|32|64>
 * — returns the cached PNG (with ETag + Cache-Control) or 404.
 * GET /api/icon/packs
 * — lists available icon packs with icon counts.
 * GET /api/icon/sync/manifest?pack=&mode=
 * — revision metadata for browser IndexedDB bulk sync.
 * GET /api/icon/sync/bulk?pack=&mode=
 * — zip stream of mode/*.png for browser IndexedDB import.
 * POST /api/icon/pack?pack=<packName>
 * — admin (OP>=2) uploads a zip of PNG icons; unzipped into web-icons/&lt;packName&gt;/.
 */
public class IconHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();

    public static NanoHTTPD.Response handle(String uri, NanoHTTPD.IHTTPSession session, WebAuthSession auth,
        String adminHeader) {
        Map<String, String> params = session.getParms();
        NanoHTTPD.Method method = session.getMethod();

        if ("/api/icon/packs".equals(uri)) {
            return handleListPacks();
        }
        if ("/api/icon/sync/manifest".equals(uri)) {
            if (method != NanoHTTPD.Method.GET) {
                return jsonResponse(
                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,
                    "{\"success\":false,\"message\":\"Use GET for sync manifest\"}");
            }
            return handleSyncManifest(params);
        }
        if ("/api/icon/sync/bulk".equals(uri)) {
            if (method != NanoHTTPD.Method.GET) {
                return jsonResponse(
                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,
                    "{\"success\":false,\"message\":\"Use GET for sync bulk zip\"}");
            }
            return handleSyncBulk(params);
        }
        if ("/api/icon/pack".equals(uri)) {
            if (method != NanoHTTPD.Method.POST) {
                return jsonResponse(
                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,
                    "{\"success\":false,\"message\":\"Use POST to upload a pack zip\"}");
            }
            return handleUploadPack(session, params, auth, adminHeader);
        }
        if ("/api/icon".equals(uri)) {
            if (method != NanoHTTPD.Method.GET) {
                return jsonResponse(
                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,
                    "{\"success\":false,\"message\":\"Use GET to fetch an icon\"}");
            }
            return handleGetIcon(params);
        }
        return jsonResponse(
            NanoHTTPD.Response.Status.NOT_FOUND,
            "{\"success\":false,\"message\":\"Unknown icon endpoint: " + uri + "\"}");
    }

    private static NanoHTTPD.Response handleListPacks() {
        List<PackInfo> packs = IconStore.instance()
            .listPacks();
        String defaultPack = IconStore.instance()
            .getDefaultPack();
        com.imgood.textech.webae.debug.WebAeDebugLog.info(
            com.imgood.textech.webae.debug.WebAeDebugLog.Feature.ICONS,
            "API icon packs listed: packCount={} defaultPack={}",
            packs.size(),
            defaultPack != null ? defaultPack : "null");
        String defaultPackJson = defaultPack != null ? GSON.toJson(defaultPack) : "null";
        return jsonResponse(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"packs\":" + GSON.toJson(packs) + ",\"defaultPack\":" + defaultPackJson + "}");
    }

    private static NanoHTTPD.Response handleSyncManifest(Map<String, String> params) {
        if (!Config.webIconCacheEnabled) {
            return jsonResponse(
                NanoHTTPD.Response.Status.NOT_FOUND,
                "{\"success\":false,\"message\":\"Icon cache is disabled\"}");
        }
        String pack = params.get("pack");
        String mode = params.get("mode");
        if (pack == null || pack.isEmpty()) pack = IconStore.instance().getDefaultPack();
        if (pack == null || pack.isEmpty()) pack = "default";
        if (mode == null || mode.isEmpty()) mode = IconRenderMode.NEI.getId();
        SyncManifest manifest = IconStore.instance().buildSyncManifest(pack, mode);
        return jsonResponse(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"manifest\":" + GSON.toJson(manifest) + "}");
    }

    private static NanoHTTPD.Response handleSyncBulk(Map<String, String> params) {
        if (!Config.webIconCacheEnabled) {
            return jsonResponse(
                NanoHTTPD.Response.Status.NOT_FOUND,
                "{\"success\":false,\"message\":\"Icon cache is disabled\"}");
        }
        String pack = params.get("pack");
        String mode = params.get("mode");
        if (pack == null || pack.isEmpty()) pack = IconStore.instance().getDefaultPack();
        if (pack == null || pack.isEmpty()) pack = "default";
        if (mode == null || mode.isEmpty()) mode = IconRenderMode.NEI.getId();
        SyncManifest manifest = IconStore.instance().buildSyncManifest(pack, mode);
        if (manifest.iconCount <= 0) {
            return jsonResponse(
                NanoHTTPD.Response.Status.NOT_FOUND,
                "{\"success\":false,\"message\":\"No icons in pack/mode\"}");
        }
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            IconStore.instance().writeModeZip(pack, mode, baos);
            byte[] zipBytes = baos.toByteArray();
            if (zipBytes.length == 0) {
                return jsonResponse(
                    NanoHTTPD.Response.Status.NOT_FOUND,
                    "{\"success\":false,\"message\":\"Empty icon pack\"}");
            }
            InputStream stream = new ByteArrayInputStream(zipBytes);
            NanoHTTPD.Response resp = NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK,
                "application/zip",
                stream,
                zipBytes.length);
            resp.addHeader("Cache-Control", "no-store");
            resp.addHeader("X-Icon-Sync-Version", manifest.version != null ? manifest.version : "");
            return resp;
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("[WebAE] Failed to stream icon bulk zip {}/{}", pack, mode, e);
            return jsonResponse(
                NanoHTTPD.Response.Status.INTERNAL_ERROR,
                "{\"success\":false,\"message\":\"Failed to build zip\"}");
        }
    }

    private static NanoHTTPD.Response handleGetIcon(Map<String, String> params) {
        if (!Config.webIconCacheEnabled) {
            return jsonResponse(
                NanoHTTPD.Response.Status.NOT_FOUND,
                "{\"success\":false,\"message\":\"Icon cache is disabled\"}");
        }
        String itemId = params.get("item");
        String pack = params.get("pack");
        String mode = params.get("mode");
        if (pack == null || pack.isEmpty()) pack = "default";
        if (mode == null || mode.isEmpty()) mode = IconRenderMode.NEI.getId();
        if (itemId == null || itemId.isEmpty()) {
            return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Missing 'item' parameter\"}");
        }
        IconStore.IconResolveResult resolved = resolveWithFallback(pack, mode, itemId);
        File file = resolved.file;
        if (file == null || !file.isFile()) {
            String captureMode = IconRenderMode.NEI.getId();
            byte[] direct = tryDirectCapture(pack, captureMode, itemId);
            if (direct != null && direct.length > 0) {
                return pngResponse(direct, pack, captureMode, itemId, itemId, true, true);
            }
            com.imgood.textech.webae.debug.WebAeDebugLog.info(
                com.imgood.textech.webae.debug.WebAeDebugLog.Feature.ICONS,
                "icon not found: pack={} itemId={}",
                pack != null ? pack : "",
                itemId != null ? itemId : "");
            if (Config.webIconLazyCaptureEnabled) {
                IconMissingQueue.instance()
                    .enqueue(pack, captureMode, itemId);
            }
            return NanoHTTPD
                .newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, "text/plain", "404 Icon Not Found");
        }
        com.imgood.textech.webae.debug.WebAeDebugLog.info(
            com.imgood.textech.webae.debug.WebAeDebugLog.Feature.ICONS,
            "icon served: pack={} itemId={} resolvedId={} exact={}",
            pack != null ? pack : "",
            itemId != null ? itemId : "",
            resolved.resolvedId != null ? resolved.resolvedId : "",
            resolved.exact);
        try {
            FileInputStream fis = new FileInputStream(file);
            NanoHTTPD.Response resp = NanoHTTPD.newChunkedResponse(NanoHTTPD.Response.Status.OK, "image/png", fis);
            String etag = buildEtag(pack, resolved.resolvedMode, resolved.resolvedId, file.lastModified());
            resp.addHeader("ETag", etag);
            resp.addHeader("Cache-Control", "max-age=86400");
            resp.addHeader("X-Icon-Resolved-Id", resolved.resolvedId);
            resp.addHeader("X-Icon-Resolved-Mode", resolved.resolvedMode);
            resp.addHeader("X-Icon-Exact", resolved.exact ? "1" : "0");
            return resp;
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("[WebAE] Failed to serve icon {}/{}", pack, itemId, e);
            return jsonResponse(
                NanoHTTPD.Response.Status.INTERNAL_ERROR,
                "{\"success\":false,\"message\":\"Failed to read icon\"}");
        }
    }

    private static byte[] tryDirectCapture(String pack, String mode, String itemId) {
        if (!Config.webIconDirectRenderEnabled) return null;
        byte[] png = IconDirectCaptureBridge.instance()
            .requestRender(pack, mode, itemId, Config.webIconDirectRenderTimeoutMs);
        if (png == null || png.length == 0) return null;
        scheduleAsyncWrite(pack, mode, itemId, png);
        return png;
    }

    private static void scheduleAsyncWrite(final String pack, final String mode, final String itemId,
        final byte[] png) {
        Thread writer = new Thread(new Runnable() {

            @Override
            public void run() {
                if (IconStore.instance().writeIconPng(pack, mode, itemId, png)) {
                    EventStreamHub.instance()
                        .publishIconReady(pack, mode, itemId);
                    IconMissingQueue.instance()
                        .acknowledge(pack, mode, itemId);
                }
            }
        }, "WebAE-IconDirectWrite");
        writer.setDaemon(true);
        writer.start();
    }

    private static NanoHTTPD.Response pngResponse(byte[] png, String pack, String mode, String requestedId,
        String resolvedId, boolean exact, boolean noStore) {
        InputStream stream = new ByteArrayInputStream(png);
        NanoHTTPD.Response resp = NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK,
            "image/png",
            stream,
            png.length);
        if (noStore) {
            resp.addHeader("Cache-Control", "no-store");
        } else {
            resp.addHeader("Cache-Control", "max-age=86400");
        }
        resp.addHeader("X-Icon-Resolved-Id", resolvedId != null ? resolvedId : requestedId);
        resp.addHeader("X-Icon-Resolved-Mode", mode != null ? mode : IconRenderMode.NEI.getId());
        resp.addHeader("X-Icon-Exact", exact ? "1" : "0");
        resp.addHeader("X-Icon-Direct-Capture", "1");
        return resp;
    }

    private static NanoHTTPD.Response handleUploadPack(NanoHTTPD.IHTTPSession session, Map<String, String> params,
        WebAuthSession auth, String adminHeader) {
        if (!Config.webIconPackEnabled) {
            return jsonResponse(
                NanoHTTPD.Response.Status.FORBIDDEN,
                "{\"success\":false,\"message\":\"Icon pack upload/switch is disabled\"}");
        }
        if (!WebAuthAdminCheck.isAdmin(auth, adminHeader)) {
            return jsonResponse(
                NanoHTTPD.Response.Status.FORBIDDEN,
                "{\"success\":false,\"message\":\"Admin permission required to upload icon packs\"}");
        }
        String packName = params.get("pack");
        if (!IconStore.isValidPackName(packName)) {
            return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Missing or invalid 'pack' name\"}");
        }
        byte[] body = readBodyBytes(session);
        if (body == null || body.length == 0) {
            return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Empty zip body\"}");
        }
        File packDir = new File(
            IconStore.instance()
                .getBaseDir(),
            packName);
        if (!packDir.exists()) packDir.mkdirs();

        int extracted = 0;
        ZipInputStream zis = null;
        try {
            zis = new ZipInputStream(new java.io.ByteArrayInputStream(body));
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                String name = entry.getName();
                if (!name.toLowerCase()
                    .endsWith(".png")) {
                    zis.closeEntry();
                    continue;
                }
                File outFile = new File(packDir, new File(name).getName());
                String canonicalPack = packDir.getCanonicalPath();
                String canonicalOut = outFile.getCanonicalPath();
                if (!canonicalOut.startsWith(canonicalPack + File.separator)) {
                    AdvanceDataMonitor.LOG.warn("[WebAE] Icon pack upload rejected path traversal: {}", name);
                    zis.closeEntry();
                    continue;
                }
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = zis.read(buf)) > 0) {
                    baos.write(buf, 0, n);
                }
                FileOutputStream fos = new FileOutputStream(outFile);
                try {
                    fos.write(baos.toByteArray());
                } finally {
                    fos.close();
                }
                extracted++;
                zis.closeEntry();
            }
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("[WebAE] Failed to extract icon pack zip", e);
            return jsonResponse(
                NanoHTTPD.Response.Status.INTERNAL_ERROR,
                "{\"success\":false,\"message\":\"Zip extraction failed: " + e.getMessage() + "\"}");
        } finally {
            if (zis != null) {
                try {
                    zis.close();
                } catch (Exception ignored) {}
            }
        }
        IconStore.instance()
            .refreshPack(packName);
        IconStore.instance()
            .recordDefaultPack(packName);
        AdvanceDataMonitor.LOG
            .info("[WebAE] Icon pack '{}' uploaded by {}: {} icons extracted", packName, auth.actorUuid, extracted);
        return jsonResponse(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"pack\":\"" + packName + "\",\"extracted\":" + extracted + "}");
    }

    private static IconStore.IconResolveResult resolveWithFallback(String pack, String mode, String itemId) {
        IconStore store = IconStore.instance();
        IconStore.IconResolveResult resolved = store.resolveIconFile(pack, mode, itemId);
        if (resolved.file != null) return resolved;
        String normalized = IconStore.normalizeModeId(mode);
        if (!IconRenderMode.NEI.getId()
            .equals(normalized)) {
            resolved = store.resolveIconFile(pack, IconRenderMode.NEI.getId(), itemId);
        }
        return resolved;
    }

    private static String buildEtag(String pack, String mode, String itemId, long mtime) {
        int h = (pack + "/" + mode + "/" + itemId + "/" + mtime).hashCode();
        return "\"" + Integer.toHexString(h) + "\"";
    }

    private static byte[] readBodyBytes(NanoHTTPD.IHTTPSession session) {
        try {
            int contentLength = 0;
            String cl = session.getHeaders()
                .get("content-length");
            if (cl != null) {
                contentLength = Integer.parseInt(cl.trim());
            }
            if (contentLength <= 0) return new byte[0];
            byte[] buffer = new byte[contentLength];
            InputStream is = session.getInputStream();
            int read = 0;
            while (read < contentLength) {
                int r = is.read(buffer, read, contentLength - read);
                if (r < 0) break;
                read += r;
            }
            return buffer;
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private static NanoHTTPD.Response jsonResponse(NanoHTTPD.Response.Status status, String body) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body);
    }
}
