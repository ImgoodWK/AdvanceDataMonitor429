package com.imgood.textech.webae.icon;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.webae.WebAeLocalDataDir;
import com.imgood.textech.webae.network.PacketWebIconPullZip;
import com.imgood.textech.webae.network.WebAeBinaryTransfer;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Client-local icon pack under {@code TeXTech/WebAE/icons-local/<pack>/nei/}.
 */
public final class IconLocalStore {

    public static final String LOCAL_DIR_NAME = "icons-local";
    private static final int CHUNK_BYTES = PacketWebIconPullZip.MAX_CHUNK_BYTES;
    private static final long MAX_EXTRACTED_BYTES = 16L * 1024L * 1024L;

    private IconLocalStore() {}

    public static File baseDir(File instanceRoot) {
        File dir = new File(WebAeLocalDataDir.resolve(instanceRoot), LOCAL_DIR_NAME);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static File packModeDir(File instanceRoot, String packName) {
        String pack = (packName != null && !packName.isEmpty()) ? packName : "default";
        File dir = new File(baseDir(instanceRoot), pack + File.separator + IconRenderMode.NEI.getId());
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static boolean writePng(File instanceRoot, String packName, String itemId, byte[] png) {
        String pack = packName != null ? packName : "default";
        if (!IconStore.isValidPng(png) || !IconStore.isValidItemId(itemId)
            || !IconStore.isValidPackName(pack)) return false;
        File out = new File(packModeDir(instanceRoot, packName), IconStore.sanitizeItemId(itemId) + ".png");
        try {
            if (!writeAtomically(out, png)) return false;
            return true;
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to write local icon {}: {}", itemId, e.getMessage());
            return false;
        }
    }

    public static int writeBundleBase64(File instanceRoot, String packName, java.util.Map<String, String> bundle) {
        if (bundle == null || bundle.isEmpty()) return 0;
        int written = 0;
        for (java.util.Map.Entry<String, String> e : bundle.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) continue;
            try {
                byte[] png = javax.xml.bind.DatatypeConverter.parseBase64Binary(e.getValue());
                if (writePng(instanceRoot, packName, e.getKey(), png)) {
                    written++;
                }
            } catch (Exception ignored) {}
        }
        return written;
    }

    /** Server: zip pack/mode and stream chunks to the player. */
    public static void sendPackZipToPlayer(EntityPlayerMP player, String packName) {
        if (player == null) return;
        String pack = (packName != null && !packName.isEmpty()) ? packName : "default";
        if (!IconStore.isValidPackName(pack)) {
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "[WebAE] Invalid icon pack name."));
            return;
        }
        // IconStore enforces the same cap through the output stream while ZIP bytes are produced;
        // this buffer therefore never grows beyond the client transfer budget.
        ByteArrayOutputStream bos = new ByteArrayOutputStream(8192);
        try {
            IconStore.instance()
                .writeModeZip(pack, IconRenderMode.NEI.getId(), bos);
        } catch (IOException | RuntimeException e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to zip icons for pull: {}", e.getMessage());
            player.addChatMessage(
                new ChatComponentText(EnumChatFormatting.RED + "[WebAE] Failed to prepare icon pack zip."));
            return;
        }
        byte[] full = bos.toByteArray();
        if (full.length == 0) {
            player.addChatMessage(
                new ChatComponentText(EnumChatFormatting.YELLOW + "[WebAE] No server icons for pack: " + pack));
            return;
        }
        if (full.length > PacketWebIconPullZip.MAX_ZIP_BYTES) {
            player.addChatMessage(
                new ChatComponentText(EnumChatFormatting.RED + "[WebAE] Icon pack is too large to send safely."));
            return;
        }
        int total = (full.length + CHUNK_BYTES - 1) / CHUNK_BYTES;
        for (int i = 0; i < total; i++) {
            int off = i * CHUNK_BYTES;
            int len = Math.min(CHUNK_BYTES, full.length - off);
            byte[] chunk = new byte[len];
            System.arraycopy(full, off, chunk, 0, len);
            AdvanceDataMonitor.ADMCHANEL
                .sendTo(new PacketWebIconPullZip(i == 0, i == total - 1, i, total, pack, chunk), player);
        }
        player.addChatMessage(
            new ChatComponentText(
                EnumChatFormatting.GREEN + "[WebAE] Pulling server icons: pack=" + pack + " (" + total + " chunks)"));
    }

    @SideOnly(Side.CLIENT)
    public static void onPullChunk(boolean isStart, boolean isEnd, int index, int total, String packName,
        byte[] chunk) {
        PullSession session = PullSession.INSTANCE;
        if (!IconStore.isValidPackName(packName) || total < 1 || total > PacketWebIconPullZip.MAX_TOTAL_CHUNKS
            || index < 0 || index >= total || isStart != (index == 0) || isEnd != (index == total - 1)) {
            session.reset(null);
            return;
        }
        if (isStart) {
            session.reset(packName, total);
        }
        if (!session.accept(packName, total, index, chunk)) {
            session.reset(null);
            return;
        }
        if (isEnd) {
            session.finishAndExtract();
        }
    }

    @SideOnly(Side.CLIENT)
    private static final class PullSession {

        static final PullSession INSTANCE = new PullSession();

        String packName;
        final List<byte[]> chunks = new ArrayList<byte[]>();
        int expectedTotal;
        int nextIndex;
        int receivedBytes;
        long lastTouchedMs;

        void reset(String pack) {
            reset(pack, 0);
        }

        void reset(String pack, int total) {
            packName = pack;
            chunks.clear();
            expectedTotal = total;
            nextIndex = 0;
            receivedBytes = 0;
            lastTouchedMs = System.currentTimeMillis();
        }

        boolean accept(String pack, int total, int index, byte[] chunk) {
            long now = System.currentTimeMillis();
            if (packName == null || !packName.equals(pack) || expectedTotal != total
                || now - lastTouchedMs > WebAeBinaryTransfer.SESSION_TTL_MS || index != nextIndex
                || chunk == null || chunk.length == 0 || chunk.length > PacketWebIconPullZip.MAX_CHUNK_BYTES
                || receivedBytes > PacketWebIconPullZip.MAX_ZIP_BYTES - chunk.length) {
                return false;
            }
            chunks.add(chunk.clone());
            receivedBytes += chunk.length;
            nextIndex++;
            lastTouchedMs = now;
            return true;
        }

        void finishAndExtract() {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.mcDataDir == null || expectedTotal < 1 || nextIndex != expectedTotal
                || receivedBytes <= 0 || receivedBytes > PacketWebIconPullZip.MAX_ZIP_BYTES) {
                reset(null);
                return;
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try {
                for (byte[] c : chunks) {
                    if (c != null) bos.write(c);
                }
                int written = extractZipToLocal(mc.mcDataDir, packName, bos.toByteArray());
                File dest = packModeDir(mc.mcDataDir, packName);
                if (mc.thePlayer != null) {
                    mc.thePlayer.addChatMessage(
                        new ChatComponentText(
                            EnumChatFormatting.GREEN + "[WebAE] Local icons ready: "
                                + written
                                + " files → "
                                + dest.getAbsolutePath()));
                }
            } catch (Exception e) {
                AdvanceDataMonitor.LOG.warn("[WebAE] Icon pull extract failed: {}", e.getMessage());
                if (mc.thePlayer != null) {
                    mc.thePlayer.addChatMessage(
                        new ChatComponentText(EnumChatFormatting.RED + "[WebAE] Icon pull failed: " + e.getMessage()));
                }
            } finally {
                reset(null);
            }
        }
    }

    @SideOnly(Side.CLIENT)
    public static int extractZipToLocal(File instanceRoot, String packName, byte[] zipBytes) throws IOException {
        if (zipBytes == null || zipBytes.length == 0 || zipBytes.length > PacketWebIconPullZip.MAX_ZIP_BYTES) return 0;
        if (!IconStore.isValidPackName(packName)) throw new IOException("Invalid icon pack name");
        File destDir = new File(
            new File(WebAeLocalDataDir.resolve(instanceRoot), LOCAL_DIR_NAME),
            packName + File.separator + IconRenderMode.NEI.getId());
        List<StagedLocalIcon> staged = new ArrayList<StagedLocalIcon>();
        java.util.HashSet<String> stagedTargets = new java.util.HashSet<String>();
        long extractedBytes = 0L;
        ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes));
        try {
            ZipEntry entry;
            byte[] buf = new byte[8192];
            int entryCount = 0;
            while ((entry = zis.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > IconStore.MAX_ICON_PACK_ENTRIES) {
                    throw new IOException("Icon zip contains too many files");
                }
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (name == null) continue;
                String base = name.replace('\\', '/');
                int slash = base.lastIndexOf('/');
                if (slash >= 0) base = base.substring(slash + 1);
                if (!base.toLowerCase()
                    .endsWith(".png")) continue;
                String itemId = base.substring(0, base.length() - 4);
                if (!IconStore.isValidItemId(itemId)) {
                    throw new IOException("Invalid icon entry name");
                }
                String safeName = IconStore.sanitizeItemId(itemId) + ".png";
                File out = new File(destDir, safeName);
                String canonicalDest = destDir.getCanonicalPath();
                String canonicalOut = out.getCanonicalPath();
                if (!canonicalOut.startsWith(canonicalDest + File.separator) || !stagedTargets.add(canonicalOut)) {
                    throw new IOException("Invalid or duplicate icon entry path");
                }
                ByteArrayOutputStream iconBuffer = new ByteArrayOutputStream(8192);
                long entryBytes = 0L;
                int n;
                while ((n = zis.read(buf)) >= 0) {
                    if (n == 0) continue;
                    if (entryBytes > IconStore.MAX_PNG_BYTES - n
                        || extractedBytes > MAX_EXTRACTED_BYTES - n) {
                        throw new IOException("Icon zip expands beyond the safe limit");
                    }
                    iconBuffer.write(buf, 0, n);
                    entryBytes += n;
                    extractedBytes += n;
                }
                byte[] icon = iconBuffer.toByteArray();
                if (!IconStore.isValidPng(icon)) {
                    throw new IOException("Icon zip contains an invalid PNG");
                }
                staged.add(new StagedLocalIcon(out, icon));
                zis.closeEntry();
            }
            if (!promoteStagedIcons(staged)) throw new IOException("Failed to promote local icon pack");
        } finally {
            zis.close();
        }
        return staged.size();
    }

    /** Prefer local sibling {@code icons/} copy when available (SP / shared instance). */
    @SideOnly(Side.CLIENT)
    public static int tryCopyFromLocalServerIcons(File instanceRoot, String packName) {
        if (!IconStore.isValidPackName(packName)) return 0;
        File src = new File(
            WebAeLocalDataDir.resolve(instanceRoot),
            "icons" + File.separator + packName + File.separator + IconRenderMode.NEI.getId());
        if (!src.isDirectory()) return -1;
        File[] files = src.listFiles();
        if (files == null) return 0;
        int copied = 0;
        for (File f : files) {
            if (f == null || !f.getName()
                .toLowerCase()
                .endsWith(".png")) continue;
            try {
                String itemId = f.getName().substring(0, f.getName().length() - 4);
                byte[] png = readBoundedFile(f);
                if (png != null && writePng(instanceRoot, packName, itemId, png)) copied++;
            } catch (Exception ignored) {}
        }
        return copied;
    }

    private static byte[] readBoundedFile(File file) throws IOException {
        if (file == null || !file.isFile() || file.length() < 1 || file.length() > IconStore.MAX_PNG_BYTES) {
            return null;
        }
        long expectedLength = file.length();
        byte[] bytes = new byte[(int) expectedLength];
        FileInputStream input = null;
        try {
            input = new FileInputStream(file);
            int offset = 0;
            while (offset < bytes.length) {
                int read = input.read(bytes, offset, bytes.length - offset);
                if (read < 0) return null;
                if (read == 0) continue;
                offset += read;
            }
            return input.read() < 0 ? bytes : null;
        } finally {
            if (input != null) input.close();
        }
    }

    private static boolean writeAtomically(File target, byte[] bytes) throws IOException {
        if (target == null || bytes == null) return false;
        File parent = target.getParentFile();
        if (parent == null || (!parent.exists() && !parent.mkdirs())) return false;
        File temporary = null;
        FileOutputStream output = null;
        try {
            temporary = File.createTempFile("webae-local-icon-", ".tmp", parent);
            output = new FileOutputStream(temporary);
            output.write(bytes);
            output.flush();
            output.close();
            output = null;
            moveAtomically(temporary, target);
            temporary = null;
            return true;
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (IOException ignored) {}
            }
            deleteQuietly(temporary);
        }
    }

    private static boolean promoteStagedIcons(List<StagedLocalIcon> icons) {
        if (icons == null || icons.isEmpty()) return true;
        boolean promotedAll = false;
        try {
            for (StagedLocalIcon icon : icons) {
                if (icon == null || icon.file == null || !IconStore.isValidPng(icon.bytes)) return false;
                File parent = icon.file.getParentFile();
                if (parent == null || (!parent.exists() && !parent.mkdirs())) return false;
                icon.stagedFile = File.createTempFile("webae-local-stage-", ".tmp", parent);
                writeFile(icon.stagedFile, icon.bytes);
            }
            for (StagedLocalIcon icon : icons) {
                File parent = icon.file.getParentFile();
                if (icon.file.isFile()) {
                    icon.backupFile = File.createTempFile("webae-local-backup-", ".bak", parent);
                    moveAtomically(icon.file, icon.backupFile);
                }
                moveAtomically(icon.stagedFile, icon.file);
                icon.stagedFile = null;
                icon.promoted = true;
            }
            promotedAll = true;
            return true;
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to promote local icon pack: {}", e.getMessage());
            return false;
        } finally {
            if (!promotedAll) rollbackStagedIcons(icons);
            for (StagedLocalIcon icon : icons) {
                if (icon == null) continue;
                deleteQuietly(icon.stagedFile);
                if (promotedAll) deleteQuietly(icon.backupFile);
            }
        }
    }

    private static void rollbackStagedIcons(List<StagedLocalIcon> icons) {
        for (int i = icons.size() - 1; i >= 0; i--) {
            StagedLocalIcon icon = icons.get(i);
            if (icon == null || icon.file == null) continue;
            if (icon.promoted) deleteQuietly(icon.file);
            if (icon.backupFile != null && icon.backupFile.isFile()) {
                try {
                    moveAtomically(icon.backupFile, icon.file);
                    icon.backupFile = null;
                } catch (IOException e) {
                    AdvanceDataMonitor.LOG.warn(
                        "[WebAE] Failed to restore previous local icon {}: {}",
                        icon.file.getName(),
                        e.getMessage());
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
            AdvanceDataMonitor.LOG.debug("[WebAE] Failed to remove temporary local icon {}", file.getAbsolutePath());
        }
    }

    private static final class StagedLocalIcon {

        final File file;
        final byte[] bytes;
        File stagedFile;
        File backupFile;
        boolean promoted;

        StagedLocalIcon(File file, byte[] bytes) {
            this.file = file;
            this.bytes = bytes;
        }
    }
}
