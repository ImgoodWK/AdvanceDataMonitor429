package com.imgood.textech.webae.worldmap;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.imgood.textech.TeXTechDataDir;

/**
 * Disk persistence for world-map annotations.
 *
 * <p>Each owner/network pair is stored in one JSON array at
 * {@code TeXTech/WebAE/map-annotations/{ownerUuid}/{networkId}.json}.  The
 * store does no HTTP or permission checks.  It does enforce path safety,
 * record limits and record validation so that a malformed file cannot become
 * an unsafe path or an unbounded allocation.</p>
 */
public final class WorldMapAnnotationStore {

    public static final int MAX_RECORDS = 500;
    public static final int MAX_FILE_BYTES = 2 * 1024 * 1024;
    public static final int MAX_CREATED_BY_CODE_POINTS = 128;

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String FILE_SUFFIX = ".json";
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    private static final ConcurrentHashMap<String, Object> LOCKS = new ConcurrentHashMap<String, Object>();
    private static final AtomicWriteStrategy DEFAULT_WRITER = new AtomicWriteStrategy() {
        @Override
        public void write(File target, byte[] bytes) throws IOException {
            writeAtomically(target, bytes);
        }
    };

    private final File rootDirectory;
    private final AtomicWriteStrategy writer;

    /** Creates a production store under {@code TeXTech/WebAE/map-annotations}. */
    public WorldMapAnnotationStore() {
        this(TeXTechDataDir.webAeDir("map-annotations"));
    }

    /**
     * Creates a store rooted at an isolated directory.  This constructor is
     * intentionally public so tests and embedding servers can provide a
     * dedicated data root without changing production global paths.
     */
    public WorldMapAnnotationStore(File rootDirectory) {
        this(rootDirectory, DEFAULT_WRITER);
    }

    /**
     * Creates a store with an injectable atomic writer.  A writer that throws
     * is useful for proving failed writes leave the previous file intact.
     */
    WorldMapAnnotationStore(File rootDirectory, AtomicWriteStrategy writer) {
        this.rootDirectory = rootDirectory == null ? null : rootDirectory.getAbsoluteFile();
        this.writer = writer == null ? DEFAULT_WRITER : writer;
    }

    public static File annotationsRoot() {
        return TeXTechDataDir.webAeDir("map-annotations");
    }

    public File rootDirectory() {
        return rootDirectory;
    }

    /** Returns the owner/network file, or {@code null} when its key is invalid. */
    public File fileFor(String ownerUuid, int networkId) {
        String owner = WorldMapPacketAuthorization.canonicalOwnerUuid(ownerUuid);
        if (owner == null || !WorldMapPacketAuthorization.isValidNetworkId(networkId) || rootDirectory == null) {
            return null;
        }
        File result = uncheckedFile(owner, networkId);
        return isSafePath(result) ? result : null;
    }

    /** Object used by the service to serialize read-modify-write operations. */
    Object lockFor(String ownerUuid, int networkId) {
        File file = fileFor(ownerUuid, networkId);
        String key = file == null ? String.valueOf(rootDirectory) : file.getAbsolutePath();
        Object existing = LOCKS.get(key);
        if (existing != null) {
            return existing;
        }
        Object created = new Object();
        Object raced = LOCKS.putIfAbsent(key, created);
        return raced == null ? created : raced;
    }

    /** Reads one owner/network file.  A missing file is a successful empty list. */
    public ReadResult load(String ownerUuid, int networkId) {
        String owner = WorldMapPacketAuthorization.canonicalOwnerUuid(ownerUuid);
        if (owner == null || !WorldMapPacketAuthorization.isValidNetworkId(networkId) || rootDirectory == null) {
            return ReadResult.failure("invalid_scope", "ownerUuid or networkId is invalid");
        }
        File file = uncheckedFile(owner, networkId);
        if (!isSafePath(file)) {
            return ReadResult.failure("unsafe_path", "annotation path is outside the configured root");
        }
        if (!file.exists()) {
            return ReadResult.success(Collections.<WorldMapAnnotationDto>emptyList());
        }
        if (!file.isFile() || Files.isSymbolicLink(file.toPath())) {
            return ReadResult.failure("unsafe_path", "annotation file is not a regular non-symlink file");
        }
        try {
            byte[] bytes = readLimited(file, MAX_FILE_BYTES);
            if (bytes == null) {
                return ReadResult.failure("oversize_file", "annotation file exceeds the size limit");
            }
            List<WorldMapAnnotationDto> records = parse(bytes, owner, networkId);
            return ReadResult.success(records);
        } catch (Exception e) {
            return ReadResult.failure("corrupt_file", "annotation file is corrupt or invalid");
        }
    }

