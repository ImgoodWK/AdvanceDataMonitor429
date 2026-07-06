package com.imgood.textech.webae.recipe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.webae.dto.RecipeDto;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Client-side NEI recipe collector.
 * Iterates NEI craft handers and usage handlers to extract recipe data.
 * Only usable on the client side where NEI is present.
 *
 * <p>
 * NEI handlers expose recipes via {@code codechicken.nei.PositionedStack}
 * wrappers rather than raw {@code ItemStack}. This collector reflects into the
 * {@code item} field of {@code PositionedStack} (with a fallback to {@code stack})
 * to recover the underlying {@code ItemStack} for serialization.
 * </p>
 */
@SideOnly(Side.CLIENT)
public class NeiRecipeCollector {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();
    private static boolean neiAvailable = false;
    private static boolean neiChecked = false;

    /** Cached reflection field handle for PositionedStack.item (lazy). */
    private static volatile Field positionedStackItemField;
    /** Cached reflection field handle for PositionedStack.stack (fallback). */
    private static volatile Field positionedStackStackField;
    /** Cached reflection field handle for PositionedStack.items (GTNH NEI primary storage). */
    private static volatile Field positionedStackItemsField;
    /** True once we have confirmed (or failed to confirm) PositionedStack class. */
    private static volatile boolean positionedStackResolved;

    /**
     * Check whether NEI recipe APIs are available on this client.
     */
    public static boolean isNeiAvailable() {
        if (!neiChecked) {
            try {
                Class.forName("codechicken.nei.recipe.GuiCraftingRecipe");
                neiAvailable = true;
            } catch (ClassNotFoundException e) {
                neiAvailable = false;
            }
            neiChecked = true;
        }
        return neiAvailable;
    }

    /** Cap verbose extraction-failure logs per JVM session. */
    private static int debugExtractFailLogCount = 0;

    /**
     * Collect all NEI recipes from both crafting and usage handlers.
     *
     * @return list of all collected RecipeDto objects, or empty list if NEI is unavailable
     */
    public static List<RecipeDto> collectAll() {
        return collectAll(false);
    }

