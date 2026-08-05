package com.imgood.textech.webae.icon;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Tags;
import com.imgood.textech.TeXTechDataDir;

/**
 * Server-side store for item/fluid icon PNG files organized by texture pack and render mode.
 *
 * Layout on disk:
 *
 * <pre>
 * TeXTech/WebAE/icons/
 *   &lt;packName&gt;/
 *     &lt;mode&gt;/&lt;sanitizedItemId&gt;.png
 *     &lt;sanitizedItemId&gt;.png   # legacy flat layout → treated as hybrid
 * </pre>
 */
public class IconStore {

    private static final IconStore INSTANCE = new IconStore();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .create();
    private static final String MANIFEST_FILE = "manifest.json";
    private static final String LEGACY_MIGRATED_FLAG = ".legacy-migrated";

    /** Hard upper bound for one icon resource, including client-originated renders. */
    public static final int MAX_PNG_BYTES = 512 * 1024;
    /** Hard upper bound for each decoded icon dimension. */
    public static final int MAX_PNG_DIMENSION = 2048;
    /** Maximum compressed size of a browser/local icon-pack ZIP. */
    public static final int MAX_ICON_PACK_ZIP_BYTES = 8 * 1024 * 1024;
    /** Maximum number of entries accepted by the client-side pull extractor. */
    public static final int MAX_ICON_PACK_ENTRIES = 4096;
    /** Maximum sum of decoded PNG bytes accepted in one icon-pack transaction. */
    public static final int MAX_ICON_PACK_PNG_BYTES = 16 * 1024 * 1024;
    private static final int MIN_PNG_HEADER_BYTES = 45;
    private static final byte[] PNG_SIGNATURE = { (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a };

    /** Base directory for all icon packs. */
    private final File baseDir;
    /** File that records the most recently uploaded pack name (for default pack selection). */
    private final File defaultPackFile;
    /** packName → pack index. */
    private final ConcurrentHashMap<String, PackEntry> packIndex;
    private volatile boolean indexed;
    /** Cached default pack name (loaded lazily, refreshed by {@link #recordDefaultPack}). */
    private volatile String cachedDefaultPack;
    /** True while a clear (sync or async) is deleting pack files from disk. */
    private volatile boolean clearInProgress;

    /** Invoked after disk deletion finishes (may run on a background thread). */
    public interface ClearCallback {

        void onComplete(int pngRemoved);
    }

    private IconStore() {
        this(TeXTechDataDir.webAeDir("icons"));
    }

    IconStore(File baseDir) {
        if (baseDir == null) throw new IllegalArgumentException("baseDir");
        this.baseDir = baseDir;
        this.defaultPackFile = new File(this.baseDir, "default-pack.txt");
        this.packIndex = new ConcurrentHashMap<String, PackEntry>();
        this.indexed = false;
        this.cachedDefaultPack = null;
        this.clearInProgress = false;
    }

    public static IconStore instance() {
        return INSTANCE;
    }

    public File getBaseDir() {
        return baseDir;
    }

    /** Rebuild the in-memory index by scanning the base directory. */
    public synchronized void refreshIndex() {
        packIndex.clear();
        if (!baseDir.exists()) {
            indexed = true;
            return;
        }
        File[] packs = baseDir.listFiles();
        if (packs != null) {
            for (File packDir : packs) {
                if (!packDir.isDirectory()) continue;
                migrateLegacyPackIfNeeded(packDir.getName());
                scanPack(packDir);
            }
        }
        indexed = true;
    }

    /**
     * Move legacy flat {@code web-icons/<pack>/<item>.png} files into {@code hybrid/} on first access.
     */
    public synchronized void migrateLegacyPackIfNeeded(String packName) {
        if (!isValidPackName(packName)) return;
        File packDir = new File(baseDir, packName);
        if (!packDir.isDirectory()) return;
        File migratedFlag = new File(packDir, LEGACY_MIGRATED_FLAG);
        if (migratedFlag.isFile()) return;

        File hybridDir = new File(packDir, IconRenderMode.HYBRID.getId());
        if (!hybridDir.exists()) hybridDir.mkdirs();

        boolean movedAny = false;
        File[] children = packDir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (!child.isFile() || !isPngFile(child)) continue;
                File target = new File(hybridDir, child.getName());
                if (target.exists()) {
                    if (!child.delete()) {
                        AdvanceDataMonitor.LOG
                            .warn("[WebAE] Could not remove duplicate legacy icon {}", child.getName());
                    }
                    movedAny = true;
                    continue;
                }
                if (child.renameTo(target)) {
                    movedAny = true;
                } else {
                    AdvanceDataMonitor.LOG.warn("[WebAE] Failed to migrate legacy icon {}", child.getAbsolutePath());
                }
            }
        }

        try {
            if (!migratedFlag.exists()) migratedFlag.createNewFile();
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to write legacy migration flag for pack {}", packName);
        }