    /** Writes one complete owner/network list using temp-file plus replace. */
    public WriteResult save(String ownerUuid, int networkId, List<WorldMapAnnotationDto> records) {
        String owner = WorldMapPacketAuthorization.canonicalOwnerUuid(ownerUuid);
        if (owner == null || !WorldMapPacketAuthorization.isValidNetworkId(networkId) || rootDirectory == null) {
            return WriteResult.failure("invalid_scope", "ownerUuid or networkId is invalid");
        }
        File file = uncheckedFile(owner, networkId);
        if (!isSafePath(file)) {
            return WriteResult.failure("unsafe_path", "annotation path is unavailable or unsafe");
        }
        if (records == null || records.size() > MAX_RECORDS) {
            return WriteResult.failure("record_limit", "annotation record limit exceeded");
        }
        List<WorldMapAnnotationDto> copy = new ArrayList<WorldMapAnnotationDto>(records.size());
        Set<String> ids = new HashSet<String>();
        for (WorldMapAnnotationDto record : records) {
            if (!isValidRecord(record, owner, networkId) || !ids.add(record.id)) {
                return WriteResult.failure("invalid_record", "annotation record is invalid or duplicated");
            }
            copy.add(record.copy());
        }
        byte[] bytes = GSON.toJson(copy).getBytes(UTF8);
        if (bytes.length > MAX_FILE_BYTES) {
            return WriteResult.failure("oversize_file", "serialized annotation file exceeds the size limit");
        }
        if (!ensureDirectory(rootDirectory) || !ensureDirectory(file.getParentFile()) || !isSafePath(file)) {
            return WriteResult.failure("unsafe_path", "annotation path is unavailable or unsafe");
        }
        if (file.exists() && (!file.isFile() || Files.isSymbolicLink(file.toPath()))) {
            return WriteResult.failure("unsafe_path", "annotation target is not a regular non-symlink file");
        }
        try {
            writer.write(file, bytes);
            return WriteResult.success();
        } catch (IOException e) {
            return WriteResult.failure("write_failed", "annotation file could not be written");
        } catch (RuntimeException e) {
            return WriteResult.failure("write_failed", "annotation file could not be written");
        }
    }

    private static List<WorldMapAnnotationDto> parse(byte[] bytes, String owner, int networkId) throws IOException {
        String json = new String(bytes, UTF8);
        JsonReader reader = new JsonReader(new java.io.StringReader(json));
        reader.setLenient(false);
        JsonElement root = new JsonParser().parse(reader);
        if (reader.peek() != JsonToken.END_DOCUMENT) {
            throw new JsonParseException("trailing JSON");
        }
        JsonArray array;
        if (root != null && root.isJsonArray()) {
            array = root.getAsJsonArray();
        } else if (root != null && root.isJsonObject()) {
            JsonObject object = root.getAsJsonObject();
            JsonElement annotations = object.get("annotations");
            if (annotations == null || !annotations.isJsonArray()) {
                throw new JsonParseException("annotation root must be an array");
            }
            array = annotations.getAsJsonArray();
        } else {
            throw new JsonParseException("annotation root must be an array");
        }
        if (array.size() > MAX_RECORDS) {
            throw new JsonParseException("too many annotations");
        }
        List<WorldMapAnnotationDto> records = new ArrayList<WorldMapAnnotationDto>(array.size());
        Set<String> ids = new HashSet<String>();
        for (JsonElement element : array) {
            if (element == null || !element.isJsonObject()) {
                throw new JsonParseException("annotation record must be an object");
            }
            WorldMapAnnotationDto record = GSON.fromJson(element, WorldMapAnnotationDto.class);
            if (!isValidRecord(record, owner, networkId) || !ids.add(record.id)) {
                throw new JsonParseException("invalid annotation record");
            }
            record.label = record.label.trim();
            record.note = record.note == null ? "" : record.note;
            record.color = record.color.toUpperCase(java.util.Locale.ROOT);
            records.add(record);
        }
        return records;
    }

    static boolean isValidRecord(WorldMapAnnotationDto record, String owner, int networkId) {
        if (record == null || owner == null || !owner.equals(record.ownerUuid) || record.networkId != networkId) {
            return false;
        }
        if (!isCanonicalUuid(record.id) || !isSafeText(record.createdBy, MAX_CREATED_BY_CODE_POINTS, true)) {
            return false;
        }
        if (record.createdAt <= 0L || record.updatedAt < record.createdAt) {
            return false;
        }
        if (!WorldMapAnnotationService.isValidContent(record.label, record.note, record.color,
            record.fromVersion, record.toVersion, record.dimension, record.x, record.y, record.z)) {
            return false;
        }
        return true;
    }

