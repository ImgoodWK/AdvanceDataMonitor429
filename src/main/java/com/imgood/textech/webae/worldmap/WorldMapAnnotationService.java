package com.imgood.textech.webae.worldmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Pure server-side CRUD service for world-map annotations.
 *
 * <p>This class intentionally knows nothing about HTTP, sessions or ACLs.  A
 * future handler supplies an already-authenticated actor and maps the explicit
 * result code/message to its wire response.</p>
 */
public final class WorldMapAnnotationService {

    public static final int MAX_LABEL_CODE_POINTS = 64;
    public static final int MAX_NOTE_CODE_POINTS = 512;
    public static final int MIN_Y = 0;
    public static final int MAX_Y = 255;

    private final WorldMapAnnotationStore store;
    private final TimeSource clock;

    public WorldMapAnnotationService() {
        this(new WorldMapAnnotationStore());
    }

    public WorldMapAnnotationService(WorldMapAnnotationStore store) {
        this(store, new TimeSource() {
            @Override
            public long now() {
                return System.currentTimeMillis();
            }
        });
    }

    /** Constructor seam for deterministic timestamp tests. */
    public WorldMapAnnotationService(WorldMapAnnotationStore store, TimeSource clock) {
        this.store = store == null ? new WorldMapAnnotationStore() : store;
        this.clock = clock == null ? new TimeSource() {
            @Override
            public long now() {
                return System.currentTimeMillis();
            }
        } : clock;
    }

    public WorldMapAnnotationStore store() {
        return store;
    }

    /** Lists annotations visible at a valid positive snapshot version. */
    public WorldMapAnnotationResult<List<WorldMapAnnotationDto>> list(String ownerUuid, int networkId, int version) {
        String owner = canonicalOwner(ownerUuid);
        if (owner == null) {
            return failure("invalid_owner", "ownerUuid is invalid");
        }
        if (!WorldMapPacketAuthorization.isValidNetworkId(networkId)) {
            return failure("invalid_network", "networkId is invalid");
        }
        if (!WorldMapPacketAuthorization.isValidSnapshotVersion(version)) {
            return failure("invalid_version", "snapshot version is invalid");
        }
        synchronized (store.lockFor(owner, networkId)) {
            WorldMapAnnotationStore.ReadResult loaded = store.load(owner, networkId);
            if (!loaded.success) {
                return failure(loaded.code, loaded.message);
            }
            List<WorldMapAnnotationDto> visible = new ArrayList<WorldMapAnnotationDto>();
            for (WorldMapAnnotationDto annotation : loaded.records) {
                if (isVisibleAt(annotation, version)) {
                    visible.add(annotation.copy());
                }
            }
            return WorldMapAnnotationResult.success(Collections.unmodifiableList(visible));
        }
    }

    /** Convenience alias for adapters that use an explicit method name. */
    public WorldMapAnnotationResult<List<WorldMapAnnotationDto>> listAnnotations(String ownerUuid, int networkId,
        int version) {
        return list(ownerUuid, networkId, version);
    }

