package com.imgood.textech.webae.scanner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;

import com.imgood.textech.items.LinkScanBlockType;
import com.imgood.textech.tileentity.IOwnableTile;
import com.imgood.textech.webae.context.WebAeOwnerContext;

/**
 * Enumerates Link Scanner compatible blocks in loaded chunks (read-only mirror of in-game scanner).
 */
public final class LinkScannerCollector {

    private LinkScannerCollector() {}

    public static List<LinkScannerBlockDto> collect(String ownerUuid, String typeFilter, String query) {
        String ownerName = WebAeOwnerContext.resolveOwnerName(ownerUuid);
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            return Collections.emptyList();
        }

        LinkScanBlockType typeEnum = typeFilter != null && !typeFilter.isEmpty()
            ? LinkScanBlockType.fromId(typeFilter.trim())
            : null;
        String q = query != null ? query.trim()
            .toLowerCase() : "";

        List<LinkScannerBlockDto> found = new ArrayList<LinkScannerBlockDto>();
        for (int d = 0; d < server.worldServers.length; d++) {
            WorldServer world = server.worldServers[d];
            if (world == null) {
                continue;
            }
            int dimension = world.provider.dimensionId;
            // Use the TE index to scan this dimension's tile entities.
            List<TileEntity> tiles = com.imgood.textech.webae.context.TileEntityIndex.getAllInDimension(dimension);
            for (TileEntity tile : tiles) {
                LinkScanBlockType type = LinkScanBlockType.fromTileEntity(tile);
                if (type == null) {
                    continue;
                }
                if (typeEnum != null && typeEnum != type) {
                    continue;
                }
                String teOwner = "";
                if (tile instanceof IOwnableTile) {
                    teOwner = ((IOwnableTile) tile).getOwnerName();
                    if (teOwner == null) {
                        teOwner = "";
                    }
                }
                if (!ownerName.isEmpty() && !ownerName.equals(teOwner)) {
                    continue;
                }
                LinkScannerBlockDto dto = new LinkScannerBlockDto();
                dto.dimension = dimension;
                dto.x = tile.xCoord;
                dto.y = tile.yCoord;
                dto.z = tile.zCoord;
                dto.blockTypeId = type.getId();
                dto.blockTypeLabelKey = type.getLangKey();
                dto.owner = teOwner;
                dto.alias = "";
                dto.locationKey = dimension + ":" + dto.x + ":" + dto.y + ":" + dto.z;
                if (!q.isEmpty() && !matchesQuery(dto, q)) {
                    continue;
                }
                found.add(dto);
            }
        }
        return found;
    }

    /**
     * Filter a previously collected full list on any thread (HTTP-safe).
     */
    public static List<LinkScannerBlockDto> filterCached(List<LinkScannerBlockDto> base, String typeFilter,
        String query) {
        if (base == null || base.isEmpty()) {
            return Collections.emptyList();
        }
        LinkScanBlockType typeEnum = typeFilter != null && !typeFilter.isEmpty()
            ? LinkScanBlockType.fromId(typeFilter.trim())
            : null;
        String q = query != null ? query.trim()
            .toLowerCase() : "";
        if (typeEnum == null && q.isEmpty()) {
            return base;
        }
        List<LinkScannerBlockDto> out = new ArrayList<LinkScannerBlockDto>();
        for (LinkScannerBlockDto dto : base) {
            if (typeEnum != null && !typeEnum.getId()
                .equals(dto.blockTypeId)) {
                continue;
            }
            if (!q.isEmpty() && !matchesQuery(dto, q)) {
                continue;
            }
            out.add(dto);
        }
        return out;
    }

    private static boolean matchesQuery(LinkScannerBlockDto dto, String q) {
        if (dto.locationKey.contains(q)) {
            return true;
        }
        if (dto.blockTypeId.contains(q)) {
            return true;
        }
        if (dto.owner != null && dto.owner.toLowerCase()
            .contains(q)) {
            return true;
        }
        if (dto.alias != null && dto.alias.toLowerCase()
            .contains(q)) {
            return true;
        }
        return String.valueOf(dto.x)
            .contains(q)
            || String.valueOf(dto.y)
                .contains(q)
            || String.valueOf(dto.z)
                .contains(q);
    }
}
