package com.imgood.textech.webae.icon;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Client-local icon pack under {@code TeXTech/WebAE/icons-local/<pack>/nei/}.
 */
public final class IconLocalStore {

    public static final String LOCAL_DIR_NAME = "icons-local";
    private static final int CHUNK_BYTES = 28000;

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
        if (png == null || png.length == 0 || itemId == null || itemId.isEmpty()) return false;
        if (!IconStore.isValidPackName(packName != null ? packName : "default")) return false;
        File out = new File(packModeDir(instanceRoot, packName), IconStore.sanitizeItemId(itemId) + ".png");
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(out);
            fos.write(png);
            return true;
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to write local icon {}: {}", itemId, e.getMessage());
            return false;
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (Exception ignored) {}
            }
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
            player.addChatMessage(
                new ChatComponentText(EnumChatFormatting.RED + "[WebAE] Invalid icon pack name."));
            return;
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            IconStore.instance()
                .writeModeZip(pack, IconRenderMode.NEI.getId(), bos);
        } catch (IOException e) {
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
        int total = (full.length + CHUNK_BYTES - 1) / CHUNK_BYTES;
        for (int i = 0; i < total; i++) {
            int off = i * CHUNK_BYTES;
            int len = Math.min(CHUNK_BYTES, full.length - off);
            byte[] chunk = new byte[len];
            System.arraycopy(full, off, chunk, 0, len);
            AdvanceDataMonitor.ADMCHANEL.sendTo(
                new PacketWebIconPullZip(i == 0, i == total - 1, i, total, pack, chunk),
                player);
        }
        player.addChatMessage(
            new ChatComponentText(
                EnumChatFormatting.GREEN + "[WebAE] Pulling server icons: pack=" + pack + " (" + total + " chunks)"));
    }

    @SideOnly(Side.CLIENT)
    public static void onPullChunk(boolean isStart, boolean isEnd, int index, int total, String packName,
        byte[] chunk) {
        PullSession session = PullSession.INSTANCE;
        if (isStart) {
            session.reset(packName);
        }
        if (session.packName == null) {
            session.reset(packName);
        }
        session.accept(index, chunk);
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

        void reset(String pack) {
            packName = pack;
            chunks.clear();
            expectedTotal = 0;
        }

        void accept(int index, byte[] chunk) {
            while (chunks.size() <= index) {
                chunks.add(null);
            }
            chunks.set(index, chunk);
        }

        void finishAndExtract() {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.mcDataDir == null) return;
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
                            EnumChatFormatting.GREEN + "[WebAE] Local icons ready: " + written
                                + " files → " + dest.getAbsolutePath()));
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
        if (zipBytes == null || zipBytes.length == 0) return 0;
        File destDir = packModeDir(instanceRoot, packName);
        int written = 0;
        ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes));
        try {
            ZipEntry entry;
            byte[] buf = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (name == null) continue;
                String base = name.replace('\\', '/');
                int slash = base.lastIndexOf('/');
                if (slash >= 0) base = base.substring(slash + 1);
                if (!base.toLowerCase()
                    .endsWith(".png")) continue;
                File out = new File(destDir, base);
                FileOutputStream fos = new FileOutputStream(out);
                try {
                    int n;
                    while ((n = zis.read(buf)) > 0) {
                        fos.write(buf, 0, n);
                    }
                } finally {
                    fos.close();
                }
                written++;
            }
        } finally {
            zis.close();
        }
        return written;
    }

    /** Prefer local sibling {@code icons/} copy when available (SP / shared instance). */
    @SideOnly(Side.CLIENT)
    public static int tryCopyFromLocalServerIcons(File instanceRoot, String packName) {
        File src = new File(
            WebAeLocalDataDir.resolve(instanceRoot),
            "icons" + File.separator + packName + File.separator + IconRenderMode.NEI.getId());
        if (!src.isDirectory()) return -1;
        File dest = packModeDir(instanceRoot, packName);
        File[] files = src.listFiles();
        if (files == null) return 0;
        int copied = 0;
        byte[] buf = new byte[8192];
        for (File f : files) {
            if (f == null || !f.getName()
                .endsWith(".png")) continue;
            File out = new File(dest, f.getName());
            FileInputStream in = null;
            FileOutputStream os = null;
            try {
                in = new FileInputStream(f);
                os = new FileOutputStream(out);
                int n;
                while ((n = in.read(buf)) > 0) {
                    os.write(buf, 0, n);
                }
                copied++;
            } catch (Exception ignored) {} finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (Exception ignored) {}
                }
                if (os != null) {
                    try {
                        os.close();
                    } catch (Exception ignored) {}
                }
            }
        }
        return copied;
    }
}
