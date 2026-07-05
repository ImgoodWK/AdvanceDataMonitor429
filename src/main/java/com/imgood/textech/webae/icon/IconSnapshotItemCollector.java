package com.imgood.textech.webae.icon;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.imgood.textech.webae.cache.SnapshotCache;
import com.imgood.textech.webae.dto.StorageDto;

/**
 * Collects canonical icon cache keys from cached AE storage snapshots (server-side).
 */
public final class IconSnapshotItemCollector {

    private IconSnapshotItemCollector() {}

    public static List<String> collectItemIds() {
        Set<String> ids = new LinkedHashSet<String>();
        SnapshotCache.instance()
            .forEachStorageSnapshot(new SnapshotCache.StorageSnapshotConsumer() {

                @Override
                public void accept(StorageDto dto) {
                    if (dto == null) return;
                    if (dto.items != null) {
                        for (StorageDto.ItemEntry item : dto.items) {
                            if (item == null) continue;
                            if (item.itemId != null && !item.itemId.isEmpty()) {
                                ids.add(item.itemId);
                            } else if (item.registryName != null && !item.registryName.isEmpty()) {
                                ids.add(IconItemId.build(item.registryName, item.meta));
                            }
                        }
                    }
                    if (dto.fluids != null) {
                        for (StorageDto.FluidEntry fluid : dto.fluids) {
                            if (fluid == null || fluid.fluidName == null || fluid.fluidName.isEmpty()) continue;
                            ids.add(IconItemId.FLUID_PREFIX + fluid.fluidName);
                        }
                    }
                }
            });
        return new ArrayList<String>(ids);
    }
}