    public WorldMapAnnotationResult<WorldMapAnnotationDto> create(String ownerUuid, int networkId, String actor,
        WorldMapAnnotationRequest request) {
        String owner = canonicalOwner(ownerUuid);
        WorldMapAnnotationResult<Void> scope = validateScope(owner, networkId, actor);
        if (!scope.success) {
            return failure(scope.code, scope.message);
        }
        if (request == null) {
            return failure("invalid_request", "annotation request is missing");
        }
        WorldMapAnnotationResult<Void> metadata = validateCreateMetadata(owner, networkId, request);
        if (!metadata.success) {
            return failure(metadata.code, metadata.message);
        }
        NormalizedContent content = normalizeContent(request);
        if (!content.valid) {
            return failure(content.code, content.message);
        }
        String createdBy = normalizeActor(actor);
        if (!isSafeActor(createdBy)) {
            return failure("invalid_actor", "actor is missing or contains invalid characters");
        }
        synchronized (store.lockFor(owner, networkId)) {
            WorldMapAnnotationStore.ReadResult loaded = store.load(owner, networkId);
            if (!loaded.success) {
                return failure(loaded.code, loaded.message);
            }
            if (loaded.records.size() >= WorldMapAnnotationStore.MAX_RECORDS) {
                return failure("record_limit", "annotation record limit exceeded");
            }
            WorldMapAnnotationDto annotation = new WorldMapAnnotationDto();
            annotation.id = allocateId(loaded.records);
            annotation.ownerUuid = owner;
            annotation.networkId = networkId;
            annotation.dimension = request.dimension;
            annotation.x = request.x;
            annotation.y = request.y;
            annotation.z = request.z;
            annotation.label = content.label;
            annotation.note = content.note;
            annotation.color = content.color;
            annotation.fromVersion = request.fromVersion;
            annotation.toVersion = request.toVersion;
            long now = positiveNow(0L);
            annotation.createdAt = now;
            annotation.updatedAt = now;
            annotation.createdBy = createdBy;

            List<WorldMapAnnotationDto> records = copyRecords(loaded.records);
            records.add(annotation);
            WorldMapAnnotationStore.WriteResult written = store.save(owner, networkId, records);
            if (!written.success) {
                return failure(written.code, written.message);
            }
            return WorldMapAnnotationResult.success(annotation.copy());
        }
    }

    public WorldMapAnnotationResult<WorldMapAnnotationDto> create(String ownerUuid, int networkId, String actor,
        WorldMapAnnotationDto request) {
        return create(ownerUuid, networkId, actor, WorldMapAnnotationRequest.fromDto(request));
    }

    public WorldMapAnnotationResult<WorldMapAnnotationDto> update(String ownerUuid, int networkId, String id,
        String actor, WorldMapAnnotationRequest request) {
        String owner = canonicalOwner(ownerUuid);
        WorldMapAnnotationResult<Void> scope = validateScope(owner, networkId, actor);
        if (!scope.success) {
            return failure(scope.code, scope.message);
        }
        if (!WorldMapAnnotationStore.isCanonicalUuid(id)) {
            return failure("invalid_id", "annotation id is invalid");
        }
        if (request == null) {
            return failure("invalid_request", "annotation request is missing");
        }
        if (!validateUpdateMetadata(owner, networkId, id, request)) {
            return failure("immutable_field", "annotation identity and server metadata are immutable");
        }
        NormalizedContent content = normalizeContent(request);
        if (!content.valid) {
            return failure(content.code, content.message);
        }
        synchronized (store.lockFor(owner, networkId)) {
            WorldMapAnnotationStore.ReadResult loaded = store.load(owner, networkId);
            if (!loaded.success) {
                return failure(loaded.code, loaded.message);
            }
            WorldMapAnnotationDto found = null;
            for (WorldMapAnnotationDto annotation : loaded.records) {
                if (id.equals(annotation.id)) {
                    found = annotation;
                    break;
                }
            }
            if (found == null) {
                return failure("not_found", "annotation was not found");
            }
            if (!matchesStoredMetadata(found, request)) {
                return failure("immutable_field", "annotation identity and server metadata are immutable");
            }
            WorldMapAnnotationDto updated = found.copy();
            updated.dimension = request.dimension;
            updated.x = request.x;
            updated.y = request.y;
            updated.z = request.z;
            updated.label = content.label;
            updated.note = content.note;
            updated.color = content.color;
            updated.fromVersion = request.fromVersion;
            updated.toVersion = request.toVersion;
            updated.updatedAt = positiveNow(found.updatedAt);

            List<WorldMapAnnotationDto> records = copyRecords(loaded.records);
            for (int index = 0; index < records.size(); index++) {
                if (id.equals(records.get(index).id)) {
                    records.set(index, updated);
                    break;
                }
            }
            WorldMapAnnotationStore.WriteResult written = store.save(owner, networkId, records);
            if (!written.success) {
                return failure(written.code, written.message);
            }
            return WorldMapAnnotationResult.success(updated.copy());
        }
    }

    public WorldMapAnnotationResult<WorldMapAnnotationDto> update(String ownerUuid, int networkId, String id,
        String actor, WorldMapAnnotationDto request) {
        return update(ownerUuid, networkId, id, actor, WorldMapAnnotationRequest.fromDto(request));
    }

