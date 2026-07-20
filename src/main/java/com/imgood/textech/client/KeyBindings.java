package com.imgood.textech.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Keyboard;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.assistant.AssistantMonitorRegistry;
import com.imgood.textech.gui.guiscreen.GuiAIChat;
import com.imgood.textech.gui.guiscreen.GuiAdvancePlanner;
import com.imgood.textech.gui.guiscreen.GuiIconVerifyScreen;
import com.imgood.textech.gui.guiscreen.GuiMainAdvanceDataMonitor;
import com.imgood.textech.items.ItemAdvancePlanner;
import com.imgood.textech.network.packet.PacketMonitorRecord;
import com.imgood.textech.tileentity.TileEntityAdvanceDataMonitor;
import com.imgood.textech.webae.dto.RecipeDto;
import com.imgood.textech.webae.icon.IconExportScope;
import com.imgood.textech.webae.icon.IconLocalStore;
import com.imgood.textech.webae.icon.IconRenderMode;
import com.imgood.textech.webae.icon.IconRenderer;
import com.imgood.textech.webae.icon.IconStore;
import com.imgood.textech.webae.network.RecipeUploadBatcher;
import com.imgood.textech.webae.network.RecipeUploadBatcher.Batch;
import com.imgood.textech.webae.network.RecipeUploadThrottler;
import com.imgood.textech.webae.recipe.GameRecipeCollector;
import com.imgood.textech.webae.recipe.NeiRecipeCollector;
import com.imgood.textech.webae.recipe.RecipeLocalExporter;
import com.imgood.textech.webae.recipe.RecipeSnapshotCollector;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Manages all AdvanceDataMonitor key bindings registered in the Controls menu.
 *
 * <p>
 * Recipe/icon upload is triggered via {@code /admweb} commands through
 * {@link com.imgood.textech.webae.network.PacketWebUploadTrigger}.
 * </p>
 */
public class KeyBindings {

    private static IconExportScope pendingIconExportScope = IconExportScope.ALL;
    private static final List<String> pendingIconExportItemIds = new ArrayList<String>();

    public static void setPendingIconExportScope(IconExportScope scope, List<String> itemIds) {
        pendingIconExportScope = scope != null ? scope : IconExportScope.ALL;
        pendingIconExportItemIds.clear();
        if (itemIds != null) {
            pendingIconExportItemIds.addAll(itemIds);
        }
    }

    private static final int MONITOR_SEARCH_RADIUS = 32;
    private static final ResourceLocation MONITOR_MAIN_BACKGROUND = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/gui/background_AdvanceDataMonitor_Main.png");

    public final KeyBinding openAiChat = new KeyBinding(
        "key.textech.open_ai_chat",
        Keyboard.KEY_O,
        "key.categories.textech");

    public final KeyBinding openPlanner = new KeyBinding(
        "key.textech.open_planner",
        Keyboard.KEY_P,
        "key.categories.textech");

    public final KeyBinding toggleHud = new KeyBinding(
        "key.textech.toggle_hud",
        Keyboard.KEY_H,
        "key.categories.textech");

    public final KeyBinding openMonitorAi = new KeyBinding(
        "key.textech.open_monitor_ai",
        Keyboard.KEY_NONE,
        "key.categories.textech");

    public final KeyBinding captureScreenshot = new KeyBinding(
        "key.textech.capture_screenshot",
        Keyboard.KEY_F10,
        "key.categories.textech");

    private boolean screenshotKeyWasDown;

    public void register() {
        ClientRegistry.registerKeyBinding(openAiChat);
        ClientRegistry.registerKeyBinding(openPlanner);
        ClientRegistry.registerKeyBinding(toggleHud);
        ClientRegistry.registerKeyBinding(openMonitorAi);
        ClientRegistry.registerKeyBinding(captureScreenshot);
    }

