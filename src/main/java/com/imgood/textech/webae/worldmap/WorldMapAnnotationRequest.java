package com.imgood.textech.webae.worldmap;

/**
 * Mutable fields accepted by {@link WorldMapAnnotationService}.
 *
 * <p>The nullable metadata fields are optional assertions useful to an HTTP
 * adapter that round-trips a DTO.  They are never used to assign server-owned
 * values.  If supplied on update, immutable assertions must match the stored
 * record. A supplied {@code updatedAt} is therefore treated only as an
 * optimistic assertion against the stored server-generated value.</p>
 */
public class WorldMapAnnotationRequest {

    public int dimension;
    public int x;
    public int y;
    public int z;
    public String label;
    public String note;
    public String color;
    public int fromVersion;
    public int toVersion;

    /** Optional immutable-field assertions. */
    public String id;
    public String ownerUuid;
    public Integer networkId;
    public Long createdAt;
    public Long updatedAt;
    public String createdBy;

    public WorldMapAnnotationRequest() {}

    public static WorldMapAnnotationRequest fromDto(WorldMapAnnotationDto dto) {
        if (dto == null) {
            return null;
        }
        WorldMapAnnotationRequest request = new WorldMapAnnotationRequest();
        request.dimension = dto.dimension;
        request.x = dto.x;
        request.y = dto.y;
        request.z = dto.z;
        request.label = dto.label;
        request.note = dto.note;
        request.color = dto.color;
        request.fromVersion = dto.fromVersion;
        request.toVersion = dto.toVersion;
        request.id = dto.id;
        request.ownerUuid = dto.ownerUuid;
        // Zero is the DTO's Java default and is not enough to assert a
        // non-zero network/timestamp.  Non-zero values are retained for an
        // update round-trip; the service still owns the actual fields.
        request.networkId = dto.networkId == 0 ? null : Integer.valueOf(dto.networkId);
        request.createdAt = dto.createdAt <= 0L ? null : Long.valueOf(dto.createdAt);
        request.updatedAt = dto.updatedAt <= 0L ? null : Long.valueOf(dto.updatedAt);
        request.createdBy = dto.createdBy;
        return request;
    }
}
