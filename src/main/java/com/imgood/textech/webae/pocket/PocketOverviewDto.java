package com.imgood.textech.webae.pocket;

/**
 * OP-only read-only dimensional pocket overview (no item contents — privacy).
 */
public final class PocketOverviewDto {

    public boolean available;
    public boolean opRequired;
    public int pageCount;
    public int slotsPerPage;
    public int spaceUpgrades;
    public int pageUpgrades;
    public int stackUpgrades;
    public boolean infiniteStackUpgrade;
    public boolean enabled;
    public int occupiedSlots;
    public int totalSlots;
    public String message = "";
}