    /** Raw edge detection remains active while a GuiScreen owns keyboard input. */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        int keyCode = captureScreenshot.getKeyCode();
        boolean down = keyCode > Keyboard.KEY_NONE && Keyboard.isKeyDown(keyCode);
        if (down && !screenshotKeyWasDown) {
            com.imgood.textech.client.screenshot.ClientScreenshotService.instance()
                .capture();
        }
        screenshotKeyWasDown = down;
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (openAiChat.isPressed()) {
            openAiChatGui();
        } else if (openPlanner.isPressed()) {
            openPlannerGui();
        } else if (toggleHud.isPressed()) {
            togglePlannerHud();
        } else if (openMonitorAi.isPressed()) {
            openNearbyMonitorAiGui();
        }
    }

    private void openAiChatGui() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        AdvanceDataMonitor.LOG.info("[TeXTech] AI Chat key pressed");
        mc.displayGuiScreen(new GuiAIChat(mc.currentScreen));
    }

    private void openNearbyMonitorAiGui() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        AdvanceDataMonitor.LOG.info("[TeXTech] Nearby monitor AI key pressed");
        TileEntityAdvanceDataMonitor monitor = AssistantMonitorRegistry
            .findNearest(mc.thePlayer, MONITOR_SEARCH_RADIUS);
        if (monitor == null) {
            notifyPlayer(I18n.format("adm.error.no_nearby_monitor"));
            return;
        }
        AdvanceDataMonitor.ADMCHANEL
            .sendToServer(new PacketMonitorRecord(monitor.xCoord, monitor.yCoord, monitor.zCoord));
        GuiMainAdvanceDataMonitor monitorGui = new GuiMainAdvanceDataMonitor(mc.thePlayer, mc.theWorld, monitor);
        monitorGui.setPosition(-10, 30);
        monitorGui.setSize(470, 270);
        monitorGui.setStretch(false);
        monitorGui.setBackgroundTexture(MONITOR_MAIN_BACKGROUND);
        mc.displayGuiScreen(new GuiAIChat(monitorGui));
    }

    private void openPlannerGui() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        AdvanceDataMonitor.LOG.info("[TeXTech] Planner key pressed");
        // Find first planner in inventory
        for (int i = 0; i < mc.thePlayer.inventory.getSizeInventory(); i++) {
            net.minecraft.item.ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemAdvancePlanner) {
                mc.displayGuiScreen(new GuiAdvancePlanner(stack, mc.thePlayer));
                return;
            }
        }
        notifyPlayer("No Advance Planner found in inventory.");
    }

    private void togglePlannerHud() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        AdvanceDataMonitor.LOG.info("[TeXTech] HUD toggle key pressed");
        for (int i = 0; i < mc.thePlayer.inventory.getSizeInventory(); i++) {
            net.minecraft.item.ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemAdvancePlanner) {
                boolean wasEnabled = ItemAdvancePlanner.isHudEnabled(stack);
                ItemAdvancePlanner.setHudEnabled(stack, !wasEnabled);
                notifyPlayer("Planner HUD: " + (!wasEnabled ? "ON" : "OFF"));
                return;
            }
        }
        notifyPlayer("No Advance Planner found in inventory.");
    }

    public static void uploadNeiRecipes() {
        uploadNeiRecipes("full", null);
    }

    public static void uploadNeiRecipes(String scope, List<String> snapshotItemIds) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        if (!Config.webConsoleEnabled) {
            notifyStatic(mc, "Web Console is disabled. Enable it in config.");
            return;
        }
        final String playerUuid = mc.thePlayer.getUniqueID()
            .toString();
        final boolean deepScan = "deep".equalsIgnoreCase(scope);
        final boolean snapshot = "snapshot".equalsIgnoreCase(scope);
        final List<String> snapshotIds = snapshotItemIds;

        new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    final Map<String, RecipeDto> merged = new LinkedHashMap<String, RecipeDto>();
                    final AtomicReference<List<RecipeDto>> neiRef = new AtomicReference<List<RecipeDto>>(
                        new ArrayList<RecipeDto>());
                    final CountDownLatch neiDone = new CountDownLatch(1);

                    scheduleOnClientThread(new Runnable() {

                        @Override
                        public void run() {
                            try {
                                List<RecipeDto> nei;
                                if (snapshot && snapshotIds != null && !snapshotIds.isEmpty()) {
                                    nei = RecipeSnapshotCollector.collectForItems(snapshotIds);
                                } else {
                                    nei = NeiRecipeCollector.collectAll(deepScan);
                                }
                                neiRef.set(nei);
                            } finally {
                                neiDone.countDown();
                            }
                        }
                    });
                    neiDone.await(45, TimeUnit.MINUTES);
                    mergeInto(merged, neiRef.get(), true);

                    List<RecipeDto> game = GameRecipeCollector.collectAll();
                    mergeInto(merged, game, false);

                    scheduleOnClientThread(new Runnable() {

                        @Override
                        public void run() {
                            List<RecipeDto> recipes = new ArrayList<RecipeDto>(merged.values());
                            if (recipes.isEmpty()) {
                                notifyStatic(mc, "No recipes collected.");
                                return;
                            }
                            java.io.File localExport = RecipeLocalExporter.exportRecipes(recipes);
                            Gson gson = new GsonBuilder().serializeNulls()
                                .create();
                            List<Batch> batches = RecipeUploadBatcher.buildBatches(recipes, gson);
                            if (batches.isEmpty()) return;
                            RecipeUploadThrottler.instance()
                                .startUpload(playerUuid, batches, scope);
                            StringBuilder msg = new StringBuilder();
                            msg.append("Uploading ")
                                .append(recipes.size())
                                .append(" merged recipes (")
                                .append(scope)
                                .append(") in ")
                                .append(batches.size())
                                .append(" batches");
                            if (localExport != null) {
                                msg.append("; local export: ")
                                    .append(localExport.getAbsolutePath());
                            }
                            msg.append('.');
                            notifyStatic(mc, msg.toString());
                        }
                    });
                } catch (Throwable t) {
                    AdvanceDataMonitor.LOG.error("[WebAE] Recipe upload failed", t);
                }
            }
        }, "WebAE-RecipeCollector").start();
    }

    private static void mergeInto(Map<String, RecipeDto> merged, List<RecipeDto> recipes, boolean neiPriority) {
        if (recipes == null) return;
        for (RecipeDto dto : recipes) {
            if (dto == null) continue;
            String key = dto.handlerId + ":" + dto.recipeIndex;
            if (neiPriority || !merged.containsKey(key)) {
                merged.put(key, dto);
            }
        }
    }

    private static void scheduleOnClientThread(Runnable runnable) {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            mc.func_152344_a(runnable);
        } catch (Exception e) {
            runnable.run();
        }
    }

    public static void triggerIconUpload(String packName) {
        triggerIconUpload(packName, IconRenderMode.NEI.getId());
    }

    public static void triggerIconUpload(String packName, String renderModeId) {
        triggerIconUpload(packName, renderModeId, null, null);
    }

    public static void triggerIconUpload(String packName, String renderModeId, IconExportScope scope,
        List<String> itemIds) {
        triggerIconUpload(packName, renderModeId, scope, itemIds, false);
    }

    public static void triggerIconUpload(String packName, String renderModeId, IconExportScope scope,
        List<String> itemIds, boolean localOnly) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        if (!Config.webConsoleEnabled) {
            notifyStatic(mc, "Web Console is disabled. Enable it in config.");
            return;
        }
        if (!Config.webIconCacheEnabled) {
            notifyStatic(mc, "Item icon cache is disabled in config.");
            return;
        }
        if (!localOnly && !Config.webIconUploadEnabled) {
            notifyStatic(mc, "Icon upload is disabled in config.");
            return;
        }
        if (IconRenderer.instance()
            .isRunning()) {
            notifyStatic(mc, "Icon rendering already in progress, please wait...");
            return;
        }
        if (!IconStore.isValidPackName(packName)) {
            notifyStatic(mc, "Invalid pack name: " + packName);
            return;
        }
        // Active path is nei-only; renderModeId arg ignored (kept for packet/API signature).
        String effectiveMode = IconRenderMode.NEI.getId();
        String playerUuid = mc.thePlayer.getUniqueID()
            .toString();
        IconExportScope effectiveScope = scope != null ? scope : pendingIconExportScope;
        List<String> effectiveIds = itemIds != null ? new ArrayList<String>(itemIds)
            : new ArrayList<String>(pendingIconExportItemIds);
        AdvanceDataMonitor.LOG.info(
            "[WebAE] Icon {} triggered: pack='{}' mode='{}' scope='{}' ids={}",
            localOnly ? "local export" : "upload",
            packName,
            effectiveMode,
            effectiveScope.getId(),
            effectiveIds.size());
        if (localOnly) {
            notifyStatic(mc, I18n.format("adm.webconsole.icons.local_started", packName));
        } else {
            notifyStatic(mc, I18n.format("adm.webconsole.icons.uploading_started_mode", packName, effectiveMode));
        }
        IconRenderer.instance()
            .start(packName, playerUuid, effectiveMode, effectiveScope, effectiveIds, localOnly);
        pendingIconExportScope = IconExportScope.ALL;
        pendingIconExportItemIds.clear();
    }

    /** Pull server icons into {@code TeXTech/WebAE/icons-local/} (copy if local, else wait for zip packets). */
    public static void triggerIconPull(String packName) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        if (!IconStore.isValidPackName(packName)) {
            notifyStatic(mc, "Invalid pack name: " + packName);
            return;
        }
        int copied = IconLocalStore.tryCopyFromLocalServerIcons(mc.mcDataDir, packName);
        if (copied >= 0) {
            java.io.File dest = IconLocalStore.packModeDir(mc.mcDataDir, packName);
            notifyStatic(
                mc,
                I18n.format("adm.webconsole.icons.pull_local_done", Integer.valueOf(copied), dest.getAbsolutePath()));
            return;
        }
        notifyStatic(mc, I18n.format("adm.webconsole.icons.pull_waiting", packName));
    }

    public static void openIconVerify(String packName, String itemId) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || itemId == null || itemId.isEmpty()) return;
        mc.displayGuiScreen(new GuiIconVerifyScreen(itemId, packName));
    }

    private static void notifyStatic(Minecraft mc, String text) {
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText("[WebAE] " + text));
        }
    }

    private void notifyPlayer(String text) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText("[TeXTech] " + text));
        }
    }
}
