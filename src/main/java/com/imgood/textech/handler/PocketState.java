package com.imgood.textech.handler;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagShort;

/**
 * Per-player Dimensional Pocket state. All state is bound to the player UUID,
 * not to any specific item instance â€?every ItemDimensionalPocket in the player's
 * inventory shares the same PocketState.
 *
 * Layout: pages = 1 + pageUpgrades (max 9), slotsPerPage = 1 + spaceUpgrades (max 63).
 * Space upgrades: 64 max, but only 62 are effective (1 free slot baseline).
 * Page upgrades: 8 max, require space upgrades to be fully stacked (64) to take effect.
 *
 * Stack upgrades: 8 max, each doubles the per-slot stack limit (2^n).
 * Infinite stack upgrade: 1 max, sets per-slot limit to Integer.MAX_VALUE.
 */
public class PocketState {

    public static final int MAX_SPACE_UPGRADES = 64;
    public static final int MAX_PAGE_UPGRADES = 8;
    public static final int MAX_STACK_UPGRADES = 8;
    public static final int BASE_SLOTS_PER_PAGE = 1;
    public static final int SLOTS_PER_PAGE_CAP = 63; // 7 rows x 9 cols
    public static final int BASE_PAGES = 1;
    public static final int PAGES_CAP = 9; // 1 + 8 page upgrades

    private ItemStack[][] pages; // [page][slot]
    private int spaceUpgrades;
    private int pageUpgrades;
    private int stackUpgrades; // 0..8, stack limit = base * 2^stackUpgrades
    private boolean infiniteStackUpgrade;
    private boolean enabled;
    private float windowX = 0.02f;
    private float windowY = 0.02f;
    private boolean collapsed;

    public PocketState() {
        this.spaceUpgrades = 0;
        this.pageUpgrades = 0;
        this.stackUpgrades = 0;
        this.infiniteStackUpgrade = false;
        this.enabled = true;
        this.collapsed = false;
        resizeStorage();
    }

    public int getSlotsPerPage() {
        return Math.min(SLOTS_PER_PAGE_CAP, BASE_SLOTS_PER_PAGE + Math.min(spaceUpgrades, MAX_SPACE_UPGRADES - 2));
    }

    public int getPageCount() {
        if (spaceUpgrades < MAX_SPACE_UPGRADES) {
            return BASE_PAGES;
        }
        return Math.min(PAGES_CAP, BASE_PAGES + Math.min(pageUpgrades, MAX_PAGE_UPGRADES));
    }

    public int getSpaceUpgrades() {
        return spaceUpgrades;
    }

    public void setSpaceUpgrades(int value) {
        this.spaceUpgrades = Math.max(0, Math.min(MAX_SPACE_UPGRADES, value));
        resizeStorage();
    }

    public int getPageUpgrades() {
        return pageUpgrades;
    }

    public void setPageUpgrades(int value) {
        this.pageUpgrades = Math.max(0, Math.min(MAX_PAGE_UPGRADES, value));
        resizeStorage();
    }

    public int getStackUpgrades() {
        return stackUpgrades;
    }

    public void setStackUpgrades(int value) {
        this.stackUpgrades = Math.max(0, Math.min(MAX_STACK_UPGRADES, value));
    }

    public boolean isInfiniteStackUpgrade() {
        return infiniteStackUpgrade;
    }

    public void setInfiniteStackUpgrade(boolean value) {
        this.infiniteStackUpgrade = value;
    }

    public int getStackMultiplier() {
        if (infiniteStackUpgrade) return Integer.MAX_VALUE;
        if (stackUpgrades == 0) return 1;
        return 1 << stackUpgrades; // 2^stackUpgrades
    }

    public boolean isEnabled() {
        return true;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = true;
    }

    public float getWindowX() {
        return windowX;
    }

    public void setWindowX(float x) {
        this.windowX = Math.max(0.0f, Math.min(1.0f, x));
    }

    public float getWindowY() {
        return windowY;
    }

    public void setWindowY(float y) {
        this.windowY = Math.max(0.0f, Math.min(1.0f, y));
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    public void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
    }

    public ItemStack getStack(int page, int slot) {
        if (!isValid(page, slot)) return null;
        return pages[page][slot];
    }

    public void setStack(int page, int slot, ItemStack stack) {
        if (!isValid(page, slot)) return;
        pages[page][slot] = stack;
    }

    public ItemStack[] getPage(int page) {
        if (page < 0 || page >= pages.length) return new ItemStack[0];
        return pages[page];
    }

    public int countItemsInPage(int page) {
        if (page < 0 || page >= pages.length) return 0;
        int n = 0;
        for (ItemStack s : pages[page]) {
            if (s != null) n++;
        }
        return n;
    }

    /** True if any page contains at least one non-empty slot. */
    public boolean hasStoredItems() {
        if (pages == null) return false;
        for (int p = 0; p < pages.length; p++) {
            if (countItemsInPage(p) > 0) return true;
        }
        return false;
    }

    public boolean isValid(int page, int slot) {
        return page >= 0 && page < pages.length && slot >= 0 && slot < pages[page].length;
    }

