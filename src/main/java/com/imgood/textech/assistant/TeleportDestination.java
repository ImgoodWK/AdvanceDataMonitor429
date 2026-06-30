package com.imgood.textech.assistant;

import net.minecraft.item.ItemStack;

/**
 * Represents a teleport destination read from a Draconic Evolution Advanced Dislocator.
 */
public class TeleportDestination {

    public final int index;
    public final String name;
    public final int dimensionId;
    public final String dimensionName;
    public final int x;
    public final int y;
    public final int z;
    public final ItemStack sourceItem;

    public TeleportDestination(int index, String name, int dimensionId, String dimensionName, int x, int y, int z,
        ItemStack sourceItem) {
        this.index = index;
        this.name = name == null ? "" : name;
        this.dimensionId = dimensionId;
        this.dimensionName = dimensionName == null ? "" : dimensionName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.sourceItem = sourceItem;
    }

    public String formatEntry(String locale) {
        boolean zh = locale == null || locale.trim()
            .isEmpty()
            || locale.toLowerCase()
                .startsWith("zh");
        StringBuilder sb = new StringBuilder();
        sb.append(name);
        sb.append(" [");
        sb.append(dimensionName.isEmpty() ? String.valueOf(dimensionId) : dimensionName);
        sb.append("] ");
        sb.append(x)
            .append(", ")
            .append(y)
            .append(", ")
            .append(z);
        return sb.toString();
    }

    @Override
    public String toString() {
        return name + " [dim=" + dimensionId + "] " + x + "," + y + "," + z;
    }
}
