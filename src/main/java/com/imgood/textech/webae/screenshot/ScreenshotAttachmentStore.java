package com.imgood.textech.webae.screenshot;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.TeXTechDataDir;

/** Bounded server-side files used by WebAE chat screenshot attachments. */
public final class ScreenshotAttachmentStore {

    private static final ScreenshotAttachmentStore INSTANCE = new ScreenshotAttachmentStore();

    private ScreenshotAttachmentStore() {}

    public static ScreenshotAttachmentStore instance() {
        return INSTANCE;
    }

    public synchronized StoredAttachment saveJpeg(byte[] bytes, int width, int height, String originalName)
        throws IOException {
        if (bytes == null || bytes.length == 0) throw new IOException("empty screenshot");
        String id = UUID.randomUUID().toString().replace("-", "");
        File target = new File(root(), id + ".jpg");
        File temporary = new File(root(), id + ".tmp");
        FileOutputStream output = new FileOutputStream(temporary);
        try {
            output.write(bytes);
            output.flush();
        } finally {
            output.close();
        }
        if (!temporary.renameTo(target)) {
            copy(temporary, target);
            if (!temporary.delete()) AdvanceDataMonitor.LOG.warn("[Screenshot] Failed to remove {}", temporary);
        }
        target.setLastModified(System.currentTimeMillis());
        prune();
        return new StoredAttachment(id, target, safeName(originalName), "image/jpeg", width, height, bytes.length);
    }

    public File resolve(String id) {
        if (!isValidId(id)) return null;
        File file = new File(root(), id + ".jpg");
        return file.isFile() ? file : null;
    }

    public static boolean isValidId(String id) {
        if (id == null || id.length() != 32) return false;
        for (int i = 0; i < id.length(); i++) {
            char value = id.charAt(i);
            if (!((value >= '0' && value <= '9') || (value >= 'a' && value <= 'f'))) return false;
        }
        return true;
    }

    private File root() {
        return TeXTechDataDir.webAeDir("chat-attachments");
    }

    private void prune() {
        File[] values = root().listFiles();
        if (values == null || values.length == 0) return;
        List<File> files = new ArrayList<File>();
        for (File file : values) if (file.isFile() && file.getName().endsWith(".jpg")) files.add(file);
        Collections.sort(files, NEWEST_FIRST);
        int maxFiles = Math.max(1, Config.webScreenshotServerHistoryMaxFiles);
        long maxBytes = Math.max(16L, Config.webScreenshotServerHistoryMaxMB) * 1024L * 1024L;
        long kept = 0L;
        for (int i = 0; i < files.size(); i++) {
            File file = files.get(i);
            boolean keep = i < maxFiles && kept + file.length() <= maxBytes;
            if (keep) kept += file.length();
            else if (!file.delete()) AdvanceDataMonitor.LOG.warn("[Screenshot] Failed to prune {}", file);
        }
    }

    private static void copy(File source, File target) throws IOException {
        java.io.FileInputStream input = new java.io.FileInputStream(source);
        try {
            FileOutputStream output = new FileOutputStream(target);
            try {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
            } finally {
                output.close();
            }
        } finally {
            input.close();
        }
    }

    private static String safeName(String value) {
        String name = value == null ? "screenshot.jpg" : value.replace('\\', '_').replace('/', '_').trim();
        if (name.isEmpty()) name = "screenshot.jpg";
        return name.length() <= 96 ? name : name.substring(0, 96);
    }

    private static final Comparator<File> NEWEST_FIRST = new Comparator<File>() {

        @Override
        public int compare(File left, File right) {
            if (left.lastModified() == right.lastModified()) return right.getName().compareTo(left.getName());
            return left.lastModified() < right.lastModified() ? 1 : -1;
        }
    };

    public static final class StoredAttachment {

        public final String id;
        public final File file;
        public final String fileName;
        public final String mimeType;
        public final int width;
        public final int height;
        public final int bytes;

        StoredAttachment(String id, File file, String fileName, String mimeType, int width, int height, int bytes) {
            this.id = id;
            this.file = file;
            this.fileName = fileName;
            this.mimeType = mimeType;
            this.width = width;
            this.height = height;
            this.bytes = bytes;
        }
    }
}