    public static List<RecipeDto> collectAll(boolean deepItemScan) {
        if (!isClientMainThread()) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null) {
                return collectAllOnClientMainThread(mc, deepItemScan);
            }
        }
        return collectAllImpl(deepItemScan);
    }

    /**
     * Collect NEI recipes related to specific item ids (snapshot upload scope).
     */
    public static List<RecipeDto> collectForItemIds(List<String> itemIds) {
        List<RecipeDto> all = new ArrayList<RecipeDto>();
        if (itemIds == null || itemIds.isEmpty() || !isNeiAvailable()) {
            return all;
        }
        ensureNeiFuelsLoaded();
        Set<String> seen = new HashSet<String>();
        for (String itemId : itemIds) {
            ItemStack stack = stackFromItemId(itemId);
            if (stack == null) continue;
            collectRecipesForStack(stack, all, seen);
        }
        return all;
    }

    private static boolean isClientMainThread() {
        return "Client thread".equals(
            Thread.currentThread()
                .getName());
    }

    /**
     * NEI / ItemList APIs are client-main-thread only. If a legacy caller still invokes
     * {@link #collectAll()} from a worker thread, marshal onto the render thread and block.
     */
    private static List<RecipeDto> collectAllOnClientMainThread(final Minecraft mc, final boolean deepItemScan) {
        final AtomicReference<List<RecipeDto>> result = new AtomicReference<List<RecipeDto>>();
        final CountDownLatch latch = new CountDownLatch(1);
        final String callerThread = Thread.currentThread()
            .getName();
        mc.func_152344_a(new Runnable() {

            @Override
            public void run() {
                try {
                    result.set(collectAllImpl(deepItemScan));
                } finally {
                    latch.countDown();
                }
            }
        });
        try {
            latch.await(45, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread()
                .interrupt();
            AdvanceDataMonitor.LOG.warn("[WebAE] NEI collection interrupted on {}", callerThread);
        }
        List<RecipeDto> collected = result.get();
        if (collected == null) {
            AdvanceDataMonitor.LOG.error(
                "[WebAE] NEI collection timed out or failed to run on client main thread (caller={})",
                callerThread);
            return new ArrayList<RecipeDto>();
        }
        return collected;
    }

    private static List<RecipeDto> collectAllImpl(boolean deepItemScan) {
        List<RecipeDto> all = new ArrayList<RecipeDto>();
        if (!isNeiAvailable()) {
            return all;
        }
        ensureNeiFuelsLoaded();

        int rawCraftingRegistry = countHandlerRegistry(
            "codechicken.nei.recipe.GuiCraftingRecipe",
            "craftinghandlers",
            "serialCraftingHandlers");
        int rawUsageRegistry = countHandlerRegistry(
            "codechicken.nei.recipe.GuiUsageRecipe",
            "usagehandlers",
            "serialUsageHandlers");

        List<?> craftingHandlers = null;
        String craftingPath = "none";
        try {
            craftingHandlers = queryGtnhHandlers("codechicken.nei.recipe.GuiCraftingRecipe", "getCraftingHandlers");
            craftingPath = "gtnh-query";
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] GTNH getCraftingHandlers unavailable", e);
            craftingPath = "gtnh-query-error";
        }
        if (craftingHandlers == null || craftingHandlers.isEmpty()) {
            craftingHandlers = loadHandlersDirect(
                "codechicken.nei.recipe.GuiCraftingRecipe",
                "craftinghandlers",
                "serialCraftingHandlers",
                false);
            craftingPath = "direct-load";
        }
        if ((craftingHandlers == null || craftingHandlers.isEmpty()) && rawCraftingRegistry > 0) {
            craftingHandlers = loadHandlersDirect(
                "codechicken.nei.recipe.GuiCraftingRecipe",
                "craftinghandlers",
                "serialCraftingHandlers",
                false);
            craftingPath = "direct-load-retry";
        }

        int craftingHandlerCount = craftingHandlers != null ? craftingHandlers.size() : 0;
        if (craftingHandlers != null) {
            collectFromHandlerList(craftingHandlers, all, "crafting");
        }

        List<?> usageHandlers = null;
        String usagePath = "none";
        try {
            usageHandlers = queryGtnhHandlers("codechicken.nei.recipe.GuiUsageRecipe", "getUsageHandlers");
            usagePath = "gtnh-query";
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] GTNH getUsageHandlers unavailable", e);
            usagePath = "gtnh-query-error";
        }
        if (usageHandlers == null || usageHandlers.isEmpty()) {
            usageHandlers = loadHandlersDirect(
                "codechicken.nei.recipe.GuiUsageRecipe",
                "usagehandlers",
                "serialUsageHandlers",
                true);
            usagePath = "direct-load";
        }
        if ((usageHandlers == null || usageHandlers.isEmpty()) && rawUsageRegistry > 0) {
            usageHandlers = loadHandlersDirect(
                "codechicken.nei.recipe.GuiUsageRecipe",
                "usagehandlers",
                "serialUsageHandlers",
                true);
            usagePath = "direct-load-retry";
        }

        int usageHandlerCount = usageHandlers != null ? usageHandlers.size() : 0;
        if (usageHandlers != null) {
            collectFromHandlerList(usageHandlers, all, "usage");
        }

        String itemScanPath = "skipped";
        int itemListSize = -1;
        if (all.isEmpty() || deepItemScan) {
            itemListSize = getNeiItemSnapshot().size();
            AdvanceDataMonitor.LOG.info(
                "[WebAE] {} — starting item-driven scan (ItemList={})...",
                all.isEmpty() ? "Fast NEI recipe paths returned 0" : "Deep item scan requested",
                itemListSize);
            itemScanPath = "running";
            collectViaItemQueries(all, deepItemScan ? -1 : 512);
            itemScanPath = "done";
        }

        final String threadName = Thread.currentThread()
            .getName();
        AdvanceDataMonitor.LOG.info(
            "[WebAE] NEI collectAll [{}]: rawCrafting={}, rawUsage={}, craftingPath={}, "
                + "usagePath={}, itemScan={}, craftingHandlers={}, usageHandlers={}, collected={}",
            threadName,
            rawCraftingRegistry,
            rawUsageRegistry,
            craftingPath,
            usagePath,
            itemScanPath,
            craftingHandlerCount,
            usageHandlerCount,
            all.size());
        return all;
    }

    /** Pre-load furnace fuels — GTNH {@link RecipeHandlerQuery} does this before handler lookup. */
    private static void ensureNeiFuelsLoaded() {
        try {
            Class<?> fuelHandler = Class.forName("codechicken.nei.recipe.FuelRecipeHandler");
            try {
                Method parallel = fuelHandler.getMethod("findFuelsOnceParallel");
                parallel.invoke(null);
                return;
            } catch (NoSuchMethodException ignored) {}
            Method once = fuelHandler.getMethod("findFuelsOnce");
            once.invoke(null);
        } catch (Exception ignored) {
            try {
                Class<?> template = Class.forName("codechicken.nei.recipe.TemplateRecipeHandler");
                Method once = template.getMethod("findFuelsOnce");
                once.invoke(null);
            } catch (Exception ignored2) {}
        }
    }

    private static int countHandlerRegistry(String guiClassName, String parallelField, String serialField) {
        try {
            Class<?> guiClass = Class.forName(guiClassName);
            int count = 0;
            Field pf = guiClass.getField(parallelField);
            List<?> parallel = (List<?>) pf.get(null);
            if (parallel != null) count += parallel.size();
            try {
                Field sf = guiClass.getField(serialField);
                List<?> serial = (List<?>) sf.get(null);
                if (serial != null) count += serial.size();
            } catch (NoSuchFieldException ignored) {}
            return count;
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Load every registered handler by calling {@code getAllRecipeHandler()} / usage
     * {@code buildAllRecipesHandler} logic directly, bypassing {@code RecipeHandlerQuery}
     * search filters that can drop all handlers when NEI search filters are active.
     */
    private static List<Object> loadHandlersDirect(String guiClassName, String parallelField, String serialField,
        boolean usageSide) {
        List<Object> loaded = new ArrayList<Object>();
        try {
            Class<?> guiClass = Class.forName(guiClassName);
            Class<?> templateCls = Class.forName("codechicken.nei.recipe.TemplateRecipeHandler");
            Field pf = guiClass.getField(parallelField);
            List<?> parallel = (List<?>) pf.get(null);
            if (parallel != null) {
                populateLoadedHandlers(parallel, loaded, templateCls, usageSide);
            }
            try {
                Field sf = guiClass.getField(serialField);
                List<?> serial = (List<?>) sf.get(null);
                if (serial != null) {
                    populateLoadedHandlers(serial, loaded, templateCls, usageSide);
                }
            } catch (NoSuchFieldException ignored) {}
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Direct NEI handler load failed for {}", guiClassName, e);
        }
        return loaded;
    }

    private static void populateLoadedHandlers(List<?> registry, List<Object> out, Class<?> templateCls,
        boolean usageSide) {
        for (Object handler : registry) {
            if (handler == null) continue;
            try {
                Object active = instantiateLoadedHandler(handler, templateCls, usageSide);
                if (active != null && getNumRecipes(active) > 0) {
                    out.add(active);
                }
            } catch (Throwable t) {
                if (Config.debugWebae) {
                    AdvanceDataMonitor.LOG.warn(
                        "[WebAE] Handler load skipped: {}",
                        handler.getClass()
                            .getName(),
                        t);
                }
            }
        }
    }

    private static Object instantiateLoadedHandler(Object handler, Class<?> templateCls, boolean usageSide)
        throws Exception {
        if (!templateCls.isInstance(handler)) {
            return handler;
        }
        if (!usageSide) {
            Object loaded = null;
            try {
                Method getAll = templateCls.getMethod("getAllRecipeHandler");
                loaded = getAll.invoke(handler);
                if (loaded != null && getNumRecipes(loaded) > 0) {
                    return loaded;
                }
            } catch (NoSuchMethodException ignored) {
                // Pre-GTNH NEI fork
            }
            Method newInstance = templateCls.getMethod("newInstance");
            Object instance = newInstance.invoke(handler);
            Method loadCrafting = templateCls.getMethod("loadCraftingRecipes", String.class, Object[].class);
            loadCrafting.invoke(instance, "all", new Object[0]);
            if (getNumRecipes(instance) > 0) {
                return instance;
            }
            return loaded != null ? loaded : instance;
        }
        // Mirror GuiUsageRecipe.buildAllRecipesHandler
        Method newInstance = templateCls.getMethod("newInstance");
        Object instance = newInstance.invoke(handler);
        Field transferRectsField = templateCls.getField("transferRects");
        Collection<?> transferRects = (Collection<?>) transferRectsField.get(instance);
        Method loadCrafting = templateCls.getMethod("loadCraftingRecipes", String.class, Object[].class);
        if (transferRects == null || transferRects.isEmpty()) {
            loadCrafting.invoke(instance, "all", new Object[0]);
            return instance;
        }
        String specifyId = null;
        try {
            Method specify = templateCls.getMethod("specifyTransferRect");
            Object id = specify.invoke(instance);
            if (id != null) specifyId = id.toString();
        } catch (Exception ignored) {}
        Class<?> rectCls = Class.forName("codechicken.nei.recipe.TemplateRecipeHandler$RecipeTransferRect");
        Field outputIdField = rectCls.getField("outputId");
        Field resultsField = rectCls.getField("results");
        for (Object rect : transferRects) {
            if (rect == null) continue;
            String outputId = (String) outputIdField.get(rect);
            if (specifyId != null && outputId != null && !specifyId.equals(outputId)) {
                continue;
            }
            Object results = resultsField.get(rect);
            Object[] loadArgs = buildLoadCraftingArgs(outputId, results);
            loadCrafting.invoke(instance, loadArgs);
            if (getNumRecipes(instance) > 0) {
                return instance;
            }
        }
        loadCrafting.invoke(instance, "all", new Object[0]);
        return instance;
    }

    private static Object[] buildLoadCraftingArgs(String outputId, Object results) {
        if (results instanceof Object[]) {
            Object[] resultArr = (Object[]) results;
            Object[] args = new Object[resultArr.length + 1];
            args[0] = outputId;
            System.arraycopy(resultArr, 0, args, 1, resultArr.length);
            return args;
        }
        return new Object[] { outputId, results };
    }

    /**
     * GTNH NEI entry point: {@code GuiCraftingRecipe.getCraftingHandlers("all")} /
     * {@code GuiUsageRecipe.getUsageHandlers("all")}. Each returned handler is already
     * loaded (via {@code buildAllRecipesHandler}) and filtered to {@code numRecipes() > 0}.
     */
    @SuppressWarnings("unchecked")
    private static List<?> queryGtnhHandlers(String guiClassName, String methodName) throws Exception {
        Class<?> guiClass = Class.forName(guiClassName);
        Method method = guiClass.getMethod(methodName, String.class, Object[].class);
        return (List<?>) method.invoke(null, "all", new Object[0]);
    }

    /**
     * Fallback: iterate NEI {@link codechicken.nei.ItemList} and call
     * {@code GuiCraftingRecipe.getCraftingHandlers("item", stack)} per item — the same API NEI
     * uses when the player clicks an item in the item panel.
     */
    private static void collectViaItemQueries(List<RecipeDto> all) {
        collectViaItemQueries(all, -1);
    }

    private static void collectViaItemQueries(List<RecipeDto> all, int maxItems) {
        List<ItemStack> items = getNeiItemSnapshot();
        if (items.isEmpty()) {
            AdvanceDataMonitor.LOG.warn("[WebAE] ItemList.items is empty; item-driven recipe scan skipped");
            return;
        }

        Set<String> seen = new HashSet<String>();
        for (RecipeDto existing : all) {
            seen.add(recipeDedupKey(existing));
        }

        Method getHandlers = null;
        Method normalize = null;
        try {
            Class<?> guiCrafting = Class.forName("codechicken.nei.recipe.GuiCraftingRecipe");
            getHandlers = guiCrafting.getMethod("getCraftingHandlers", String.class, Object[].class);
            try {
                Class<?> stackInfo = Class.forName("codechicken.nei.recipe.StackInfo");
                normalize = stackInfo.getMethod("normalizeRecipeQueryStack", ItemStack.class);
            } catch (ClassNotFoundException ignored) {}
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Item-driven NEI scan unavailable", e);
            return;
        }

        int before = all.size();
        int itemsScanned = 0;
        int itemsWithHandlers = 0;
        final int totalItems = items.size();

        for (int i = 0; i < totalItems; i++) {
            if (maxItems > 0 && itemsScanned >= maxItems) break;
            ItemStack stack = items.get(i);
            if (stack == null || stack.getItem() == null) continue;
            itemsScanned++;
            try {
                ItemStack query = stack.copy();
                if (normalize != null) {
                    query = (ItemStack) normalize.invoke(null, query);
                }
                List<?> handlers = (List<?>) getHandlers.invoke(null, "item", new Object[] { query });
                if (handlers == null || handlers.isEmpty()) continue;
                itemsWithHandlers++;
                mergeHandlersIntoRecipes(handlers, all, seen, "crafting");
            } catch (Exception ignored) {}

            if (itemsScanned % 5000 == 0) {
                AdvanceDataMonitor.LOG.info(
                    "[WebAE] Item scan progress: {}/{} items, {} handlers hits, {} recipes",
                    itemsScanned,
                    totalItems,
                    itemsWithHandlers,
                    all.size());
            }
        }

        AdvanceDataMonitor.LOG.info(
            "[WebAE] Item-driven scan done: {} items scanned, {} had handlers, {} recipes total (+{})",
            itemsScanned,
            itemsWithHandlers,
            all.size(),
            all.size() - before);
    }

    @SuppressWarnings("unchecked")
    private static List<ItemStack> getNeiItemSnapshot() {
        try {
            Class<?> itemListCls = Class.forName("codechicken.nei.ItemList");
            Field itemsField = itemListCls.getField("items");
            List<ItemStack> items = (List<ItemStack>) itemsField.get(null);
            if (items != null && !items.isEmpty()) {
                return new ArrayList<ItemStack>(items);
            }
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to read codechicken.nei.ItemList.items", e);
        }
        return new ArrayList<ItemStack>();
    }

    private static void mergeHandlersIntoRecipes(List<?> handlers, List<RecipeDto> all, Set<String> seen, String type) {
        for (Object handler : handlers) {
            if (handler == null) continue;
            try {
                String handlerId = getHandlerId(handler);
                String handlerName = getRecipeHandlerName(handler);
                int count = getNumRecipes(handler);
                for (int r = 0; r < count; r++) {
                    try {
                        RecipeDto dto = extractRecipe(handler, r, handlerId, handlerName, type);
                        if (dto == null || (dto.outputs.isEmpty() && dto.inputs.isEmpty())) continue;
                        String key = recipeDedupKey(dto);
                        if (!seen.add(key)) continue;
                        all.add(dto);
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
        }
    }

    private static String recipeDedupKey(RecipeDto dto) {
        StringBuilder sb = new StringBuilder();
        sb.append(dto.handlerId)
            .append('|')
            .append(dto.recipeIndex)
            .append('|');
        if (dto.outputs != null) {
            for (RecipeDto.ItemEntry e : dto.outputs) {
                sb.append(e.registryName)
                    .append('#')
                    .append(e.stackSize)
                    .append(';');
            }
        }
        sb.append('|');
        if (dto.inputs != null) {
            for (RecipeDto.ItemEntry e : dto.inputs) {
                sb.append(e.registryName)
                    .append('#')
                    .append(e.stackSize)
                    .append(';');
            }
        }
        return sb.toString();
    }

    private static void collectFromHandlerList(List<?> handlers, List<RecipeDto> out, String type) {
        if (handlers == null) return;
        int slotsWithRecipes = 0;
        int extracted = 0;
        for (Object handler : handlers) {
            if (handler == null) continue;
            try {
                String handlerId = getHandlerId(handler);
                String handlerName = getRecipeHandlerName(handler);
                int count = getNumRecipes(handler);
                slotsWithRecipes += count;
                for (int i = 0; i < count; i++) {
                    try {
                        RecipeDto dto = extractRecipe(handler, i, handlerId, handlerName, type);
                        if (dto != null && (!dto.outputs.isEmpty() || !dto.inputs.isEmpty())) {
                            out.add(dto);
                            extracted++;
                        }
                    } catch (Exception ignored) {
                        // Skip individual recipe extraction failures
                    }
                }
            } catch (Exception e) {
                AdvanceDataMonitor.LOG.warn(
                    "[WebAE] Failed to process NEI {} handler: {}",
                    type,
                    handler.getClass()
                        .getName(),
                    e);
            }
        }
        if (slotsWithRecipes > 0 && extracted == 0) {
            AdvanceDataMonitor.LOG.debug(
                "[WebAE] NEI {} handler slots present but extraction yielded 0 (slotsWithRecipes={})",
                type,
                slotsWithRecipes);
        }
    }

    /**
     * Legacy path: iterate raw handler registry (pre-GTNH NEI fork or fallback).
     */
    private static int collectCraftingHandlersLegacy(List<RecipeDto> out) throws Exception {
        Class<?> guiCraftingRecipe = Class.forName("codechicken.nei.recipe.GuiCraftingRecipe");
        Field field = guiCraftingRecipe.getField("craftinghandlers");
        List<?> handlers = (List<?>) field.get(null);
        if (handlers == null) return 0;

        int handlerCount = 0;
        for (Object handler : handlers) {
            handlerCount++;
            try {
                Object activeHandler = resolveLoadedHandler(handler, true);
                String handlerId = activeHandler.getClass()
                    .getName();
                String handlerName = getRecipeHandlerName(activeHandler);
                int count = getNumRecipes(activeHandler);
                for (int i = 0; i < count; i++) {
                    try {
                        RecipeDto dto = extractRecipe(activeHandler, i, handlerId, handlerName, "crafting");
                        if (dto != null && (!dto.outputs.isEmpty() || !dto.inputs.isEmpty())) {
                            out.add(dto);
                        }
                    } catch (Exception ignored) {
                        // Skip individual recipe extraction failures
                    }
                }
            } catch (Exception e) {
                AdvanceDataMonitor.LOG.warn(
                    "[WebAE] Failed to process NEI crafting handler: {}",
                    handler.getClass()
                        .getName(),
                    e);
            }
        }
        return handlerCount;
    }

    /**
     * NEI {@link codechicken.nei.recipe.TemplateRecipeHandler} instances keep recipes in a
     * lazily-populated {@code arecipes} list. The shared handler objects in
     * {@code GuiCraftingRecipe.craftinghandlers} often have {@code numRecipes()==0} until
     * {@code loadCraftingRecipes("all")} or {@code getAllRecipeHandler()} is called.
     */
    private static Object resolveLoadedHandler(Object handler, boolean crafting) {
        try {
            Class<?> templateCls = Class.forName("codechicken.nei.recipe.TemplateRecipeHandler");
            if (!templateCls.isInstance(handler)) {
                return handler;
            }
            if (crafting) {
                Method getAll = templateCls.getMethod("getAllRecipeHandler");
                Object loaded = getAll.invoke(handler);
                return loaded != null ? loaded : handler;
            }
            Method newInstance = templateCls.getMethod("newInstance");
            Object instance = newInstance.invoke(handler);
            Method loadUsage = templateCls.getMethod("loadUsageRecipes", String.class, Object[].class);
            loadUsage.invoke(instance, "all", new Object[0]);
            if (getNumRecipes(instance) > 0) {
                return instance;
            }
            // Some handlers only populate via crafting-side "all" load.
            Method getAll = templateCls.getMethod("getAllRecipeHandler");
            Object loaded = getAll.invoke(handler);
            return loaded != null ? loaded : instance;
        } catch (Exception e) {
            return handler;
        }
    }

    /**
     * Collect from usage handlers (IUsageHandler list) — legacy fallback.
     *
     * @return number of usage handlers iterated
     */
    private static int collectUsageHandlersLegacy(List<RecipeDto> out) throws Exception {
        Class<?> guiUsageRecipe = Class.forName("codechicken.nei.recipe.GuiUsageRecipe");
        Field field = guiUsageRecipe.getField("usagehandlers");
        List<?> handlers = (List<?>) field.get(null);
        if (handlers == null) return 0;

        int handlerCount = 0;
        for (Object handler : handlers) {
            handlerCount++;
            try {
                Object activeHandler = resolveLoadedHandler(handler, false);
                String handlerId = activeHandler.getClass()
                    .getName();
                String handlerName = getRecipeHandlerName(activeHandler);
                int count = getNumRecipes(activeHandler);
                for (int i = 0; i < count; i++) {
                    try {
                        RecipeDto dto = extractRecipe(activeHandler, i, handlerId, handlerName, "usage");
                        if (dto != null && (!dto.outputs.isEmpty() || !dto.inputs.isEmpty())) {
                            out.add(dto);
                        }
                    } catch (Exception ignored) {
                        // Skip individual recipe extraction failures
                    }
                }
            } catch (Exception e) {
                AdvanceDataMonitor.LOG.warn(
                    "[WebAE] Failed to process NEI usage handler: {}",
                    handler.getClass()
                        .getName(),
                    e);
            }
        }
        return handlerCount;
    }

    /**
     * Extract a single recipe from a handler by index. NEI handlers return
     * {@code PositionedStack} (or {@code List<PositionedStack>}) from their
     * {@code getResultStack}/{@code getIngredientStacks}/{@code getOtherStacks}
     * methods; we reflect into the {@code item} field (fallback {@code stack})
     * to recover the underlying {@link ItemStack}.
     */
    private static RecipeDto extractRecipe(Object handler, int index, String handlerId, String handlerName, String type)
        throws Exception {
        String displayHandlerName = RecipeDisplayNames.formatHandlerLabel(handlerName, handlerId);
        RecipeDto dto = new RecipeDto(handlerId, index, displayHandlerName);
        List<RecipeDto.ItemEntry> inputs = new ArrayList<RecipeDto.ItemEntry>();
        List<RecipeDto.ItemEntry> outputs = new ArrayList<RecipeDto.ItemEntry>();

        // Primary: read TemplateRecipeHandler.arecipes (most reliable on GTNH NEI).
        if (!extractFromArecipes(handler, index, inputs, outputs)) {
            try {
                Method getResultStack = handler.getClass()
                    .getMethod("getResultStack", int.class);
                Object result = getResultStack.invoke(handler, index);
                ItemStack resultStack = positionedStackToItemStack(result);
                if (resultStack != null) {
                    outputs.add(itemStackToEntry(resultStack));
                }
            } catch (Exception ignored) {}

            try {
                Method getIngredientStacks = handler.getClass()
                    .getMethod("getIngredientStacks", int.class);
                Object ingredients = getIngredientStacks.invoke(handler, index);
                collectPositionedStacks(ingredients, inputs, false);
            } catch (Exception ignored) {}

            try {
                Method getOtherStacks = handler.getClass()
                    .getMethod("getOtherStacks", int.class);
                Object others = getOtherStacks.invoke(handler, index);
                collectPositionedStacks(others, inputs, true);
            } catch (Exception ignored) {}
        }

        dto.inputs = inputs;
        dto.outputs = outputs;
        dto.recipeType = "nei";
        tryCollectGridFromHandler(handler, index, dto);
        Object cached = getCachedArecipe(handler, index);
        applyGtMetadata(dto, cached);
        dto.rawJson = buildRawJson(handler, index, type);
        return dto;
    }

    private static Object getCachedArecipe(Object handler, int index) {
        try {
            Class<?> templateCls = Class.forName("codechicken.nei.recipe.TemplateRecipeHandler");
            if (!templateCls.isInstance(handler)) return null;
            Field arecipesField = templateCls.getField("arecipes");
            List<?> arecipes = (List<?>) arecipesField.get(handler);
            if (arecipes == null || index < 0 || index >= arecipes.size()) return null;
            return arecipes.get(index);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void tryCollectGridFromHandler(Object handler, int index, RecipeDto dto) {
        try {
            Method getIngredientStacks = handler.getClass()
                .getMethod("getIngredientStacks", int.class);
            Object ingredients = getIngredientStacks.invoke(handler, index);
            collectGridSlots(ingredients, dto.gridSlots);
        } catch (Exception ignored) {}
        Object cached = getCachedArecipe(handler, index);
        if (cached != null) {
            try {
                Method getIngredients = cached.getClass()
                    .getMethod("getIngredients");
                collectGridSlots(getIngredients.invoke(cached), dto.gridSlots);
            } catch (Exception ignored) {}
        }
        finalizeGridDimensions(dto);
    }

    private static void finalizeGridDimensions(RecipeDto dto) {
        if (dto.gridSlots == null || dto.gridSlots.isEmpty()) return;
        int maxCol = 0;
        int maxRow = 0;
        for (RecipeDto.GridSlot slot : dto.gridSlots) {
            if (slot.col > maxCol) maxCol = slot.col;
            if (slot.row > maxRow) maxRow = slot.row;
        }
        dto.gridWidth = maxCol + 1;
        dto.gridHeight = maxRow + 1;
    }

    private static void applyGtMetadata(RecipeDto dto, Object cachedRecipe) {
        if (cachedRecipe == null) return;
        try {
            java.lang.reflect.Field eutField = findDeclaredField(cachedRecipe.getClass(), "mEUt");
            if (eutField != null) {
                eutField.setAccessible(true);
                dto.euPerTick = Long.valueOf(eutField.getLong(cachedRecipe));
            }
        } catch (Throwable ignored) {}
        try {
            java.lang.reflect.Field durField = findDeclaredField(cachedRecipe.getClass(), "mDuration");
            if (durField != null) {
                durField.setAccessible(true);
                dto.durationTicks = Integer.valueOf(durField.getInt(cachedRecipe));
            }
        } catch (Throwable ignored) {}
        try {
            java.lang.reflect.Method tierMethod = cachedRecipe.getClass()
                .getMethod("getVoltageTier");
            Object tier = tierMethod.invoke(cachedRecipe);
            if (tier != null) dto.voltageTier = tier.toString();
        } catch (Throwable ignored) {}
    }

    private static java.lang.reflect.Field findDeclaredField(Class<?> cls, String name) {
        Class<?> current = cls;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static void collectGridSlots(Object value, List<RecipeDto.GridSlot> gridSlots) {
        if (value == null) return;
        java.util.List<int[]> coords = new ArrayList<int[]>();
        java.util.List<RecipeDto.ItemEntry> entries = new ArrayList<RecipeDto.ItemEntry>();
        walkPositionedForGrid(value, coords, entries);
        if (coords.isEmpty()) return;
        int minX = coords.get(0)[0];
        int minY = coords.get(0)[1];
        for (int i = 1; i < coords.size(); i++) {
            if (coords.get(i)[0] < minX) minX = coords.get(i)[0];
            if (coords.get(i)[1] < minY) minY = coords.get(i)[1];
        }
        final int cell = 18;
        for (int i = 0; i < coords.size(); i++) {
            int col = (coords.get(i)[0] - minX) / cell;
            int row = (coords.get(i)[1] - minY) / cell;
            RecipeDto.ItemEntry entry = entries.get(i);
            for (RecipeDto.GridSlot existing : gridSlots) {
                if (existing.col == col && existing.row == row) {
                    entry = null;
                    break;
                }
            }
            if (entry != null) {
                gridSlots.add(new RecipeDto.GridSlot(col, row, entry));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void walkPositionedForGrid(Object value, java.util.List<int[]> coords,
        java.util.List<RecipeDto.ItemEntry> entries) {
        if (value == null) return;
        if (isPositionedStack(value)) {
            ItemStack stack = positionedStackToItemStack(value);
            if (stack != null) {
                int[] xy = readPositionedStackCoords(value);
                if (xy != null) {
                    coords.add(xy);
                    entries.add(itemStackToEntry(stack));
                }
            }
            return;
        }
        if (value instanceof ItemStack) {
            return;
        }
        if (value.getClass()
            .isArray()) {
            Object[] arr = (Object[]) value;
            for (int i = 0; i < arr.length; i++) {
                walkPositionedForGrid(arr[i], coords, entries);
            }
            return;
        }
        if (value instanceof Iterable) {
            Iterator<?> iter = ((Iterable<?>) value).iterator();
            while (iter.hasNext()) {
                walkPositionedForGrid(iter.next(), coords, entries);
            }
        }
    }

    private static boolean isPositionedStack(Object obj) {
        if (obj == null) return false;
        return obj.getClass()
            .getName()
            .equals("codechicken.nei.PositionedStack")
            || obj.getClass()
                .getName()
                .endsWith(".PositionedStack");
    }

    private static int[] readPositionedStackCoords(Object positionedStack) {
        try {
            Field relx = positionedStack.getClass()
                .getField("relx");
            Field rely = positionedStack.getClass()
                .getField("rely");
            int x = relx.getInt(positionedStack);
            int y = rely.getInt(positionedStack);
            return new int[] { x, y };
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Read stacks from {@code TemplateRecipeHandler.arecipes[index]}.
     * 
     * @return true if any input or output was extracted
     */
    private static boolean extractFromArecipes(Object handler, int index, List<RecipeDto.ItemEntry> inputs,
        List<RecipeDto.ItemEntry> outputs) {
        try {
            Class<?> templateCls = Class.forName("codechicken.nei.recipe.TemplateRecipeHandler");
            if (!templateCls.isInstance(handler)) return false;
            Field arecipesField = templateCls.getField("arecipes");
            List<?> arecipes = (List<?>) arecipesField.get(handler);
            if (arecipes == null || index < 0 || index >= arecipes.size()) return false;
            Object cached = arecipes.get(index);
            if (cached == null) return false;

            try {
                Method computeVisuals = cached.getClass()
                    .getMethod("computeVisuals");
                computeVisuals.invoke(cached);
            } catch (Exception ignored) {}

            int before = inputs.size() + outputs.size();
            try {
                Method getResult = cached.getClass()
                    .getMethod("getResult");
                Object result = getResult.invoke(cached);
                ItemStack resultStack = positionedStackToItemStack(result);
                if (resultStack != null) {
                    outputs.add(itemStackToEntry(resultStack));
                }
            } catch (Exception ignored) {}
            try {
                Method getIngredients = cached.getClass()
                    .getMethod("getIngredients");
                collectPositionedStacks(getIngredients.invoke(cached), inputs, false);
            } catch (Exception ignored) {}
            try {
                Method getOtherStacks = cached.getClass()
                    .getMethod("getOtherStacks");
                collectPositionedStacks(getOtherStacks.invoke(cached), inputs, true);
            } catch (Exception ignored) {}
            // GT / mod handlers may expose outputs/inputs under alternate names
            try {
                Method getOutputs = cached.getClass()
                    .getMethod("getOutputs");
                collectPositionedStacks(getOutputs.invoke(cached), outputs, false);
            } catch (Exception ignored) {}
            try {
                Method getInputs = cached.getClass()
                    .getMethod("getInputs");
                collectPositionedStacks(getInputs.invoke(cached), inputs, false);
            } catch (Exception ignored) {}

            if ((inputs.size() + outputs.size()) > before) {
                return true;
            }
            scanCachedObjectFields(cached, inputs, outputs);
            if ((inputs.size() + outputs.size()) > before) {
                return true;
            }
            if (debugExtractFailLogCount < 5) {
                debugExtractFailLogCount++;
                AdvanceDataMonitor.LOG.debug(
                    "[WebAE] arecipes slot present but stack extraction empty: cachedClass={} index={} arecipesSize={}",
                    cached.getClass()
                        .getName(),
                    index,
                    arecipes.size());
            }
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Reflect cached recipe fields when getter-based extraction yields nothing. */
    private static void scanCachedObjectFields(Object cached, List<RecipeDto.ItemEntry> inputs,
        List<RecipeDto.ItemEntry> outputs) {
        if (cached == null) return;
        Class<?> cls = cached.getClass();
        while (cls != null && cls != Object.class) {
            Field[] fields = cls.getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                Field field = fields[i];
                try {
                    field.setAccessible(true);
                    Object value = field.get(cached);
                    if (value == null) continue;
                    String name = field.getName()
                        .toLowerCase();
                    boolean outputField = name.contains("result") || name.contains("output");
                    if (value instanceof ItemStack) {
                        addEntry(outputField ? outputs : inputs, (ItemStack) value, false);
                    } else {
                        collectPositionedStacks(value, outputField ? outputs : inputs, false);
                    }
                } catch (Exception ignored) {}
            }
            cls = cls.getSuperclass();
        }
    }

    private static String getHandlerId(Object handler) {
        try {
            Method m = handler.getClass()
                .getMethod("getHandlerId");
            Object result = m.invoke(handler);
            if (result != null) {
                String id = result.toString();
                if (!id.isEmpty()) return id;
            }
        } catch (Exception ignored) {}
        return handler.getClass()
            .getName();
    }

    /**
     * Walk a {@code PositionedStack}, {@code ItemStack}, {@code PositionedStack[]},
     * or {@code List<PositionedStack>} value returned by an NEI handler method and
     * append each underlying {@link ItemStack} to {@code out}.
     *
     * @param value       the raw return value from the handler method
     * @param out         accumulator for item entries
     * @param dedupeInput when true, skip items already present (used for getOtherStacks)
     */
    @SuppressWarnings("unchecked")
    private static void collectPositionedStacks(Object value, List<RecipeDto.ItemEntry> out, boolean dedupeInput) {
        if (value == null) return;
        if (value instanceof ItemStack) {
            addEntry(out, (ItemStack) value, dedupeInput);
            return;
        }
        if (value.getClass()
            .isArray()) {
            Object[] arr = (Object[]) value;
            for (int i = 0; i < arr.length; i++) {
                ItemStack stack = positionedStackToItemStack(arr[i]);
                if (stack != null) addEntry(out, stack, dedupeInput);
            }
            return;
        }
        if (value instanceof Iterable) {
            Iterator<?> iter = ((Iterable<?>) value).iterator();
            while (iter.hasNext()) {
                Object next = iter.next();
                // Nested rows (e.g. shaped crafting grids as List<List<PositionedStack>>)
                if (next instanceof Iterable && !(next instanceof ItemStack)) {
                    collectPositionedStacks(next, out, dedupeInput);
                } else {
                    ItemStack stack = positionedStackToItemStack(next);
                    if (stack != null) addEntry(out, stack, dedupeInput);
                }
            }
            return;
        }
        // Last resort: maybe it's a single PositionedStack wrapper
        ItemStack stack = positionedStackToItemStack(value);
        if (stack != null) addEntry(out, stack, dedupeInput);
    }

    private static void addEntry(List<RecipeDto.ItemEntry> out, ItemStack stack, boolean dedupeInput) {
        if (stack == null) return;
        if (dedupeInput && containsItem(out, stack)) return;
        RecipeDto.ItemEntry entry = itemStackToEntry(stack);
        if (entry != null) out.add(entry);
    }

    /**
     * Reflect into a {@code codechicken.nei.PositionedStack} instance and extract
     * its underlying {@link ItemStack} via the {@code item} field. Falls back to the
     * legacy {@code stack} field name. Returns the input unchanged if it is already
     * an {@link ItemStack}, and returns {@code null} for null/unknown types.
     */
    private static ItemStack positionedStackToItemStack(Object positionedStack) {
        if (positionedStack == null) return null;
        if (positionedStack instanceof ItemStack) return (ItemStack) positionedStack;
        // GTNH PositionedStack: permutations must be generated before item is readable
        try {
            Method genPerm = positionedStack.getClass()
                .getMethod("generatePermutations");
            genPerm.invoke(positionedStack);
        } catch (Throwable ignored) {}
        try {
            Method setPerm = positionedStack.getClass()
                .getMethod("setPermutationToRender", int.class);
            setPerm.invoke(positionedStack, Integer.valueOf(0));
        } catch (Throwable ignored) {}
        try {
            Method getStack = positionedStack.getClass()
                .getMethod("getStack");
            Object stackObj = getStack.invoke(positionedStack);
            if (stackObj instanceof ItemStack) return (ItemStack) stackObj;
        } catch (Throwable ignored) {}
        ensurePositionedStackFields();
        Field itemField = positionedStackItemField;
        if (itemField != null) {
            try {
                Object obj = itemField.get(positionedStack);
                if (obj instanceof ItemStack) return (ItemStack) obj;
            } catch (Throwable ignored) {}
        }
        Field stackField = positionedStackStackField;
        if (stackField != null) {
            try {
                Object obj = stackField.get(positionedStack);
                if (obj instanceof ItemStack) return (ItemStack) obj;
            } catch (Throwable ignored) {}
        }
        Field itemsField = positionedStackItemsField;
        if (itemsField != null) {
            try {
                Object arr = itemsField.get(positionedStack);
                if (arr instanceof ItemStack[]) {
                    ItemStack[] stacks = (ItemStack[]) arr;
                    for (int i = 0; i < stacks.length; i++) {
                        if (stacks[i] != null && stacks[i].getItem() != null) {
                            return stacks[i].copy();
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    /**
     * Lazily resolve the {@code item} and {@code stack} fields on
     * {@code codechicken.nei.PositionedStack}. Cached for the lifetime of the JVM.
     */
    private static void ensurePositionedStackFields() {
        if (positionedStackResolved) return;
        synchronized (NeiRecipeCollector.class) {
            if (positionedStackResolved) return;
            try {
                Class<?> cls = Class.forName("codechicken.nei.PositionedStack");
                try {
                    positionedStackItemField = cls.getField("item");
                } catch (NoSuchFieldException e) {
                    positionedStackItemField = null;
                }
                try {
                    positionedStackStackField = cls.getField("stack");
                } catch (NoSuchFieldException e) {
                    positionedStackStackField = null;
                }
                try {
                    positionedStackItemsField = cls.getField("items");
                } catch (NoSuchFieldException e) {
                    positionedStackItemsField = null;
                }
            } catch (Throwable t) {
                if (Config.debugWebae) {
                    AdvanceDataMonitor.LOG.warn("[WebAE] PositionedStack class not resolved: {}", t.getMessage());
                }
                positionedStackItemField = null;
                positionedStackStackField = null;
                positionedStackItemsField = null;
            }
            positionedStackResolved = true;
        }
    }

    /**
     * Build a raw JSON representation of the recipe for frontend display.
     */
    private static String buildRawJson(Object handler, int index, String type) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"type\":\"")
                .append(type)
                .append("\",");
            sb.append("\"handlerId\":\"")
                .append(
                    escapeJson(
                        handler.getClass()
                            .getName()))
                .append("\",");
            sb.append("\"recipeIndex\":")
                .append(index)
                .append("}");
            return sb.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * Get the human-readable name of a recipe handler.
     */
    private static String getRecipeHandlerName(Object handler) {
        try {
            Method m = handler.getClass()
                .getMethod("getRecipeName");
            Object result = m.invoke(handler);
            return result != null ? result.toString()
                : handler.getClass()
                    .getSimpleName();
        } catch (Exception e) {
            return handler.getClass()
                .getSimpleName();
        }
    }

    /**
     * Get the number of recipes in a handler.
     */
    private static int getNumRecipes(Object handler) {
        try {
            Method m = handler.getClass()
                .getMethod("numRecipes");
            Object result = m.invoke(handler);
            if (result instanceof Integer) {
                return (Integer) result;
            }
        } catch (Exception e) {
            // Fallback: try to enumerate up to a reasonable limit
        }
        return 0;
    }

    /**
     * Sum {@code numRecipes()} across all handlers of the given type ("crafting" or "usage").
     */
    private static int sumNumRecipes(String type) {
        try {
            if ("crafting".equals(type)) {
                Class<?> guiCraftingRecipe = Class.forName("codechicken.nei.recipe.GuiCraftingRecipe");
                Field f = guiCraftingRecipe.getField("craftinghandlers");
                List<?> handlers = (List<?>) f.get(null);
                return sumNumRecipes(handlers);
            } else if ("usage".equals(type)) {
                Class<?> guiUsageRecipe = Class.forName("codechicken.nei.recipe.GuiUsageRecipe");
                Field f = guiUsageRecipe.getField("usagehandlers");
                List<?> handlers = (List<?>) f.get(null);
                return sumNumRecipes(handlers);
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private static int sumNumRecipes(List<?> handlers) {
        if (handlers == null) return 0;
        int total = 0;
        for (Object h : handlers) {
            total += getNumRecipes(h);
        }
        return total;
    }

    /**
     * Get total recipe count across all handlers.
     */
    public static int getTotalRecipeCount() {
        if (!isNeiAvailable()) return 0;
        return sumNumRecipes("crafting") + sumNumRecipes("usage");
    }

    /**
     * Collect a range of recipes (for batched upload progress reporting).
     *
     * @param startIdx start index (inclusive)
     * @param count    maximum number of recipes to collect
     * @return collected recipes
     */
    public static List<RecipeDto> collectRange(int startIdx, int count) {
        List<RecipeDto> all = collectAll();
        int end = Math.min(startIdx + count, all.size());
        List<RecipeDto> result = new ArrayList<RecipeDto>();
        for (int i = startIdx; i < end; i++) {
            result.add(all.get(i));
        }
        return result;
    }

    private static RecipeDto.ItemEntry itemStackToEntry(ItemStack stack) {
        return RecipeItemEntries.fromStack(stack);
    }

    private static boolean containsItem(List<RecipeDto.ItemEntry> items, ItemStack stack) {
        if (stack == null) return false;
        String registryName = net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem());
        if (registryName == null) return false;
        for (RecipeDto.ItemEntry entry : items) {
            if (registryName.equals(entry.registryName) && stack.getItemDamage() == entry.meta) {
                return true;
            }
        }
        return false;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"");
    }

    private static ItemStack stackFromItemId(String itemId) {
        if (itemId == null || itemId.isEmpty()) return null;
        String registry = itemId;
        int meta = 0;
        int lastColon = itemId.lastIndexOf(':');
        if (lastColon > 0 && lastColon < itemId.length() - 1) {
            try {
                meta = Integer.parseInt(itemId.substring(lastColon + 1));
                registry = itemId.substring(0, lastColon);
            } catch (NumberFormatException ignored) {
                registry = itemId;
                meta = 0;
            }
        }
        Object item = Item.itemRegistry.getObject(registry);
        if (!(item instanceof Item)) return null;
        return new ItemStack((Item) item, 1, meta);
    }

    private static void collectRecipesForStack(ItemStack stack, List<RecipeDto> all, Set<String> seen) {
        if (stack == null) return;
        try {
            Class<?> guiCrafting = Class.forName("codechicken.nei.recipe.GuiCraftingRecipe");
            java.lang.reflect.Method getCrafting = guiCrafting
                .getMethod("getCraftingHandlers", String.class, Object[].class);
            List<?> crafting = (List<?>) getCrafting.invoke(null, "item", new Object[] { stack.copy() });
            if (crafting != null) mergeHandlersIntoRecipes(crafting, all, seen, "crafting");
        } catch (Exception ignored) {}
        try {
            Class<?> guiUsage = Class.forName("codechicken.nei.recipe.GuiUsageRecipe");
            java.lang.reflect.Method getUsage = guiUsage.getMethod("getUsageHandlers", String.class, Object[].class);
            List<?> usage = (List<?>) getUsage.invoke(null, "item", new Object[] { stack.copy() });
            if (usage != null) mergeHandlersIntoRecipes(usage, all, seen, "usage");
        } catch (Exception ignored) {}
    }
}