    static boolean isCanonicalUuid(String value) {
        if (value == null || value.length() != 36) {
            return false;
        }
        try {
            return UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    static boolean isSafeText(String value, int maxCodePoints, boolean required) {
        if (value == null) {
            return !required;
        }
        if (required && !hasNonWhitespace(value)) {
            return false;
        }
        if (value.codePointCount(0, value.length()) > maxCodePoints) {
            return false;
        }
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            if (Character.isISOControl(codePoint) || Character.getType(codePoint) == Character.SURROGATE) {
                return false;
            }
            offset += Character.charCount(codePoint);
        }
        return true;
    }

    private static boolean hasNonWhitespace(String value) {
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            if (!Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint)) {
                return true;
            }
            offset += Character.charCount(codePoint);
        }
        return false;
    }

    private static byte[] readLimited(File file, int maxBytes) throws IOException {
        long length = file.length();
        if (length <= 0L || length > maxBytes) {
            return null;
        }
        FileInputStream input = new FileInputStream(file);
        ByteArrayOutputStream output = new ByteArrayOutputStream((int) length);
        try {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }
                total += read;
                if (total > maxBytes) {
                    return null;
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } finally {
            input.close();
        }
    }

    private boolean ensureDirectory(File directory) {
        if (directory == null || !isSafePath(directory)) {
            return false;
        }
        if (directory.exists()) {
            return directory.isDirectory() && !Files.isSymbolicLink(directory.toPath());
        }
        Path root = rootDirectory.getAbsoluteFile().toPath().normalize();
        Path path = directory.getAbsoluteFile().toPath().normalize();
        if (!path.equals(root)) {
            File parent = directory.getParentFile();
            if (parent != null && !ensureDirectory(parent)) {
                return false;
            }
        } else {
            File parent = directory.getParentFile();
            if (parent == null || !parent.isDirectory() || Files.isSymbolicLink(parent.toPath())) {
                return false;
            }
        }
        return directory.mkdir() || (directory.isDirectory() && !Files.isSymbolicLink(directory.toPath()));
    }

    private File uncheckedFile(String canonicalOwner, int networkId) {
        File ownerDirectory = new File(rootDirectory, canonicalOwner);
        return new File(ownerDirectory, String.valueOf(networkId) + FILE_SUFFIX);
    }

    private boolean isSafePath(File candidate) {
        if (rootDirectory == null || candidate == null) {
            return false;
        }
        try {
            Path root = rootDirectory.getAbsoluteFile().toPath().normalize();
            Path path = candidate.getAbsoluteFile().toPath().normalize();
            if (!path.equals(root) && !path.startsWith(root)) {
                return false;
            }
            if (Files.exists(root) && Files.isSymbolicLink(root)) {
                return false;
            }
            Path current = root;
            Path relative = root.relativize(path);
            for (Path part : relative) {
                current = current.resolve(part);
                if (Files.exists(current) && Files.isSymbolicLink(current)) {
                    return false;
                }
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static void writeAtomically(File target, byte[] bytes) throws IOException {
        if (target == null || bytes == null) {
            throw new IOException("missing annotation target");
        }
        File parent = target.getParentFile();
        if (parent == null || !parent.isDirectory() || Files.isSymbolicLink(parent.toPath())
            || (target.exists() && Files.isSymbolicLink(target.toPath()))) {
            throw new IOException("unsafe annotation target");
        }
        File temporary = File.createTempFile("annotation-", ".tmp", parent);
        FileOutputStream output = null;
        try {
            output = new FileOutputStream(temporary);
            output.write(bytes);
            output.flush();
            output.getFD().sync();
            output.close();
            output = null;
            try {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            temporary = null;
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (IOException ignored) {}
            }
            if (temporary != null && temporary.exists()) {
                temporary.delete();
            }
        }
    }

    /** Functional seam used by tests to force an atomic-write failure. */
    interface AtomicWriteStrategy {
        void write(File target, byte[] bytes) throws IOException;
    }

    public static final class ReadResult {
        public final boolean success;
        public final String code;
        public final String message;
        public final List<WorldMapAnnotationDto> records;

        private ReadResult(boolean success, String code, String message, List<WorldMapAnnotationDto> records) {
            this.success = success;
            this.code = code;
            this.message = message;
            this.records = records;
        }

        static ReadResult success(List<WorldMapAnnotationDto> records) {
            return new ReadResult(true, "ok", "ok", records);
        }

        static ReadResult failure(String code, String message) {
            return new ReadResult(false, code, message, Collections.<WorldMapAnnotationDto>emptyList());
        }
    }

    public static final class WriteResult {
        public final boolean success;
        public final String code;
        public final String message;

        private WriteResult(boolean success, String code, String message) {
            this.success = success;
            this.code = code;
            this.message = message;
        }

        static WriteResult success() {
            return new WriteResult(true, "ok", "ok");
        }

        static WriteResult failure(String code, String message) {
            return new WriteResult(false, code, message);
        }
    }
}