    public WorldMapAnnotationResult<WorldMapAnnotationDto> delete(String ownerUuid, int networkId, String id) {
        String owner = canonicalOwner(ownerUuid);
        if (owner == null) {
            return failure("invalid_owner", "ownerUuid is invalid");
        }
        if (!WorldMapPacketAuthorization.isValidNetworkId(networkId)) {
            return failure("invalid_network", "networkId is invalid");
        }
        if (!WorldMapAnnotationStore.isCanonicalUuid(id)) {
            return failure("invalid_id", "annotation id is invalid");
        }
        synchronized (store.lockFor(owner, networkId)) {
            WorldMapAnnotationStore.ReadResult loaded = store.load(owner, networkId);
            if (!loaded.success) {
                return failure(loaded.code, loaded.message);
            }
            WorldMapAnnotationDto removed = null;
            List<WorldMapAnnotationDto> records = copyRecords(loaded.records);
            for (int index = 0; index < records.size(); index++) {
                if (id.equals(records.get(index).id)) {
                    removed = records.remove(index);
                    break;
                }
            }
            if (removed == null) {
                return failure("not_found", "annotation was not found");
            }
            WorldMapAnnotationStore.WriteResult written = store.save(owner, networkId, records);
            if (!written.success) {
                return failure(written.code, written.message);
            }
            return WorldMapAnnotationResult.success(removed.copy());
        }
    }

    private static String canonicalOwner(String ownerUuid) {
        return WorldMapPacketAuthorization.canonicalOwnerUuid(ownerUuid);
    }

    private static WorldMapAnnotationResult<Void> validateScope(String owner, int networkId, String actor) {
        if (owner == null) {
            return failure("invalid_owner", "ownerUuid is invalid");
        }
        if (!WorldMapPacketAuthorization.isValidNetworkId(networkId)) {
            return failure("invalid_network", "networkId is invalid");
        }
        if (!isSafeActor(normalizeActor(actor))) {
            return failure("invalid_actor", "actor is missing or contains invalid characters");
        }
        return WorldMapAnnotationResult.success(null);
    }

    private static WorldMapAnnotationResult<Void> validateCreateMetadata(String owner, int networkId,
        WorldMapAnnotationRequest request) {
        if (request.id != null || request.createdAt != null || request.updatedAt != null || request.createdBy != null) {
            return failure("server_field", "id and server metadata are assigned by the service");
        }
        if (request.ownerUuid != null && !owner.equals(request.ownerUuid)) {
            return failure("cross_owner", "request ownerUuid does not match the scoped owner");
        }
        if (request.networkId != null && request.networkId.intValue() != networkId) {
            return failure("cross_network", "request networkId does not match the scoped network");
        }
        return WorldMapAnnotationResult.success(null);
    }

    private static boolean validateUpdateMetadata(String owner, int networkId, String id,
        WorldMapAnnotationRequest request) {
        if (request.id != null && !id.equals(request.id)) {
            return false;
        }
        if (request.ownerUuid != null && !owner.equals(request.ownerUuid)) {
            return false;
        }
        if (request.networkId != null && request.networkId.intValue() != networkId) {
            return false;
        }
        if (request.createdAt != null && request.createdAt.longValue() <= 0L) {
            return false;
        }
        if (request.updatedAt != null && request.updatedAt.longValue() <= 0L) {
            return false;
        }
        if (request.createdBy != null && !isSafeActor(request.createdBy)) {
            return false;
        }
        return true;
    }

    private static boolean matchesStoredMetadata(WorldMapAnnotationDto stored, WorldMapAnnotationRequest request) {
        if (request.createdAt != null && request.createdAt.longValue() != stored.createdAt) {
            return false;
        }
        if (request.updatedAt != null && request.updatedAt.longValue() != stored.updatedAt) {
            return false;
        }
        return request.createdBy == null || request.createdBy.equals(stored.createdBy);
    }

    private static NormalizedContent normalizeContent(WorldMapAnnotationRequest request) {
        if (!isValidContent(request.label, request.note, request.color, request.fromVersion, request.toVersion,
            request.dimension, request.x, request.y, request.z)) {
            return NormalizedContent.invalid("invalid_request", "annotation content is invalid");
        }
        String label = request.label.trim();
        String note = request.note == null ? "" : request.note;
        String color = request.color.toUpperCase(Locale.ROOT);
        return NormalizedContent.valid(label, note, color);
    }

