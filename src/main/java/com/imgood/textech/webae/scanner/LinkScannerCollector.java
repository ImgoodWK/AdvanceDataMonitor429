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
            for (Object obj : world.loadedTileEntityList) {
                if (!(obj instanceof TileEntity)) {
                    continue;
                }
                TileEntity tile = (TileEntity) obj;
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