        if (movedAny) {
            AdvanceDataMonitor.LOG.info("[WebAE] Migrated legacy flat icons to hybrid/ for pack '{}'", packName);
            writeManifestFromDisk(packName);
        }
    }

    private void scanPack(File packDir) {
        PackEntry entry = new PackEntry();
        File[] children = packDir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isFile() && isPngFile(child)) {
                    String itemId = stripPng(child.getName());
                    entry.legacyFlat.put(itemId, child);
                    incrementModeCount(entry, IconRenderMode.HYBRID.getId());
                } else if (child.isDirectory() && isValidModeDirName(child.getName())) {
                    scanModeDir(entry, child.getName(), child);
                }
            }
        }
        packIndex.put(packDir.getName(), entry);
    }

    private static void scanModeDir(PackEntry entry, String modeId, File modeDir) {
        File[] files = modeDir.listFiles();
        if (files == null) return;
        Map<String, File> icons = entry.byMode.get(modeId);
        if (icons == null) {
            icons = new ConcurrentHashMap<String, File>();
            entry.byMode.put(modeId, icons);
        }
        for (File f : files) {
            if (!f.isFile() || !isPngFile(f)) continue;
            String itemId = stripPng(f.getName());
            icons.put(itemId, f);
            incrementModeCount(entry, modeId);
        }
    }

    private static void incrementModeCount(PackEntry entry, String modeId) {
        Integer n = entry.modeCounts.get(modeId);
        entry.modeCounts.put(modeId, n == null ? 1 : n + 1);
    }

    /** Refresh a single pack's index after an upload. */
    public synchronized void refreshPack(String packName) {
        if (!isValidPackName(packName)) return;
        migrateLegacyPackIfNeeded(packName);
        File packDir = new File(baseDir, packName);
        if (!packDir.isDirectory()) {
            packIndex.remove(packName);
            return;
        }
        scanPack(packDir);
    }

    /** Update {@code manifest.json} after an upload for one mode. */
    public synchronized void recordModeUpload(String packName, String modeId, int iconCount) {
        if (!isValidPackName(packName)) return;
        migrateLegacyPackIfNeeded(packName);
        File packDir = new File(baseDir, packName);
        if (!packDir.isDirectory()) return;

        File manifestFile = new File(packDir, MANIFEST_FILE);
        JsonObject root = readManifestJson(manifestFile);
        JsonObject counts = root.has("counts") ? root.getAsJsonObject("counts") : new JsonObject();
        counts.addProperty(normalizeModeId(modeId), iconCount);
        root.add("counts", counts);

        com.google.gson.JsonArray modes = root.has("modes") ? root.getAsJsonArray("modes")
            : new com.google.gson.JsonArray();
        String normalized = normalizeModeId(modeId);
        boolean found = false;
        for (int i = 0; i < modes.size(); i++) {
            if (normalized.equals(
                modes.get(i)
                    .getAsString())) {
                found = true;
                break;
            }
        }
        if (!found) modes.add(new JsonPrimitive(normalized));
        root.add("modes", modes);

        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));
        root.addProperty("uploadedAt", iso.format(new Date()));
        root.addProperty("clientVersion", Tags.VERSION);

        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(manifestFile, false));
            writer.write(GSON.toJson(root));
            writer.newLine();
        } catch (Exception e) {
            AdvanceDataMonitor.LOG
                .warn("[WebAE] Failed to write icon manifest for pack {}: {}", packName, e.getMessage());
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (Exception ignored) {}
            }
        }
    }

    private void writeManifestFromDisk(String packName) {
        refreshPack(packName);
        PackEntry entry = packIndex.get(packName);
        if (entry == null) return;
        File manifestFile = new File(new File(baseDir, packName), MANIFEST_FILE);
        JsonObject root = readManifestJson(manifestFile);
        JsonObject counts = new JsonObject();
        List<String> modes = entry.availableModes();
        com.google.gson.JsonArray modesArr = new com.google.gson.JsonArray();
        for (String mode : modes) {
            modesArr.add(new JsonPrimitive(mode));
            Integer n = entry.modeCounts.get(mode);
            if (n != null) counts.addProperty(mode, n.intValue());
        }
        root.add("modes", modesArr);
        root.add("counts", counts);
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));
        if (!root.has("uploadedAt")) root.addProperty("uploadedAt", iso.format(new Date()));
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(manifestFile, false));
            writer.write(GSON.toJson(root));
            writer.newLine();
        } catch (Exception e) {
            AdvanceDataMonitor.LOG
                .warn("[WebAE] Failed to refresh icon manifest for pack {}: {}", packName, e.getMessage());
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (Exception ignored) {}
            }
        }
    }

    private static JsonObject readManifestJson(File manifestFile) {
        if (!manifestFile.isFile()) return new JsonObject();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(manifestFile));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            if (sb.length() == 0) return new JsonObject();
            return GSON.fromJson(sb.toString(), JsonObject.class);
        } catch (Exception e) {
            return new JsonObject();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {}
            }
        }
    }

    private void ensureIndexed() {
        if (!indexed) {
            refreshIndex();
        }
    }

    /**
     * List all available icon packs with per-mode icon counts.
     */
    public List<PackInfo> listPacks() {
        ensureIndexed();
        List<PackInfo> result = new ArrayList<PackInfo>();
        for (Map.Entry<String, PackEntry> e : packIndex.entrySet()) {
            PackEntry pe = e.getValue();
            result.add(new PackInfo(e.getKey(), pe.totalIconCount(), pe.modeCounts, pe.availableModes()));
        }
        Collections.sort(result);
        return result;
    }

    /**
     * List all icon itemIds within a pack (hybrid mode, including legacy flat files).
     */
    public List<String> listIcons(String packName) {
        return listIcons(packName, IconRenderMode.HYBRID.getId());
    }

    public List<String> listIcons(String packName, String modeId) {
        ensureIndexed();
        PackEntry entry = packIndex.get(packName);
        if (entry == null) return Collections.emptyList();
        Map<String, File> icons = entry.iconsForMode(modeId);
        if (icons == null || icons.isEmpty()) return Collections.emptyList();
        return new ArrayList<String>(icons.keySet());
    }

    /**
     * Build sync metadata for browser bulk download of a pack/mode directory.
     */
    public SyncManifest buildSyncManifest(String packName, String modeId) {
        String mode = normalizeModeId(modeId);
        if (!isValidPackName(packName)) {
            return SyncManifest.empty(packName, mode);
        }
        ensureIndexed();
        PackEntry entry = packIndex.get(packName);
        if (entry == null) {
            return SyncManifest.empty(packName, mode);
        }
        Map<String, File> icons = entry.iconsForMode(mode);
        if (icons == null || icons.isEmpty()) {
            return SyncManifest.empty(packName, mode);
        }
        List<String> ids = new ArrayList<String>(icons.keySet());
        Collections.sort(ids);
        long maxMtime = 0L;
        for (File f : icons.values()) {
            if (f != null && f.lastModified() > maxMtime) {
                maxMtime = f.lastModified();
            }
        }
        StringBuilder idJoin = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) idJoin.append('\n');
            idJoin.append(ids.get(i));
        }
        String idsHash = Integer.toHexString(
            idJoin.toString()
                .hashCode());
        String uploadedAt = readManifestUploadedAt(packName);
        String version = packName + "|" + mode + "|" + ids.size() + "|" + maxMtime + "|" + idsHash;
        return new SyncManifest(packName, mode, ids.size(), version, uploadedAt, idsHash);
    }

    /**
     * Stream a zip of {@code mode/*.png} for browser IndexedDB import.
     */
    public void writeModeZip(String packName, String modeId, OutputStream out) throws java.io.IOException {
        String mode = normalizeModeId(modeId);
        if (!isValidPackName(packName) || out == null) return;
        ensureIndexed();
        PackEntry entry = packIndex.get(packName);
        if (entry == null) return;
        Map<String, File> icons = entry.iconsForMode(mode);
        if (icons == null || icons.isEmpty()) return;
        ZipOutputStream zos = new ZipOutputStream(new SizeLimitedOutputStream(out, MAX_ICON_PACK_ZIP_BYTES));
        byte[] buf = new byte[8192];
        int entryCount = 0;
        try {
            for (Map.Entry<String, File> e : icons.entrySet()) {
                File file = e.getValue();
                if (file == null || !file.isFile() || !isValidPng(file)) continue;
                entryCount++;
                if (entryCount > MAX_ICON_PACK_ENTRIES) {
                    throw new IOException("Icon pack contains too many entries");
                }
                String entryName = mode + "/" + file.getName();
                zos.putNextEntry(new ZipEntry(entryName));
                FileInputStream fis = new FileInputStream(file);
                try {
                    int n;
                    while ((n = fis.read(buf)) > 0) {
                        zos.write(buf, 0, n);
                    }
                } finally {
                    fis.close();
                }
                zos.closeEntry();
            }
        } finally {
            zos.finish();
            zos.close();
        }
    }

    /** Write a rendered PNG to disk and refresh the in-memory index. */
    public boolean writeIconPng(String packName, String modeId, String itemId, byte[] png) {
        Map<String, byte[]> singleton = new LinkedHashMap<String, byte[]>(1);
        singleton.put(itemId, png);
        return writeIconPngBatch(packName, modeId, singleton);
    }

    /**
     * Validate, stage, and promote a complete icon bundle as one best-effort transaction.
     * No target is changed until every entry has passed the resource and path checks. If a
     * promotion fails, already-promoted entries are rolled back before this method returns.
     */
    public synchronized boolean writeIconPngBatch(String packName, String modeId, Map<String, byte[]> icons) {
        if (!isValidPackName(packName) || icons == null || icons.size() > MAX_ICON_PACK_ENTRIES) return false;
        String mode = normalizeModeId(modeId);
        if (!isValidModeDirName(mode)) return false;
        if (icons.isEmpty()) return true;

        List<StagedIconWrite> writes = new ArrayList<StagedIconWrite>(icons.size());
        Set<String> targetPaths = new HashSet<String>();
        long totalPngBytes = 0L;
        try {
            if ((!baseDir.exists() && !baseDir.mkdirs()) || !baseDir.isDirectory()
                || Files.isSymbolicLink(baseDir.toPath())) {
                return false;
            }
            File canonicalBase = baseDir.getCanonicalFile();
            File packDir = new File(baseDir, packName);
            if ((!packDir.exists() && !packDir.mkdir()) || !packDir.isDirectory()
                || Files.isSymbolicLink(packDir.toPath())) {
                return false;
            }
            File canonicalPack = packDir.getCanonicalFile();
            if (!isContained(canonicalBase, canonicalPack)) return false;

            File modeDir = new File(packDir, mode);
            if ((!modeDir.exists() && !modeDir.mkdir()) || !modeDir.isDirectory()
                || Files.isSymbolicLink(modeDir.toPath())) {
                return false;
            }
            File canonicalMode = modeDir.getCanonicalFile();
            if (!isContained(canonicalPack, canonicalMode)) return false;

            for (Map.Entry<String, byte[]> entry : icons.entrySet()) {
                String itemId = entry.getKey();
                byte[] png = entry.getValue();
                if (!isValidItemId(itemId) || !isValidPng(png)
                    || totalPngBytes > MAX_ICON_PACK_PNG_BYTES - png.length) {
                    return false;
                }
                totalPngBytes += png.length;
                File target = new File(modeDir, sanitizeItemId(itemId) + ".png");
                File canonicalTarget = target.getCanonicalFile();
                File canonicalParent = canonicalTarget.getParentFile();
                if (canonicalParent == null || !canonicalMode.equals(canonicalParent)
                    || (target.exists() && (!target.isFile() || Files.isSymbolicLink(target.toPath())))) {
                    return false;
                }
                String targetPath = canonicalTarget.getPath();
                if (!targetPaths.add(targetPath)) return false;
                writes.add(new StagedIconWrite(canonicalTarget, png));
            }
            return promoteIconWrites(packName, writes);
        } catch (IOException | RuntimeException e) {
            AdvanceDataMonitor.LOG
                .warn("[WebAE] Failed to prepare icon bundle for pack {}: {}", packName, e.getMessage());
            return false;
        }
    }

    private boolean promoteIconWrites(String packName, List<StagedIconWrite> writes) {
        boolean promotedAll = false;
        try {
            for (StagedIconWrite write : writes) {
                File parent = write.target.getParentFile();
                write.staged = File.createTempFile("webae-icon-stage-", ".tmp", parent);
                FileOutputStream output = null;
                try {
                    output = new FileOutputStream(write.staged);
                    output.write(write.png);
                    output.flush();
                } finally {
                    if (output != null) output.close();
                }
            }
            for (StagedIconWrite write : writes) {
                File parent = write.target.getParentFile();
                if (write.target.isFile()) {
                    write.backup = File.createTempFile("webae-icon-backup-", ".bak", parent);
                    moveAtomically(write.target, write.backup);
                }
                moveAtomically(write.staged, write.target);
                write.staged = null;
                write.promoted = true;
            }
            refreshPack(packName);
            promotedAll = true;
            return true;
        } catch (IOException | RuntimeException e) {
            AdvanceDataMonitor.LOG
                .warn("[WebAE] Failed to promote icon bundle for pack {}: {}", packName, e.getMessage());
            return false;
        } finally {
            if (!promotedAll) rollbackIconWrites(writes);
            for (StagedIconWrite write : writes) {
                deleteQuietly(write.staged);
                if (promotedAll) deleteQuietly(write.backup);
            }
        }
    }

    private static void rollbackIconWrites(List<StagedIconWrite> writes) {
        for (int i = writes.size() - 1; i >= 0; i--) {
            StagedIconWrite write = writes.get(i);
            if (write.promoted) deleteQuietly(write.target);
            if (write.backup != null && write.backup.isFile()) {
                try {
                    moveAtomically(write.backup, write.target);
                    write.backup = null;
                } catch (IOException e) {
                    AdvanceDataMonitor.LOG
                        .warn("[WebAE] Failed to restore previous icon {}: {}", write.target.getName(), e.getMessage());
                }
            }
        }
    }

    private static boolean isContained(File parent, File child) {
        if (parent == null || child == null || parent.equals(child)) return false;
        return child.toPath()
            .startsWith(parent.toPath());
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.exists() && !file.delete()) {
            AdvanceDataMonitor.LOG.debug("[WebAE] Failed to remove temporary icon {}", file.getAbsolutePath());
        }
    }

    /**
     * Validate an in-memory icon before it can cross the WebAE resource boundary.
     *
     * <p>
     * The signature/IHDR check runs before ImageIO so a malformed payload cannot make the
     * decoder allocate based on an unbounded header. ImageIO then performs a real PNG decode,
     * which rejects truncated/corrupt data and confirms that the payload is actually PNG rather
     * than merely a file with a {@code .png} suffix.
     * </p>
     */
    public static boolean isValidPng(byte[] png) {
        if (png == null || png.length < MIN_PNG_HEADER_BYTES
            || png.length > MAX_PNG_BYTES
            || !hasValidPngStructure(png)) {
            return false;
        }

        ImageInputStream input = null;
        ImageReader reader = null;
        try {
            input = ImageIO.createImageInputStream(new ByteArrayInputStream(png));
            if (input == null) return false;
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("png");
            if (!readers.hasNext()) return false;
            reader = readers.next();
            reader.setInput(input, true, true);
            if (reader.getWidth(0) <= 0 || reader.getHeight(0) <= 0
                || reader.getWidth(0) > MAX_PNG_DIMENSION
                || reader.getHeight(0) > MAX_PNG_DIMENSION) {
                return false;
            }
            BufferedImage decoded = reader.read(0);
            if (decoded == null || decoded.getWidth() <= 0
                || decoded.getHeight() <= 0
                || decoded.getWidth() > MAX_PNG_DIMENSION
                || decoded.getHeight() > MAX_PNG_DIMENSION) {
                return false;
            }
            String format = reader.getFormatName();
            return format != null && "png".equalsIgnoreCase(format);
        } catch (IOException | RuntimeException e) {
            return false;
        } finally {
            if (reader != null) {
                reader.dispose();
            }
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ignored) {}
            }
        }
    }

    /** Validate a bounded on-disk PNG without reading an arbitrarily large file into memory. */
    public static boolean isValidPng(File file) {
        if (file == null || !file.isFile()) return false;
        long length = file.length();
        if (length < MIN_PNG_HEADER_BYTES || length > MAX_PNG_BYTES) return false;
        byte[] png = new byte[(int) length];
        FileInputStream input = null;
        try {
            input = new FileInputStream(file);
            int offset = 0;
            while (offset < png.length) {
                int read = input.read(png, offset, png.length - offset);
                if (read < 0) return false;
                if (read == 0) continue;
                offset += read;
            }
            if (input.read() >= 0) return false;
            return isValidPng(png);
        } catch (IOException | RuntimeException e) {
            return false;
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ignored) {}
            }
        }
    }

    private static boolean hasValidPngStructure(byte[] png) {
        if (png.length < MIN_PNG_HEADER_BYTES) return false;
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (png[i] != PNG_SIGNATURE[i]) return false;
        }
        int offset = PNG_SIGNATURE.length;
        boolean seenIhdr = false;
        boolean seenIdat = false;
        boolean seenIend = false;
        while (offset <= png.length - 12) {
            long chunkLength = uint32(png, offset);
            if (chunkLength > Integer.MAX_VALUE || chunkLength > png.length - offset - 12L) return false;
            int dataLength = (int) chunkLength;
            int typeOffset = offset + 4;
            int dataOffset = offset + 8;
            int crcOffset = dataOffset + dataLength;
            if (!isPngChunkType(png, typeOffset)) return false;
            String type = new String(png, typeOffset, 4, java.nio.charset.StandardCharsets.US_ASCII);
            CRC32 crc = new CRC32();
            crc.update(png, typeOffset, 4 + dataLength);
            if (crc.getValue() != uint32(png, crcOffset)) return false;

            if (!seenIhdr) {
                if (!"IHDR".equals(type) || dataLength != 13) return false;
                long width = uint32(png, dataOffset);
                long height = uint32(png, dataOffset + 4);
                int bitDepth = png[dataOffset + 8] & 0xff;
                int colorType = png[dataOffset + 9] & 0xff;
                int compression = png[dataOffset + 10] & 0xff;
                int filter = png[dataOffset + 11] & 0xff;
                int interlace = png[dataOffset + 12] & 0xff;
                if (width <= 0L || height <= 0L
                    || width > MAX_PNG_DIMENSION
                    || height > MAX_PNG_DIMENSION
                    || !isValidPngColorFormat(bitDepth, colorType)
                    || compression != 0
                    || filter != 0
                    || (interlace != 0 && interlace != 1)) {
                    return false;
                }
                seenIhdr = true;
            } else if ("IHDR".equals(type)) {
                return false;
            }

            if ("IDAT".equals(type)) {
                if (seenIend) return false;
                seenIdat = true;
            } else if ("IEND".equals(type)) {
                if (dataLength != 0 || seenIend || !seenIdat || offset + 12 != png.length) return false;
                seenIend = true;
            } else if (seenIend) {
                return false;
            }
            offset = crcOffset + 4;
        }
        return seenIhdr && seenIdat && seenIend && offset == png.length;
    }

    private static boolean isPngChunkType(byte[] png, int offset) {
        for (int i = 0; i < 4; i++) {
            int value = png[offset + i] & 0xff;
            if (!((value >= 'A' && value <= 'Z') || (value >= 'a' && value <= 'z'))) return false;
        }
        return true;
    }

    private static boolean isValidPngColorFormat(int bitDepth, int colorType) {
        if (colorType == 0) return bitDepth == 1 || bitDepth == 2 || bitDepth == 4 || bitDepth == 8 || bitDepth == 16;
        if (colorType == 2) return bitDepth == 8 || bitDepth == 16;
        if (colorType == 3) return bitDepth == 1 || bitDepth == 2 || bitDepth == 4 || bitDepth == 8;
        if (colorType == 4 || colorType == 6) return bitDepth == 8 || bitDepth == 16;
        return false;
    }

    private static long uint32(byte[] bytes, int offset) {
        return ((long) (bytes[offset] & 0xff) << 24) | ((long) (bytes[offset + 1] & 0xff) << 16)
            | ((long) (bytes[offset + 2] & 0xff) << 8)
            | (long) (bytes[offset + 3] & 0xff);
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

    /** Output guard used while ZIP bytes are produced, before any caller can retain them. */
    private static final class SizeLimitedOutputStream extends OutputStream {

        private final OutputStream delegate;
        private final long maxBytes;
        private long written;

        SizeLimitedOutputStream(OutputStream delegate, long maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        @Override
        public void write(int value) throws IOException {
            ensureCapacity(1L);
            delegate.write(value);
            written++;
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            if (bytes == null) throw new NullPointerException("bytes");
            if (offset < 0 || length < 0 || offset > bytes.length - length) {
                throw new IndexOutOfBoundsException();
            }
            ensureCapacity(length);
            delegate.write(bytes, offset, length);
            written += length;
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        private void ensureCapacity(long additional) throws IOException {
            if (additional < 0L || written > maxBytes - additional) {
                throw new IOException("Icon pack ZIP exceeds " + maxBytes + " bytes");
            }
        }
    }

    private String readManifestUploadedAt(String packName) {
        File manifestFile = new File(new File(baseDir, packName), MANIFEST_FILE);
        JsonObject root = readManifestJson(manifestFile);
        if (root.has("uploadedAt")) {
            return root.get("uploadedAt")
                .getAsString();
        }
        return "";
    }

    /**
     * Get the PNG file for a given pack + mode + itemId.
     *
     * @return the File if present, null otherwise
     */
    public File getIconFile(String packName, String itemId) {
        return getIconFile(packName, IconRenderMode.HYBRID.getId(), itemId);
    }

    public File getIconFile(String packName, String modeId, String itemId) {
        return resolveIconFile(packName, modeId, itemId).file;
    }

    /**
     * Resolve an icon file by trying {@link IconItemId#lookupCandidates} in order for the given mode.
     */
    public IconResolveResult resolveIconFile(String packName, String itemId) {
        return resolveIconFile(packName, IconRenderMode.HYBRID.getId(), itemId);
    }

    public IconResolveResult resolveIconFile(String packName, String modeId, String itemId) {
        if (!isValidPackName(packName) || !isValidItemId(itemId)) {
            return IconResolveResult.miss(itemId, itemId, modeId);
        }
        ensureIndexed();
        PackEntry entry = packIndex.get(packName);
        if (entry == null) return IconResolveResult.miss(itemId, itemId, modeId);
        String requested = itemId;
        String resolvedMode = normalizeModeId(modeId);
        for (String candidate : IconItemId.lookupCandidates(itemId)) {
            String sanitized = sanitizeItemId(candidate);
            File direct = entry.lookup(resolvedMode, sanitized);
            if (direct != null) {
                return new IconResolveResult(direct, requested, candidate, resolvedMode, candidate.equals(requested));
            }
        }
        return IconResolveResult.miss(requested, requested, resolvedMode);
    }

    /**
     * Resolve the target file path for writing an uploaded icon. Validates names and ensures dirs exist.
     */
    public File resolveWriteTarget(String packName, String itemId) {
        return resolveWriteTarget(packName, IconRenderMode.HYBRID.getId(), itemId);
    }

    public File resolveWriteTarget(String packName, String modeId, String itemId) {
        if (!isValidPackName(packName) || !isValidItemId(itemId)) return null;
        String mode = normalizeModeId(modeId);
        if (!isValidModeDirName(mode)) return null;
        File packDir = new File(baseDir, packName);
        File modeDir = new File(packDir, mode);
        if (!modeDir.exists()) modeDir.mkdirs();
        return new File(modeDir, sanitizeItemId(itemId) + ".png");
    }

    public static String normalizeModeId(String modeId) {
        if (modeId == null || modeId.isEmpty()) return IconRenderMode.HYBRID.getId();
        IconRenderMode m = IconRenderMode.fromId(modeId);
        return m != null ? m.getId() : modeId.toLowerCase();
    }

    public static boolean isValidModeDirName(String modeId) {
        if (modeId == null || modeId.isEmpty()) return false;
        if (modeId.length() > 32) return false;
        if (modeId.contains("..")) return false;
        if (modeId.contains("/") || modeId.contains("\\") || modeId.contains(":") || modeId.contains("\0"))
            return false;
        for (int i = 0; i < modeId.length(); i++) {
            char c = modeId.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_')) return false;
        }
        return true;
    }

    /**
     * Validate a pack name: non-empty, no path separators, no '..', no ':'.
     */
    public static boolean isValidPackName(String packName) {
        if (packName == null || packName.isEmpty()) return false;
        if (packName.length() > 64) return false;
        if (packName.contains("..")) return false;
        if (packName.contains("/") || packName.contains("\\") || packName.contains(":") || packName.contains("\0"))
            return false;
        for (int i = 0; i < packName.length(); i++) {
            char c = packName.charAt(i);
            if (c < 0x20) return false;
        }
        return true;
    }

    /**
     * Validate an item id: non-empty, no '..', no leading '/', no backslash, no NUL.
     */
    public static boolean isValidItemId(String itemId) {
        if (itemId == null || itemId.isEmpty()) return false;
        if (itemId.length() > 256) return false;
        if (itemId.contains("..")) return false;
        if (itemId.startsWith("/") || itemId.startsWith("\\")) return false;
        if (itemId.contains("\0") || itemId.contains("\\")) return false;
        for (int i = 0; i < itemId.length(); i++) {
            char c = itemId.charAt(i);
            if (c < 0x20) return false;
        }
        return true;
    }

    /**
     * Convert a registry item id into a safe on-disk filename component.
     */
    public static String sanitizeItemId(String itemId) {
        StringBuilder sb = new StringBuilder(itemId.length());
        for (int i = 0; i < itemId.length(); i++) {
            char c = itemId.charAt(i);
            if (c == ':' || c == '/' || c == '\\') {
                sb.append('_');
            } else if (c < 0x20) {
                sb.append('_');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public synchronized void recordDefaultPack(String packName) {
        if (!isValidPackName(packName)) return;
        cachedDefaultPack = packName;
        File parent = defaultPackFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(defaultPackFile, false));
            writer.write(packName);
            writer.newLine();
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to record default icon pack: {}", e.getMessage());
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (Exception ignored) {}
            }
        }
    }

    public String getDefaultPack() {
        if (cachedDefaultPack != null) return cachedDefaultPack;
        if (!defaultPackFile.isFile()) return null;
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(defaultPackFile));
            String line = reader.readLine();
            if (line != null) {
                line = line.trim();
                if (isValidPackName(line)) {
                    cachedDefaultPack = line;
                    return line;
                }
            }
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to read default icon pack file: {}", e.getMessage());
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    public boolean isClearInProgress() {
        return clearInProgress;
    }

    /**
     * Delete every icon pack under {@link #baseDir} and reset the in-memory index.
     * Blocks the calling thread for disk I/O — prefer {@link #clearAllAsync} from commands.
     *
     * @return number of PNG files removed, or {@code -1} if a clear is already running
     */
    public int clearAll() {
        if (!beginClear()) {
            return -1;
        }
        try {
            int pngRemoved = deleteAllPackFilesFromDisk();
            AdvanceDataMonitor.LOG.info("[WebAE] Cleared all icon packs ({} PNG files)", pngRemoved);
            return pngRemoved;
        } finally {
            clearInProgress = false;
        }
    }

    /**
     * Reset the in-memory index immediately, then delete pack files on a background thread.
     * Does not block the caller for disk I/O.
     *
     * @param callback invoked when deletion finishes (on the background thread); may be null
     * @return {@code false} if a clear is already in progress
     */
    public boolean clearAllAsync(final ClearCallback callback) {
        if (!beginClear()) {
            return false;
        }
        Thread worker = new Thread(new Runnable() {

            @Override
            public void run() {
                int pngRemoved = 0;
                try {
                    pngRemoved = deleteAllPackFilesFromDisk();
                    AdvanceDataMonitor.LOG.info("[WebAE] Cleared all icon packs ({} PNG files)", pngRemoved);
                } catch (Throwable t) {
                    AdvanceDataMonitor.LOG.error("[WebAE] Icon pack clear failed", t);
                } finally {
                    clearInProgress = false;
                    if (callback != null) {
                        try {
                            callback.onComplete(pngRemoved);
                        } catch (Throwable t) {
                            AdvanceDataMonitor.LOG.warn("[WebAE] Icon clear callback failed: {}", t.getMessage());
                        }
                    }
                }
            }
        }, "WebAE-IconClear");
        worker.setDaemon(true);
        worker.start();
        return true;
    }

    /** Clears the index under lock and marks clear-in-progress. */
    private synchronized boolean beginClear() {
        if (clearInProgress) {
            return false;
        }
        clearInProgress = true;
        cachedDefaultPack = null;
        packIndex.clear();
        indexed = true;
        return true;
    }

    /** Disk I/O only — call after {@link #beginClear()} has reset the index. */
    private int deleteAllPackFilesFromDisk() {
        int pngRemoved = 0;
        if (!baseDir.exists()) {
            return 0;
        }
        File[] children = baseDir.listFiles();
        if (children == null) {
            return 0;
        }
        for (File child : children) {
            if (child.isDirectory() && isValidPackName(child.getName())) {
                pngRemoved += deletePackTree(child);
            } else if (child.isFile() && "default-pack.txt".equals(child.getName())) {
                child.delete();
            }
        }
        return pngRemoved;
    }

    private static int deletePackTree(File root) {
        int pngCount = 0;
        File[] children = root.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    pngCount += deletePackTree(child);
                } else if (child.isFile()) {
                    if (isPngFile(child)) {
                        if (child.delete()) {
                            pngCount++;
                        }
                    } else {
                        child.delete();
                    }
                }
            }
        }
        root.delete();
        return pngCount;
    }

    private static boolean isPngFile(File f) {
        return f.getName()
            .toLowerCase()
            .endsWith(".png");
    }

    private static String stripPng(String filename) {
        return filename.substring(0, filename.length() - 4);
    }

    private static final class StagedIconWrite {

        final File target;
        final byte[] png;
        File staged;
        File backup;
        boolean promoted;

        StagedIconWrite(File target, byte[] png) {
            this.target = target;
            this.png = png;
        }
    }

    private static final class PackEntry {

        final Map<String, File> legacyFlat = new ConcurrentHashMap<String, File>();
        final Map<String, Map<String, File>> byMode = new ConcurrentHashMap<String, Map<String, File>>();
        final Map<String, Integer> modeCounts = new LinkedHashMap<String, Integer>();

        File lookup(String modeId, String sanitizedItemId) {
            Map<String, File> modeMap = byMode.get(modeId);
            if (modeMap != null) {
                File f = modeMap.get(sanitizedItemId);
                if (f != null) return f;
            }
            if (IconRenderMode.HYBRID.getId()
                .equals(modeId)) {
                return legacyFlat.get(sanitizedItemId);
            }
            return null;
        }

        Map<String, File> iconsForMode(String modeId) {
            Map<String, File> modeMap = byMode.get(modeId);
            if (IconRenderMode.HYBRID.getId()
                .equals(modeId)) {
                Map<String, File> merged = new ConcurrentHashMap<String, File>();
                if (modeMap != null) merged.putAll(modeMap);
                merged.putAll(legacyFlat);
                return merged;
            }
            return modeMap;
        }

        int totalIconCount() {
            int max = 0;
            for (Integer n : modeCounts.values()) {
                if (n != null && n > max) max = n;
            }
            if (max == 0) return legacyFlat.size();
            return max;
        }

        List<String> availableModes() {
            List<String> modes = new ArrayList<String>();
            for (Map.Entry<String, Integer> e : modeCounts.entrySet()) {
                if (e.getValue() != null && e.getValue() > 0) {
                    modes.add(e.getKey());
                }
            }
            if (modes.isEmpty() && !legacyFlat.isEmpty()) {
                modes.add(IconRenderMode.HYBRID.getId());
            }
            return modes;
        }
    }

    /**
     * Pack info DTO for the REST API and command output.
     */
    public static class PackInfo implements Comparable<PackInfo> {

        public String packName;
        /** Largest mode count (backward compat for clients that only read iconCount). */
        public int iconCount;
        public Map<String, Integer> modeCounts;
        public List<String> availableModes;

        public PackInfo(String packName, int iconCount, Map<String, Integer> modeCounts, List<String> availableModes) {
            this.packName = packName;
            this.iconCount = iconCount;
            this.modeCounts = modeCounts;
            this.availableModes = availableModes;
        }

        @Override
        public int compareTo(PackInfo o) {
            return this.packName.compareToIgnoreCase(o.packName);
        }
    }

    public static final class IconResolveResult {

        public final File file;
        public final String requestedId;
        public final String resolvedId;
        public final String resolvedMode;
        public final boolean exact;

        IconResolveResult(File file, String requestedId, String resolvedId, String resolvedMode, boolean exact) {
            this.file = file;
            this.requestedId = requestedId;
            this.resolvedId = resolvedId;
            this.resolvedMode = resolvedMode;
            this.exact = exact;
        }

        static IconResolveResult miss(String requestedId, String resolvedId, String modeId) {
            return new IconResolveResult(null, requestedId, resolvedId, modeId, false);
        }
    }

    /** DTO for GET /api/icon/sync/manifest. */
    public static final class SyncManifest {

        public String pack;
        public String mode;
        public int iconCount;
        public String version;
        public String uploadedAt;
        public String idsHash;

        public SyncManifest(String pack, String mode, int iconCount, String version, String uploadedAt,
            String idsHash) {
            this.pack = pack;
            this.mode = mode;
            this.iconCount = iconCount;
            this.version = version;
            this.uploadedAt = uploadedAt;
            this.idsHash = idsHash;
        }

        static SyncManifest empty(String pack, String mode) {
            return new SyncManifest(pack, mode, 0, pack + "|" + mode + "|0|0|0", "", "0");
        }
    }
}
