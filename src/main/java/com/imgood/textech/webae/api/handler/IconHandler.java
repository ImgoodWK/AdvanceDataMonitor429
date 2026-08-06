package com.imgood.textech.webae.api.handler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.webae.access.WebAeNetworkAccess;
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
    private static final int MAX_UPLOAD_BODY_BYTES = 32 * 1024 * 1024;
    private static final int MAX_TOTAL_ICON_BYTES = 16 * 1024 * 1024;

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
        if (pack == null || pack.isEmpty()) pack = IconStore.instance()
            .getDefaultPack();
        if (pack == null || pack.isEmpty()) pack = "default";
        if (mode == null || mode.isEmpty()) mode = IconRenderMode.NEI.getId();
        SyncManifest manifest = IconStore.instance()
            .buildSyncManifest(pack, mode);
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
        if (pack == null || pack.isEmpty()) pack = IconStore.instance()
            .getDefaultPack();
        if (pack == null || pack.isEmpty()) pack = "default";
        if (mode == null || mode.isEmpty()) mode = IconRenderMode.NEI.getId();
        SyncManifest manifest = IconStore.instance()
            .buildSyncManifest(pack, mode);
        if (manifest.iconCount <= 0) {
            return jsonResponse(
                NanoHTTPD.Response.Status.NOT_FOUND,
                "{\"success\":false,\"message\":\"No icons in pack/mode\"}");
        }
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            IconStore.instance()
                .writeModeZip(pack, mode, baos);
            byte[] zipBytes = baos.toByteArray();
            if (zipBytes.length == 0) {
                return jsonResponse(
                    NanoHTTPD.Response.Status.NOT_FOUND,
                    "{\"success\":false,\"message\":\"Empty icon pack\"}");
            }
            InputStream stream = new ByteArrayInputStream(zipBytes);
            NanoHTTPD.Response resp = NanoHTTPD
                .newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/zip", stream, zipBytes.length);
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
        if (!IconStore.isValidPng(png)) return null;
        scheduleAsyncWrite(pack, mode, itemId, png);
        return png;
    }

    private static void scheduleAsyncWrite(final String pack, final String mode, final String itemId,
        final byte[] png) {
        Thread writer = new Thread(new Runnable() {

            @Override
            public void run() {
                if (IconStore.instance()
                    .writeIconPng(pack, mode, itemId, png)) {
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
        NanoHTTPD.Response resp = NanoHTTPD
            .newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "image/png", stream, png.length);
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
        NanoHTTPD.Response guestDenied = WebAeNetworkAccess.assertCanWrite(auth);
        if (guestDenied != null) {
            return guestDenied;
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
        byte[] body = readBodyBytes(session, MAX_UPLOAD_BODY_BYTES);
        if (body == null || body.length == 0) {
            return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Empty or oversized zip body\"}");
        }
        File packDir = new File(
            IconStore.instance()
                .getBaseDir(),
            packName);

        int extracted = 0;
        long totalIconBytes = 0L;
        List<StagedIcon> staged = new ArrayList<StagedIcon>();
        Set<String> stagedTargets = new HashSet<String>();
        ZipInputStream zis = null;
        try {
            zis = new ZipInputStream(new java.io.ByteArrayInputStream(body));
            ZipEntry entry;
            int entryCount = 0;
            String canonicalPack = packDir.getCanonicalPath();
            while ((entry = zis.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > IconStore.MAX_ICON_PACK_ENTRIES) {
                    throw new IOException("Too many zip entries");
                }
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                String name = entry.getName();
                if (name == null || !name.toLowerCase()
                    .endsWith(".png")) {
                    zis.closeEntry();
                    continue;
                }
                String normalizedName = name.replace('\\', '/');
                int slash = normalizedName.lastIndexOf('/');
                String baseName = slash >= 0 ? normalizedName.substring(slash + 1) : normalizedName;
                String itemId = baseName.substring(0, baseName.length() - 4);
                if (!IconStore.isValidItemId(itemId)) {
                    throw new IOException("Invalid icon entry name");
                }
                String safeName = IconStore.sanitizeItemId(itemId) + ".png";
                File outFile = new File(packDir, safeName);
                String canonicalOut = outFile.getCanonicalPath();
                if (!canonicalOut.startsWith(canonicalPack + File.separator)) {
                    throw new IOException("Icon pack path traversal");
                }
                if (!stagedTargets.add(canonicalOut)) {
                    throw new IOException("Duplicate icon entry");
                }
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = zis.read(buf)) >= 0) {
                    if (n == 0) {
                        continue;
                    }
                    if (baos.size() > IconStore.MAX_PNG_BYTES - n) {
                        throw new IOException("Icon entry exceeds size limit");
                    }
                    baos.write(buf, 0, n);
                }
                byte[] icon = baos.toByteArray();
                if (!IconStore.isValidPng(icon)) {
                    throw new IOException("Icon entry is not a valid bounded PNG");
                }
                totalIconBytes += icon.length;
                if (totalIconBytes > MAX_TOTAL_ICON_BYTES) {
                    throw new IOException("Icon pack exceeds total size limit");
                }
                staged.add(new StagedIcon(outFile, icon));
                extracted++;
                zis.closeEntry();
            }
            if (staged.isEmpty()) {
                throw new IOException("Icon pack contains no valid PNG entries");
            }
            if (!promoteStagedIcons(staged)) {
                throw new IOException("Failed to promote icon pack");
            }
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("[WebAE] Failed to extract icon pack zip", e);
            return jsonResponse(
                NanoHTTPD.Response.Status.INTERNAL_ERROR,
                "{\"success\":false,\"message\":\"Zip extraction failed\"}");
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

    private static byte[] readBodyBytes(NanoHTTPD.IHTTPSession session, int maxBytes) {
        try {
            if (session == null || maxBytes <= 0) {
                return null;
            }
            String cl = session.getHeaders()
                .get("content-length");
            if (cl == null) {
                return null;
            }
            long declaredLength = Long.parseLong(cl.trim());
            if (declaredLength <= 0 || declaredLength > maxBytes) {
                return null;
            }
            int contentLength = (int) declaredLength;
            byte[] buffer = new byte[contentLength];
            InputStream is = session.getInputStream();
            int read = 0;
            while (read < contentLength) {
                int r = is.read(buffer, read, contentLength - read);
                if (r < 0) return null;
                if (r == 0) continue;
                read += r;
            }
            return buffer;
        } catch (Exception e) {
            return null;
        }
    }

    /** Promote a fully validated pack as one best-effort transaction with rollback on failure. */
    private static boolean promoteStagedIcons(List<StagedIcon> icons) {
        if (icons == null || icons.isEmpty()) return false;
        boolean promotedAll = false;
        try {
            for (StagedIcon icon : icons) {
                if (icon == null || icon.file == null || icon.bytes == null || !IconStore.isValidPng(icon.bytes)) {
                    return false;
                }
                File parent = icon.file.getParentFile();
                if (parent == null || (!parent.exists() && !parent.mkdirs())) return false;
                icon.stagedFile = File.createTempFile("webae-icon-stage-", ".tmp", parent);
                writeFile(icon.stagedFile, icon.bytes);
            }
            for (StagedIcon icon : icons) {
                File parent = icon.file.getParentFile();
                if (icon.file.isFile()) {
                    icon.backupFile = File.createTempFile("webae-icon-backup-", ".bak", parent);
                    moveAtomically(icon.file, icon.backupFile);
                }
                moveAtomically(icon.stagedFile, icon.file);
                icon.stagedFile = null;
                icon.promoted = true;
            }
            promotedAll = true;
            return true;
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to promote staged icon pack: {}", e.getMessage());
            return false;
        } finally {
            if (!promotedAll) {
                rollbackStagedIcons(icons);
            }
            for (StagedIcon icon : icons) {
                if (icon == null) continue;
                deleteQuietly(icon.stagedFile);
                if (promotedAll) {
                    deleteQuietly(icon.backupFile);
                }
            }
        }
    }

    private static void rollbackStagedIcons(List<StagedIcon> icons) {
        for (int i = icons.size() - 1; i >= 0; i--) {
            StagedIcon icon = icons.get(i);
            if (icon == null || icon.file == null) continue;
            if (icon.promoted) {
                deleteQuietly(icon.file);
            }
            if (icon.backupFile != null && icon.backupFile.isFile()) {
                try {
                    moveAtomically(icon.backupFile, icon.file);
                    icon.backupFile = null;
                } catch (IOException e) {
                    AdvanceDataMonitor.LOG
                        .warn("[WebAE] Failed to restore previous icon {}: {}", icon.file.getName(), e.getMessage());
                }
            }
        }
    }

    private static void writeFile(File target, byte[] bytes) throws IOException {
        FileOutputStream output = null;
        try {
            output = new FileOutputStream(target);
            output.write(bytes);
            output.flush();
        } finally {
            if (output != null) output.close();
        }
    }

    private static void moveAtomically(File source, File target) throws IOException {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (UnsupportedOperationException e) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.exists() && !file.delete()) {
            AdvanceDataMonitor.LOG.debug("[WebAE] Failed to remove temporary icon file {}", file.getAbsolutePath());
        }
    }

    private static final class StagedIcon {

        final File file;
        final byte[] bytes;
        File stagedFile;
        File backupFile;
        boolean promoted;

        StagedIcon(File file, byte[] bytes) {
            this.file = file;
            this.bytes = bytes;
        }
    }

    private static NanoHTTPD.Response jsonResponse(NanoHTTPD.Response.Status status, String body) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body);
    }
}
