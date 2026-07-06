package com.imgood.textech.webae.icon;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.webae.WebAeLocalDataDir;

/**
 * Imports pre-rendered PNG icons from a NESQL exporter repository into WebAE icon packs.
 */
public final class NesqlIconImporter {

    private NesqlIconImporter() {}

    public static int importFromRepository(String packName, String repositorySubPath) {
        if (!IconStore.isValidPackName(packName)) return 0;
        String root = WebAeLocalDataDir.resolveNesqlRepositoryPath();
        WebAeLocalDataDir.serverDir();
        File imagesDir = resolveImagesDir(new File(root), repositorySubPath);
        if (imagesDir == null || !imagesDir.isDirectory()) {
            AdvanceDataMonitor.LOG.warn("[WebAE] NESQL images directory not found: {}", root);
            return 0;
        }
        File destDir = new File(
            IconStore.instance()
                .getBaseDir(),
            packName + File.separator + IconRenderMode.NEI.getId());
        destDir.mkdirs();
        int copied = 0;
        int skipped = 0;
        File[] files = imagesDir.listFiles();
        if (files == null) return 0;
        for (File src : files) {
            if (src == null || !src.getName()
                .endsWith(".png")) continue;
            String base = src.getName()
                .substring(
                    0,
                    src.getName()
                        .length() - 4);
            String itemId = base.replace('~', ':')
                .replace('_', ':');
            File out = new File(destDir, IconStore.sanitizeItemId(itemId) + ".png");
            if (out.exists()) {
                skipped++;
                continue;
            }
            try {
                Files.copy(src.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING);
                copied++;
            } catch (Exception e) {
                AdvanceDataMonitor.LOG.debug("[WebAE] NESQL icon import skip {}: {}", src.getName(), e.getMessage());
            }
        }
        if (copied > 0) {
            IconStore.instance()
                .refreshPack(packName);
            IconStore.instance()
                .recordDefaultPack(packName);
            IconStore.instance()
                .recordModeUpload(packName, IconRenderMode.NEI.getId(), copied);
        }
        AdvanceDataMonitor.LOG.info(
            "[WebAE] NESQL icon import: {} copied, {} skipped (existing) from {}",
            copied,
            skipped,
            imagesDir.getAbsolutePath());
        return copied;
    }

    private static File resolveImagesDir(File root, String subPath) {
        if (subPath != null && !subPath.isEmpty()) {
            File direct = new File(root, subPath.replace('/', File.separatorChar));
            if (direct.isDirectory()) return direct;
        }
        File nested = new File(root, "images");
        if (nested.isDirectory()) return nested;
        File repoImages = new File(root, "nesql-repository" + File.separator + "images");
        if (repoImages.isDirectory()) return repoImages;
        return root.isDirectory() ? root : null;
    }
}