    private void resizeStorage() {
        int pageCount = getPageCount();
        int slotsPerPage = getSlotsPerPage();
        ItemStack[][] newPages = new ItemStack[pageCount][];
        for (int p = 0; p < pageCount; p++) {
            newPages[p] = new ItemStack[slotsPerPage];
            if (pages != null && p < pages.length) {
                int copy = Math.min(pages[p].length, slotsPerPage);
                for (int s = 0; s < copy; s++) {
                    newPages[p][s] = pages[p][s];
                }
            }
        }
        this.pages = newPages;
    }

    /**
     * Write ItemStack to NBT with Count stored as explicit int (not vanilla byte).
     * Vanilla {@link ItemStack#writeToNBT(NBTTagCompound)} casts stackSize to byte,
     * which corrupts stack sizes above 127 for upgraded pockets.
     */
    public static void writeItemStackToNBT(ItemStack stack, NBTTagCompound tag) {
        if (stack == null) return;
        tag.setShort("id", (short) Item.getIdFromItem(stack.getItem()));
        tag.setInteger("Count", stack.stackSize);
        tag.setShort("Damage", (short) stack.getItemDamage());
        if (stack.stackTagCompound != null) {
            tag.setTag("tag", stack.stackTagCompound);
        }
    }

    /**
     * Read ItemStack from NBT with backward compatibility for Count stored as
     * byte (vanilla), short, or int (new format).
     */
    public static ItemStack readItemStackFromNBT(NBTTagCompound tag) {
        int id = tag.getShort("id");
        if (id == 0) return null;
        Item item = Item.getItemById(id);
        if (item == null) return null;
        int count = readStackCount(tag);
        int damage = tag.getShort("Damage");
        ItemStack stack = new ItemStack(item, count, damage);
        if (tag.hasKey("tag")) {
            stack.stackTagCompound = tag.getCompoundTag("tag");
        }
        return stack;
    }

    private static int readStackCount(NBTTagCompound tag) {
        if (!tag.hasKey("Count")) return 0;
        NBTBase countTag = tag.getTag("Count");
        if (countTag instanceof NBTTagInt) {
            return ((NBTTagInt) countTag).func_150287_c();
        }
        if (countTag instanceof NBTTagByte) {
            int b = ((NBTTagByte) countTag).func_150290_a();
            return b < 0 ? b + 256 : b;
        }
        if (countTag instanceof NBTTagShort) {
            return ((NBTTagShort) countTag).func_150289_e();
        }
        return 0;
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("spaceUpgrades", spaceUpgrades);
        tag.setInteger("pageUpgrades", pageUpgrades);
        tag.setInteger("stackUpgrades", stackUpgrades);
        tag.setBoolean("infiniteStackUpgrade", infiniteStackUpgrade);
        tag.setBoolean("enabled", enabled);
        tag.setFloat("windowX", windowX);
        tag.setFloat("windowY", windowY);
        tag.setBoolean("collapsed", collapsed);

        NBTTagList pageList = new NBTTagList();
        for (int p = 0; p < pages.length; p++) {
            NBTTagList slotList = new NBTTagList();
            for (int s = 0; s < pages[p].length; s++) {
                if (pages[p][s] != null) {
                    NBTTagCompound slotTag = new NBTTagCompound();
                    slotTag.setInteger("Slot", s);
                    writeItemStackToNBT(pages[p][s], slotTag);
                    slotList.appendTag(slotTag);
                }
            }
            NBTTagCompound pageTag = new NBTTagCompound();
            pageTag.setInteger("Page", p);
            pageTag.setTag("Items", slotList);
            pageList.appendTag(pageTag);
        }
        tag.setTag("Pages", pageList);
        return tag;
    }

    public void readFromNBT(NBTTagCompound tag) {
        if (tag == null) {
            spaceUpgrades = 0;
            pageUpgrades = 0;
            stackUpgrades = 0;
            infiniteStackUpgrade = false;
            enabled = false;
            collapsed = false;
            resizeStorage();
            return;
        }
        spaceUpgrades = tag.getInteger("spaceUpgrades");
        pageUpgrades = tag.getInteger("pageUpgrades");
        stackUpgrades = tag.hasKey("stackUpgrades") ? tag.getInteger("stackUpgrades") : 0;
        infiniteStackUpgrade = tag.hasKey("infiniteStackUpgrade") ? tag.getBoolean("infiniteStackUpgrade") : false;
        enabled = tag.getBoolean("enabled");
        windowX = tag.hasKey("windowX") ? tag.getFloat("windowX") : 0.02f;
        windowY = tag.hasKey("windowY") ? tag.getFloat("windowY") : 0.02f;
        collapsed = tag.getBoolean("collapsed");
        resizeStorage();

        NBTTagList pageList = tag.getTagList("Pages", 10);
        for (int i = 0; i < pageList.tagCount(); i++) {
            NBTTagCompound pageTag = pageList.getCompoundTagAt(i);
            int p = pageTag.getInteger("Page");
            if (p < 0 || p >= pages.length) continue;
            NBTTagList slotList = pageTag.getTagList("Items", 10);
            for (int j = 0; j < slotList.tagCount(); j++) {
                NBTTagCompound slotTag = slotList.getCompoundTagAt(j);
                int s = slotTag.getInteger("Slot");
                if (s < 0 || s >= pages[p].length) continue;
                pages[p][s] = readItemStackFromNBT(slotTag);
            }
        }
    }
}
