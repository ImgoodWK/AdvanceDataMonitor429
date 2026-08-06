package com.imgood.textech.webae.cpu;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.TeXTechDataDir;
import com.imgood.textech.webae.access.WebAeNetworkKeys;

/**
 * Atomic, owner-isolated JSON persistence for CPU history.
 *
 * <p>
 * Every write is prepared in the target directory and then replaced
 * atomically where the filesystem supports it. A failed write or move leaves
 * the previous target untouched.
 * </p>
 */
public final class CpuHistoryStore {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .setPrettyPrinting()
        .create();

    private final File root;
    private final FileMover mover;

    public CpuHistoryStore() {
        this(TeXTechDataDir.webAeDir("cpu-history"), new AtomicFileMover());
    }

    CpuHistoryStore(File root, FileMover mover) {
        this.root = root;
        this.mover = mover != null ? mover : new AtomicFileMover();
    }

    public CpuHistoryState load(String ownerUuid, int networkId, String networkKey) {
        CpuHistoryState empty = new CpuHistoryState(ownerUuid, networkId, networkKey);
        File primary = fileFor(ownerUuid, networkId, networkKey);
        CpuHistoryState state = loadMatching(primary, ownerUuid, networkKey);
        if (state == null) {
            // NetworkRegistry may legally reorder its numeric ids. Only the
            // mutation/sampler path reaches this method; HTTP reads use the
            // in-memory cache and never scan this directory.
            state = findByStableIdentity(ownerUuid, networkKey, primary);
        }
        if (state == null) {
            return empty;
        }
        normalizeLoadedState(state, ownerUuid, networkId, networkKey);
        return state;
    }

