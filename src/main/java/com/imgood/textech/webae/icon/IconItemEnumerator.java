package com.imgood.textech.webae.icon;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.webae.recipe.RecipeItemEntries;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Builds the set of {@link ItemStack}s to render for the WebAE icon cache.
 * Prefers NEI {@code ItemList.items} (includes GT meta variants); when NEI is
 * unavailable falls back to registry meta=0 plus {@link Item#getSubItems}
 * sub-item enumeration (Phase 8 scheme F).
 */
@SideOnly(Side.CLIENT)
public final class IconItemEnumerator {

    private IconItemEnumerator() {}

    public static List<StackTask> collectForScope(IconExportScope scope, List<String> explicitItemIds) {
        if (scope == IconExportScope.SNAPSHOT || scope == IconExportScope.LIST) {
            return resolveStackTasks(explicitItemIds);
        }
        return collectStackTasks();
    }

    public static List<StackTask> collectStackTasks() {
        LinkedHashMap<String, StackTask> byId = new LinkedHashMap<String, StackTask>();
        int neiCount = 0;
        for (ItemStack stack : getNeiStacks()) {
            if (addStack(byId, stack)) {
                neiCount++;
            }
        }
        int registryAdded = 0;
        int subItemAdded = 0;
        if (byId.isEmpty()) {
            Iterator<?> it = Item.itemRegistry.iterator();
            while (it.hasNext()) {
                Object o = it.next();
                if (!(o instanceof Item)) continue;
                Item item = (Item) o;
                if (addStack(byId, new ItemStack(item, 1, 0))) {
                    registryAdded++;
                }
                subItemAdded += addSubItemVariants(byId, item);
            }
        }
        AdvanceDataMonitor.LOG.info(
            "[WebAE] Icon enumerator: {} unique stacks ({} from NEI, {} registry meta=0, {} subItems)",
            byId.size(),
            neiCount,
            registryAdded,
            subItemAdded);
        return new ArrayList<StackTask>(byId.values());
    }

    public static List<StackTask> resolveStackTasks(List<String> itemIds) {
        LinkedHashMap<String, StackTask> byId = new LinkedHashMap<String, StackTask>();
        if (itemIds == null) return new ArrayList<StackTask>();
        int requested = itemIds.size();
        for (String itemId : itemIds) {
            StackTask task = resolveSingle(itemId);
            if (task != null && !byId.containsKey(task.itemId)) {
                byId.put(task.itemId, task);
            }
        }
        AdvanceDataMonitor.LOG
            .info("[WebAE] Icon enumerator (scoped): {} resolved tasks ({} requested ids)", byId.size(), requested);
        return new ArrayList<StackTask>(byId.values());
    }

    public static StackTask resolveSingle(String itemId) {
        if (itemId == null || itemId.isEmpty()) return null;
        if (itemId.startsWith(IconItemId.FLUID_PREFIX)) {
            String fluidName = itemId.substring(IconItemId.FLUID_PREFIX.length());
            Fluid fluid = FluidRegistry.getFluid(fluidName);
            if (fluid == null) {
                AdvanceDataMonitor.LOG.warn("[WebAE] Icon resolve failed: unknown fluid '{}'", itemId);
                return null;
            }
            return new StackTask(itemId, null, fluid);
        }
        for (String candidate : IconItemId.lookupCandidates(itemId)) {
            StackTask task = resolveItemStackCandidate(candidate);
            if (task != null) return task;
        }
        AdvanceDataMonitor.LOG.debug("[WebAE] Icon resolve failed: no registry match for '{}'", itemId);
        return null;
    }

    /**
     * Resolves one registry/meta candidate. Does not require {@code getIconIndex} — custom
     * {@code IItemRenderer} items are exported via {@link IconExportResolver}.
     */
    private static StackTask resolveItemStackCandidate(String candidate) {
        if (candidate == null || candidate.isEmpty()) return null;
        // Prefer item registry (same path as recipe icons) before pseudo-tile block lookup.
        StackTask registryTask = resolveFromItemRegistry(candidate);
        if (registryTask != null) return registryTask;
        StackTask tileTask = IconTileResolver.resolve(candidate);
        if (tileTask != null) return tileTask;
        return null;
    }

    private static StackTask resolveFromItemRegistry(String candidate) {
        String registry = candidate;
        int meta = 0;
        int colon = candidate.lastIndexOf(':');
        if (colon > 0) {
            String suffix = candidate.substring(colon + 1);
            if (suffix.matches("\\d+")) {
                registry = candidate.substring(0, colon);
                meta = Integer.parseInt(suffix);
            }
        }
        Item item = (Item) Item.itemRegistry.getObject(registry);
        if (item == null) return null;
        ItemStack stack = new ItemStack(item, 1, meta);
        String registryName = Item.itemRegistry.getNameForObject(item);
        if (registryName == null || registryName.isEmpty()) return null;
        int damage = stack.getItemDamage();
        if (damage == Short.MAX_VALUE) damage = 0;
        String canonicalId = RecipeItemEntries.buildItemId(registryName, damage);
        return new StackTask(canonicalId, stack.copy(), null);
    }

    private static int addSubItemVariants(Map<String, StackTask> byId, Item item) {
        if (item == null) return 0;
        List<ItemStack> subItems = new ArrayList<ItemStack>();
        try {
            item.getSubItems(item, null, subItems);
        } catch (Throwable ignored) {
            return 0;
        }
        int added = 0;
        for (ItemStack stack : subItems) {
            if (stack == null || stack.getItem() == null) continue;
            int meta = stack.getItemDamage();
            if (meta == 0) continue;
            if (addStack(byId, stack)) {
                added++;
            }
        }
        return added;
    }

    private static boolean addStack(Map<String, StackTask> byId, ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        String registryName = Item.itemRegistry.getNameForObject(stack.getItem());
        if (registryName == null || registryName.isEmpty()) return false;
        int meta = stack.getItemDamage();
        if (meta == Short.MAX_VALUE) meta = 0;
        String itemId = RecipeItemEntries.buildItemId(registryName, meta);
        // Do NOT gate on getIconIndex: GT++ / GTNH material items (e.g. miscutils:itemIngot*)
        // often return null iconIndex and rely on IItemRenderer / NESQL drawItem. The old
        // getIconIndex check dropped those stacks from full-pack upload while dusts with
        // atlas icons were kept — AE storage then 404s for the missing PNG keys.
        if (byId.containsKey(itemId)) {
            if (!IconFluidRenderer.isFluidDropItem(stack.getItem())) {
                return false;
            }
            StackTask existing = byId.get(itemId);
            if (existing != null && IconFluidRenderer.isFluidDropStack(existing.stack)
                && !IconFluidRenderer.isFluidDropStack(stack)) {
                return false;
            }
        }
        byId.put(itemId, new StackTask(itemId, stack.copy(), null));
        return true;
    }

    @SuppressWarnings("unchecked")
    private static List<ItemStack> getNeiStacks() {
        try {
            Class<?> itemListCls = Class.forName("codechicken.nei.ItemList");
            Field itemsField = itemListCls.getField("items");
            List<ItemStack> items = (List<ItemStack>) itemsField.get(null);
            if (items != null && !items.isEmpty()) {
                return new ArrayList<ItemStack>(items);
            }
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] NEI ItemList unavailable for icon upload, using registry fallback", e);
        }
        return new ArrayList<ItemStack>();
    }

    /** Item stack render job with canonical cache key. */
    public static final class StackTask {

        public final String itemId;
        public final ItemStack stack;
        public final Fluid fluid;

        StackTask(String itemId, ItemStack stack, Fluid fluid) {
            this.itemId = itemId;
            this.stack = stack;
            this.fluid = fluid;
        }
    }
}