    /** Shared record validator used by the store while loading untrusted JSON. */
    static boolean isValidContent(String label, String note, String color, int fromVersion, int toVersion,
        int dimension, int x, int y, int z) {
        if (!WorldMapAnnotationStore.isSafeText(label, MAX_LABEL_CODE_POINTS, true)
            || !WorldMapAnnotationStore.isSafeText(note, MAX_NOTE_CODE_POINTS, false)) {
            return false;
        }
        if (color == null || !color.matches("#[0-9a-fA-F]{6}")) {
            return false;
        }
        if (!isValidVersionRange(fromVersion, toVersion)) {
            return false;
        }
        if (y < MIN_Y || y > MAX_Y) {
            return false;
        }
        int chunkX = Math.floorDiv(x, 16);
        int chunkZ = Math.floorDiv(z, 16);
        return WorldMapPacketAuthorization.isValidChunk(dimension, chunkX, chunkZ);
    }

    public static boolean isValidVersionRange(int fromVersion, int toVersion) {
        if (fromVersion < 0 || toVersion < 0) {
            return false;
        }
        if (fromVersion > WorldMapPacketAuthorization.MAX_SNAPSHOT_VERSION
            || toVersion > WorldMapPacketAuthorization.MAX_SNAPSHOT_VERSION) {
            return false;
        }
        return fromVersion == 0 || toVersion == 0 || fromVersion <= toVersion;
    }

    public static boolean isVisibleAt(WorldMapAnnotationDto annotation, int version) {
        if (annotation == null || version <= 0) {
            return false;
        }
        return (annotation.fromVersion == 0 || version >= annotation.fromVersion)
            && (annotation.toVersion == 0 || version <= annotation.toVersion);
    }

    private String allocateId(List<WorldMapAnnotationDto> records) {
        for (int attempt = 0; attempt < 10; attempt++) {
            String id = UUID.randomUUID().toString();
            boolean used = false;
            for (WorldMapAnnotationDto record : records) {
                if (record != null && id.equals(record.id)) {
                    used = true;
                    break;
                }
            }
            if (!used) {
                return id;
            }
        }
        throw new IllegalStateException("could not allocate unique annotation id");
    }

    private long positiveNow(long previous) {
        long now = clock.now();
        if (now <= previous) {
            if (previous == Long.MAX_VALUE) {
                return Long.MAX_VALUE;
            }
            return previous + 1L;
        }
        return now;
    }

    private static String normalizeActor(String actor) {
        return actor == null ? null : actor.trim();
    }

    private static boolean isSafeActor(String actor) {
        return WorldMapAnnotationStore.isSafeText(actor, WorldMapAnnotationStore.MAX_CREATED_BY_CODE_POINTS, true);
    }

    private static List<WorldMapAnnotationDto> copyRecords(List<WorldMapAnnotationDto> records) {
        List<WorldMapAnnotationDto> copy = new ArrayList<WorldMapAnnotationDto>(records.size());
        for (WorldMapAnnotationDto record : records) {
            copy.add(record.copy());
        }
        return copy;
    }

    private static <T> WorldMapAnnotationResult<T> failure(String code, String message) {
        return WorldMapAnnotationResult.failure(code, message);
    }

    private static final class NormalizedContent {
        private final boolean valid;
        private final String code;
        private final String message;
        private final String label;
        private final String note;
        private final String color;

        private NormalizedContent(boolean valid, String code, String message, String label, String note, String color) {
            this.valid = valid;
            this.code = code;
            this.message = message;
            this.label = label;
            this.note = note;
            this.color = color;
        }

        static NormalizedContent invalid(String code, String message) {
            return new NormalizedContent(false, code, message, null, null, null);
        }

        static NormalizedContent valid(String label, String note, String color) {
            return new NormalizedContent(true, "ok", "ok", label, note, color);
        }
    }

    /** Small Java 8-compatible time seam. */
    public interface TimeSource {
        long now();
    }
}