    public boolean save(CpuHistoryState state) {
        if (state == null || !isSafeOwnerUuid(state.ownerUuid)
            || state.networkId < 0
            || !WebAeNetworkKeys.isValidKeyFormat(state.networkKey)) {
            return false;
        }
        File target = resolveSaveTarget(state);
        if (target == null) {
            return false;
        }
        File parent = target.getParentFile();
        if (parent == null || (!parent.exists() && !parent.mkdirs())) {
            warn("[WebAE] Failed to create CPU history directory for {}", target.getAbsolutePath());
            return false;
        }

        File temp = null;
        Writer writer = null;
        try {
            temp = File.createTempFile(target.getName() + ".", ".tmp", parent);
            writer = new OutputStreamWriter(new FileOutputStream(temp), "UTF-8");
            GSON.toJson(state, writer);
            writer.close();
            writer = null;
            mover.move(temp, target);
            state.backingFile = target;
            return true;
        } catch (Exception e) {
            warn("[WebAE] Failed to save CPU history {}", target.getAbsolutePath(), e);
            return false;
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {}
            }
            if (temp != null && temp.exists() && !temp.delete()) {
                debug("[WebAE] Could not remove failed CPU history temp {}", temp.getAbsolutePath());
            }
        }
    }

    File fileFor(String ownerUuid, int networkId, String networkKey) {
        if (!isSafeOwnerUuid(ownerUuid) || networkId < 0 || !WebAeNetworkKeys.isValidKeyFormat(networkKey)) {
            return null;
        }
        try {
            File canonicalRoot = root.getCanonicalFile();
            File target = new File(new File(canonicalRoot, ownerUuid), networkId + ".json").getCanonicalFile();
            String rootPath = canonicalRoot.getPath();
            String targetPath = target.getPath();
            if (!targetPath.startsWith(rootPath + File.separator)) {
                return null;
            }
            return target;
        } catch (IOException e) {
            warn("[WebAE] Failed to resolve CPU history path", e);
            return null;
        }
    }

    static boolean isSafeOwnerUuid(String ownerUuid) {
        return ownerUuid != null && ownerUuid.matches("[A-Za-z0-9_-]{1,128}");
    }

    private CpuHistoryState findByStableIdentity(String ownerUuid, String networkKey, File primary) {
        File ownerDirectory = ownerDirectory(ownerUuid);
        if (ownerDirectory == null || !ownerDirectory.isDirectory()) {
            return null;
        }
        File[] files = ownerDirectory.listFiles();
        if (files == null) {
            return null;
        }
        // Deterministic iteration makes an exceptional duplicate recoverable
        // and predictable. The primary slot is checked first by the caller.
        Arrays.sort(files, new Comparator<File>() {

            @Override
            public int compare(File a, File b) {
                return a.getName()
                    .compareTo(b.getName());
            }
        });
        for (int i = 0; i < files.length; i++) {
            File candidate = files[i];
            if (!candidate.isFile() || !candidate.getName()
                .endsWith(".json") || sameFile(candidate, primary)) {
                continue;
            }
            CpuHistoryState state = loadMatching(candidate, ownerUuid, networkKey);
            if (state != null) {
                return state;
            }
        }
        return null;
    }

    private CpuHistoryState loadMatching(File file, String ownerUuid, String networkKey) {
        if (file == null || !file.isFile()) {
            return null;
        }
        Reader reader = null;
        try {
            reader = new InputStreamReader(new FileInputStream(file), "UTF-8");
            CpuHistoryState state = GSON.fromJson(reader, CpuHistoryState.class);
            if (!isMatchingStableIdentity(state, ownerUuid, networkKey)) {
                return null;
            }
            state.backingFile = file.getCanonicalFile();
            return state;
        } catch (Exception e) {
            warn("[WebAE] Failed to load CPU history {}", file.getAbsolutePath(), e);
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {}
            }
        }
    }

    private void normalizeLoadedState(CpuHistoryState state, String ownerUuid, int networkId, String networkKey) {
        if (state.jobs == null) {
            state.jobs = new java.util.ArrayList<CpuJobHistoryDto>();
        }
        if (state.snapshots == null) {
            state.snapshots = new java.util.ArrayList<CpuSnapshotHistoryDto>();
        }
        state.schemaVersion = 1;
        state.ownerUuid = ownerUuid;
        state.networkId = networkId;
        state.networkKey = networkKey;
        for (int i = 0; i < state.jobs.size(); i++) {
            CpuJobHistoryDto job = state.jobs.get(i);
            if (job == null) {
                continue;
            }
            job.ownerUuid = ownerUuid;
            job.networkId = networkId;
            job.networkKey = networkKey;
        }
        state.dirty = false;
    }

    private File resolveSaveTarget(CpuHistoryState state) {
        File backing = canonicalOwnedFile(state.backingFile, state.ownerUuid, state.networkKey);
        if (backing != null) {
            return backing;
        }
        File primary = fileFor(state.ownerUuid, state.networkId, state.networkKey);
        if (primary == null || !primary.isFile() || isOwnedFile(primary, state.ownerUuid, state.networkKey)) {
            return primary;
        }
        // Do not overwrite a reassigned numeric slot. This is an exceptional
        // new identity with no older backing file, so store it in a stable
        // collision sidecar which is also considered by the mutation-only
        // recovery scan above.
        File sidecar = sidecarFileFor(state.ownerUuid, state.networkId, state.networkKey);
        if (sidecar != null && (!sidecar.isFile() || isOwnedFile(sidecar, state.ownerUuid, state.networkKey))) {
            return sidecar;
        }
        warn(
            "[WebAE] Refusing to overwrite CPU history owned by another stable network at {}",
            primary.getAbsolutePath());
        return null;
    }

    private File canonicalOwnedFile(File candidate, String ownerUuid, String networkKey) {
        if (candidate == null || !candidate.isFile() || !isInsideOwnerDirectory(candidate, ownerUuid)) {
            return null;
        }
        return isOwnedFile(candidate, ownerUuid, networkKey) ? candidate : null;
    }

    private boolean isOwnedFile(File file, String ownerUuid, String networkKey) {
        return loadMatching(file, ownerUuid, networkKey) != null;
    }

    private File ownerDirectory(String ownerUuid) {
        if (!isSafeOwnerUuid(ownerUuid)) {
            return null;
        }
        try {
            File canonicalRoot = root.getCanonicalFile();
            File owner = new File(canonicalRoot, ownerUuid).getCanonicalFile();
            String rootPath = canonicalRoot.getPath();
            if (!owner.getPath()
                .startsWith(rootPath + File.separator)) {
                return null;
            }
            return owner;
        } catch (IOException e) {
            warn("[WebAE] Failed to resolve CPU history owner directory", e);
            return null;
        }
    }

    private boolean isInsideOwnerDirectory(File candidate, String ownerUuid) {
        File owner = ownerDirectory(ownerUuid);
        if (owner == null) {
            return false;
        }
        try {
            return candidate.getCanonicalFile()
                .getPath()
                .startsWith(owner.getPath() + File.separator);
        } catch (IOException e) {
            return false;
        }
    }

    private File sidecarFileFor(String ownerUuid, int networkId, String networkKey) {
        File owner = ownerDirectory(ownerUuid);
        if (owner == null || networkId < 0 || !WebAeNetworkKeys.isValidKeyFormat(networkKey)) {
            return null;
        }
        String suffix = stableKeyDigest(networkKey);
        if (suffix == null) {
            return null;
        }
        try {
            File sidecar = new File(owner, networkId + "-" + suffix + ".json").getCanonicalFile();
            return sidecar.getPath()
                .startsWith(owner.getPath() + File.separator) ? sidecar : null;
        } catch (IOException e) {
            warn("[WebAE] Failed to resolve CPU history sidecar path", e);
            return null;
        }
    }

    private static String stableKeyDigest(String networkKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(networkKey.getBytes("UTF-8"));
            StringBuilder out = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                String hex = Integer.toHexString(digest[i] & 0xff);
                if (hex.length() == 1) {
                    out.append('0');
                }
                out.append(hex);
            }
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            warn("[WebAE] SHA-256 is unavailable for CPU history sidecar", e);
            return null;
        } catch (java.io.UnsupportedEncodingException e) {
            return null;
        }
    }

    private static boolean isMatchingStableIdentity(CpuHistoryState state, String ownerUuid, String networkKey) {
        return state != null && ownerUuid.equals(state.ownerUuid) && networkKey.equals(state.networkKey);
    }

    private static boolean sameFile(File a, File b) {
        if (a == null || b == null) {
            return false;
        }
        try {
            return a.getCanonicalFile()
                .equals(b.getCanonicalFile());
        } catch (IOException e) {
            return a.equals(b);
        }
    }

    private static void warn(String message, Object... args) {
        try {
            com.imgood.textech.AdvanceDataMonitor.LOG.warn(message, args);
        } catch (Throwable ignored) {
            // Pure data tests and early bootstrap can run without Forge's
            // LaunchClassLoader. Persistence must still fail safely.
        }
    }

    private static void debug(String message, Object... args) {
        try {
            com.imgood.textech.AdvanceDataMonitor.LOG.debug(message, args);
        } catch (Throwable ignored) {}
    }

    interface FileMover {

        void move(File source, File target) throws IOException;
    }

    private static final class AtomicFileMover implements FileMover {

        @Override
        public void move(File source, File target) throws IOException {
            try {
                Files.move(
                    source.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}
