package com.imgood.textech.webae.worldmap;

/**
 * A user-owned annotation shown on a world-map snapshot.
 *
 * <p>
 * The DTO is deliberately a simple mutable data object. The annotation
 * service is the authority for assigning ids and timestamps; callers should
 * not construct those fields for a create request.
 * </p>
 */
public class WorldMapAnnotationDto {

    public String id;
    public String ownerUuid;
    public int networkId;
    public int dimension;
    public int x;
    public int y;
    public int z;
    public String label;
    public String note;
    public String color;
    /** Zero means no lower bound. */
    public int fromVersion;
    /** Zero means no upper bound. */
    public int toVersion;
    public long createdAt;
    public long updatedAt;
    public String createdBy;

    public WorldMapAnnotationDto() {}

    public WorldMapAnnotationDto copy() {
        WorldMapAnnotationDto copy = new WorldMapAnnotationDto();
        copy.id = id;
        copy.ownerUuid = ownerUuid;
        copy.networkId = networkId;
        copy.dimension = dimension;
        copy.x = x;
        copy.y = y;
        copy.z = z;
        copy.label = label;
        copy.note = note;
        copy.color = color;
        copy.fromVersion = fromVersion;
        copy.toVersion = toVersion;
        copy.createdAt = createdAt;
        copy.updatedAt = updatedAt;
        copy.createdBy = createdBy;
        return copy;
    }
}
