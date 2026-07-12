package com.imgood.textech.webae.icon;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.xml.bind.DatatypeConverter;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.gui.guiscreen.GuiIconExportScreen;
import com.imgood.textech.webae.network.PacketWebIconUpload;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Client-side icon cache builder for WebAE.
 *
 * <p>
 * Bulk export runs inside {@link GuiIconExportScreen} using NESQL-style per-icon FBO
 * ({@code GuiContainerManager.drawItem}) via {@link IconExportResolver}. Fluids keep the mod
 * special path. Upload is spread across client ticks. Legacy {@link IconGridExporter} is retained
 * but not used by the active path.
 * </p>
 */
@SideOnly(Side.CLIENT)
public class IconRenderer {

    public static final int ICON_SIZE = 32;
    /** @deprecated use {@link IconItemId#FLUID_PREFIX} */
    public static final String FLUID_ID_PREFIX = IconItemId.FLUID_PREFIX;

    private static final int MAX_CHUNK_BYTES = 28000;
    private static final IconRenderer INSTANCE = new IconRenderer();

    private enum Phase {
        IDLE,
        RENDERING_GUI,
        UPLOADING
    }

    private Phase phase = Phase.IDLE;
    private final List<Task> pending = new ArrayList<Task>();
    private final Map<String, String> bundle = new LinkedHashMap<String, String>();
    private final IconAtlasSampler atlasSampler = new IconAtlasSampler();
    private final IconGlFallback glFallback = new IconGlFallback();
    private final IconExportResolver exportResolver = new IconExportResolver(atlasSampler, glFallback);
    /** @deprecated archived; active bulk path no longer uses grid FBO */
    @Deprecated
    private final IconGridExporter gridExporter = new IconGridExporter(exportResolver);
    private final IconUploadProgress progress = new IconUploadProgress();

    private int currentIndex = 0;
    private String packName = "default";
    private String playerUuid = "";
    private IconRenderMode renderMode = IconRenderMode.NEI;
    private IconRenderStrategy strategy = IconRenderStrategies.get(IconRenderMode.NEI);
    private final List<IconRenderMode> modeQueue = new ArrayList<IconRenderMode>();
    private int modeQueueIndex = 0;
    private boolean uploadAllModes = false;
    private int skippedNoIcon = 0;
    private IconExportScope exportScope = IconExportScope.ALL;
    private List<String> scopedItemIds = new ArrayList<String>();

    private List<byte[]> uploadChunks = new ArrayList<byte[]>();
    private int uploadChunkIndex = 0;
    private int uploadTotalChunks = 0;
    private int lastModeIconCount = 0;
    private int renderContextGlCount = 0;
    private int renderContextNesqlCount = 0;
    private int renderContextVanillaCount = 0;
    private int renderContextAtlasCount = 0;
    private int renderContextBlockCount = 0;
    private int renderContextEntityCount = 0;
    private int renderContextPlaceholderCount = 0;

    private IconRenderer() {}

    public static IconRenderer instance() {
        return INSTANCE;
    }

    /** @deprecated archived grid exporter; kept for API compatibility with export-complete callback */
    @Deprecated
    public IconGridExporter getGridExporter() {
        return gridExporter;
    }

    public IconExportResolver getExportResolver() {
        return exportResolver;
    }

    public boolean isRunning() {
        return phase != Phase.IDLE;
    }

    public boolean isUploadAllModes() {
        return uploadAllModes;
    }

    public IconRenderMode getCurrentMode() {
        return renderMode;
    }

    public String getCurrentPackName() {
        return packName;
    }

    public int getPendingCount() {
        int count = 0;
        for (Task task : pending) {
            if (task.stack != null) count++;
        }
        return count;
    }

    /** Map linear grid index to pending list index (skips fluid-only tasks). */
    private int pendingIndexForGridItem(int gridIndex) {
        int seen = 0;
        for (int i = 0; i < pending.size(); i++) {
            if (pending.get(i).stack == null) continue;
            if (seen == gridIndex) return i;
            seen++;
        }
        return pending.size();
    }

    public List<IconItemEnumerator.StackTask> getPendingSubList(int start, int end) {
        List<IconItemEnumerator.StackTask> out = new ArrayList<IconItemEnumerator.StackTask>();
        for (int gridIdx = start; gridIdx < end; gridIdx++) {
            int pendingIdx = pendingIndexForGridItem(gridIdx);
            if (pendingIdx >= pending.size()) break;
            Task task = pending.get(pendingIdx);
            if (task.stack != null) {
                out.add(new IconItemEnumerator.StackTask(task.itemId, task.stack, task.fluid));
            }
        }
        return out;
    }

    public void mergeRenderedIcons(Map<String, byte[]> rendered) {
        if (rendered == null) return;
        for (Map.Entry<String, byte[]> e : rendered.entrySet()) {
            if (e.getValue() != null && e.getValue().length > 0) {
                bundle.put(e.getKey(), DatatypeConverter.printBase64Binary(e.getValue()));
            }
        }
    }

    public void start(String packName, String playerUuid, String renderModeId) {
        start(packName, playerUuid, renderModeId, IconExportScope.ALL, null);
    }

    public void start(String packName, String playerUuid, String renderModeId, IconExportScope scope,
        List<String> explicitItemIds) {
        if (phase != Phase.IDLE) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Icon render already in progress");
            return;
        }
        IconLazyRenderQueue.instance()
            .clear();
        this.packName = (packName != null && !packName.isEmpty()) ? packName : "default";
        this.playerUuid = playerUuid != null ? playerUuid : "";
        this.exportScope = scope != null ? scope : IconExportScope.ALL;
        this.scopedItemIds = explicitItemIds != null ? new ArrayList<String>(explicitItemIds) : new ArrayList<String>();
        this.modeQueue.clear();
        this.modeQueueIndex = 0;
        this.uploadAllModes = false;
        // Active path is nei-only; ignore renderModeId (incl. legacy "all").
        this.renderMode = IconRenderMode.NEI;
        this.modeQueue.add(IconRenderMode.NEI);
        this.strategy = IconRenderStrategies.get(this.renderMode);
        resetSessionCounters();
        buildPendingTasks();
        if (pending.isEmpty()) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Icon export has no pending tasks (scope={})", exportScope.getId());
            return;
        }
        this.phase = Phase.RENDERING_GUI;
        progress.onModeStart(this.packName, this.renderMode, modeQueueIndex + 1, modeQueue.size(), pending.size());
        AdvanceDataMonitor.LOG.info(
            "[WebAE] Icon render started: pack='{}' mode='{}' scope='{}' queue={}/{} ({} tasks, {} skipped fluids)",
            this.packName,
            this.renderMode.getId(),
            exportScope.getId(),
            modeQueueIndex + 1,
            modeQueue.size(),
            pending.size(),
            skippedNoIcon);
        Minecraft mc = Minecraft.getMinecraft();
        mc.displayGuiScreen(new GuiIconExportScreen(this));
    }

    /** Render and upload a single icon (lazy-load / on-demand). */
    public void renderAndUploadSingle(String pack, String playerUuid, String modeId,
        IconItemEnumerator.StackTask task) {
        if (task == null || phase != Phase.IDLE) return;
        Minecraft mc = Minecraft.getMinecraft();
        try {
            byte[] png = renderPngBytes(modeId, task);
            if (png == null || png.length == 0) return;
            Map<String, String> single = new LinkedHashMap<String, String>();
            single.put(task.itemId, DatatypeConverter.printBase64Binary(png));
            this.packName = pack;
            this.playerUuid = playerUuid;
            this.renderMode = IconRenderMode.NEI;
            uploadSingleBundle(single);
        } finally {
            IconRenderGuard.afterRender(mc);
        }
    }

    /** Render a single icon to PNG bytes without uploading (direct HTTP capture). */
    public byte[] renderPngBytes(String modeId, String itemId) {
        IconItemEnumerator.StackTask task = IconItemEnumerator.resolveSingle(itemId);
        return renderPngBytes(modeId, task);
    }

    public byte[] renderPngBytes(String modeId, IconItemEnumerator.StackTask task) {
        if (task == null) return null;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return null;
        try {
            if (task.fluid != null) {
                byte[] png = glFallback.renderRegistryFluidIcon(mc, task.fluid);
                if (IconAtlasSampler.isPngBlank(png)) {
                    return createPlaceholderPng(task.itemId);
                }
                return png;
            }
            if (task.stack != null) {
                return exportResolver.resolve(mc, task.stack, task.itemId, null).png;
            }
            return createPlaceholderPng(task.itemId);
        } catch (Throwable t) {
            AdvanceDataMonitor.LOG.warn("[WebAE] renderPngBytes failed for {}", task.itemId, t);
            return null;
        }
    }

    /** @param exporter unused; kept for call-site compatibility with archived grid path */
    public void onExportComplete(IconGridExporter exporter) {
        syncResolverCounts();
        renderFluidsAfterItems();
        progress.onRenderProgress(renderMode, modeQueueIndex + 1, modeQueue.size(), pending.size(), pending.size());
        progress.onRenderComplete(packName, renderMode, bundle.size());
        AdvanceDataMonitor.LOG.info(
            "[WebAE] Icon NESQL export complete mode={}: {} icons ({} nesql, {} fluid-gl, {} placeholders)",
            renderMode.getId(),
            bundle.size(),
            renderContextNesqlCount,
            renderContextGlCount,
            renderContextPlaceholderCount);
        beginAsyncUpload();
    }

    private void syncResolverCounts() {
        renderContextGlCount = exportResolver.getGlCount();
        renderContextNesqlCount = exportResolver.getNesqlCount();
        renderContextVanillaCount = exportResolver.getVanillaCount();
        renderContextAtlasCount = exportResolver.getAtlasCount();
        renderContextBlockCount = exportResolver.getBlockCount();
        renderContextEntityCount = exportResolver.getEntityCount();
        renderContextPlaceholderCount = exportResolver.getPlaceholderCount();
    }

    public void onExportCancelled() {
        AdvanceDataMonitor.LOG.warn("[WebAE] Icon export cancelled by user");
        resetToIdle();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (phase != Phase.UPLOADING) return;
        if (event.phase != TickEvent.Phase.END) return;
        try {
            uploadBatch();
        } catch (Throwable t) {
            AdvanceDataMonitor.LOG.error("[WebAE] Icon upload batch failed", t);
            resetToIdle();
        }
    }

    /** Registry fluid icons after item stacks (mod special path, not NESQL drawItem). */
    private void renderFluidsAfterItems() {
        Minecraft mc = Minecraft.getMinecraft();
        for (Task task : pending) {
            if (task.fluid == null) continue;
            if (bundle.containsKey(task.itemId)) continue;
            byte[] png = glFallback.renderRegistryFluidIcon(mc, task.fluid);
            if (IconAtlasSampler.isPngBlank(png)) {
                png = createPlaceholderPng(task.itemId);
                renderContextPlaceholderCount++;
            } else {
                renderContextGlCount++;
            }
            if (png != null && png.length > 0) {
                bundle.put(task.itemId, DatatypeConverter.printBase64Binary(png));
            }
        }
    }

    private void uploadSingleBundle(Map<String, String> singleBundle) {
        com.google.gson.Gson gson = new com.google.gson.GsonBuilder().create();
        String json = gson.toJson(singleBundle);
        byte[] full = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int total = (full.length + MAX_CHUNK_BYTES - 1) / MAX_CHUNK_BYTES;
        for (int i = 0; i < total; i++) {
            int off = i * MAX_CHUNK_BYTES;
            int len = Math.min(MAX_CHUNK_BYTES, full.length - off);
            byte[] chunk = new byte[len];
            System.arraycopy(full, off, chunk, 0, len);
            AdvanceDataMonitor.ADMCHANEL.sendToServer(
                new PacketWebIconUpload(
                    i == 0,
                    i == total - 1,
                    i,
                    total,
                    packName,
                    renderMode.getId(),
                    playerUuid,
                    chunk));
        }
    }

    private void resetSessionCounters() {
        this.pending.clear();
        this.bundle.clear();
        this.uploadChunks.clear();
        this.uploadChunkIndex = 0;
        this.uploadTotalChunks = 0;
        this.currentIndex = 0;
        this.skippedNoIcon = 0;
        this.renderContextGlCount = 0;
        this.renderContextNesqlCount = 0;
        this.renderContextVanillaCount = 0;
        this.renderContextAtlasCount = 0;
        this.renderContextBlockCount = 0;
        this.renderContextEntityCount = 0;
        this.renderContextPlaceholderCount = 0;
        this.atlasSampler.reset();
        this.glFallback.reset();
        this.gridExporter.reset();
        this.exportResolver.reset();
    }

    private void buildPendingTasks() {
        int items = 0;
        for (IconItemEnumerator.StackTask task : IconItemEnumerator.collectForScope(exportScope, scopedItemIds)) {
            pending.add(new Task(task.itemId, task.stack, task.fluid));
            items++;
        }
        if (exportScope != IconExportScope.ALL) {
            AdvanceDataMonitor.LOG.info(
                "[WebAE] Icon scoped task queue: {} items (scope={}, mode={})",
                items,
                exportScope.getId(),
                renderMode.getId());
            return;
        }
        int fluids = 0;
        Map<String, Fluid> fluidMap = FluidRegistry.getRegisteredFluids();
        if (fluidMap != null) {
            Iterator<Map.Entry<String, Fluid>> fit = fluidMap.entrySet()
                .iterator();
            while (fit.hasNext()) {
                Map.Entry<String, Fluid> e = fit.next();
                Fluid fluid = e.getValue();
                if (fluid == null) continue;
                String name = fluid.getName();
                if (name == null || name.isEmpty()) continue;
                if (fluidStillIcon(fluid) == null) {
                    skippedNoIcon++;
                    continue;
                }
                pending.add(new Task(IconItemId.FLUID_PREFIX + name, null, fluid));
                fluids++;
            }
        }
        AdvanceDataMonitor.LOG
            .info("[WebAE] Icon task queue: {} items + {} fluids (mode={})", items, fluids, renderMode.getId());
    }

    private void beginAsyncUpload() {
        if (bundle.isEmpty()) {
            AdvanceDataMonitor.LOG
                .warn("[WebAE] Icon bundle is empty for mode {}; nothing to upload", renderMode.getId());
            onModeFinished(false);
            return;
        }
        com.google.gson.Gson gson = new com.google.gson.GsonBuilder().create();
        String json = gson.toJson(bundle);
        byte[] full = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        uploadChunks.clear();
        uploadTotalChunks = (full.length + MAX_CHUNK_BYTES - 1) / MAX_CHUNK_BYTES;
        for (int i = 0; i < uploadTotalChunks; i++) {
            int off = i * MAX_CHUNK_BYTES;
            int len = Math.min(MAX_CHUNK_BYTES, full.length - off);
            byte[] chunk = new byte[len];
            System.arraycopy(full, off, chunk, 0, len);
            uploadChunks.add(chunk);
        }
        uploadChunkIndex = 0;
        lastModeIconCount = bundle.size();
        bundle.clear();
        phase = Phase.UPLOADING;
        AdvanceDataMonitor.LOG.info(
            "[WebAE] Queued icon upload mode={}: {} bytes in {} chunks (async)",
            renderMode.getId(),
            full.length,
            uploadTotalChunks);
    }

    private void uploadBatch() {
        if (uploadChunks.isEmpty()) {
            onModeFinished(true);
            return;
        }
        int sent = 0;
        while (uploadChunkIndex < uploadTotalChunks && sent < Config.webIconUploadChunksPerTick) {
            byte[] chunk = uploadChunks.get(uploadChunkIndex);
            boolean isStart = uploadChunkIndex == 0;
            boolean isEnd = uploadChunkIndex == uploadTotalChunks - 1;
            AdvanceDataMonitor.ADMCHANEL.sendToServer(
                new PacketWebIconUpload(
                    isStart,
                    isEnd,
                    uploadChunkIndex,
                    uploadTotalChunks,
                    packName,
                    renderMode.getId(),
                    playerUuid,
                    chunk));
            uploadChunkIndex++;
            sent++;
            progress.onUploadChunk(renderMode, uploadChunkIndex, uploadTotalChunks);
        }
        if (uploadChunkIndex >= uploadTotalChunks) {
            uploadChunks.clear();
            onModeFinished(true);
        }
    }

    private void onModeFinished(boolean advanceQueue) {
        if (!advanceQueue) {
            resetToIdle();
            return;
        }
        progress.onModeComplete(packName, renderMode, lastModeIconCount);
        modeQueueIndex++;
        if (modeQueueIndex < modeQueue.size()) {
            renderMode = modeQueue.get(modeQueueIndex);
            strategy = IconRenderStrategies.get(renderMode);
            resetSessionCounters();
            buildPendingTasks();
            phase = Phase.RENDERING_GUI;
            progress.onModeStart(packName, renderMode, modeQueueIndex + 1, modeQueue.size(), pending.size());
            AdvanceDataMonitor.LOG.info(
                "[WebAE] Starting next icon mode {}/{}: {}",
                modeQueueIndex + 1,
                modeQueue.size(),
                renderMode.getId());
            Minecraft.getMinecraft()
                .displayGuiScreen(new GuiIconExportScreen(this));
            return;
        }
        AdvanceDataMonitor.LOG
            .info("[WebAE] Icon export session complete pack='{}' ({} mode(s))", packName, modeQueue.size());
        if (uploadAllModes) {
            progress.onSessionComplete(packName, modeQueue.size());
        }
        resetToIdle();
    }

    private void resetToIdle() {
        phase = Phase.IDLE;
        uploadChunks.clear();
        uploadChunkIndex = 0;
        uploadTotalChunks = 0;
        modeQueue.clear();
        modeQueueIndex = 0;
        uploadAllModes = false;
        exportScope = IconExportScope.ALL;
        scopedItemIds.clear();
        glFallback.reset();
        gridExporter.reset();
        exportResolver.reset();
    }

    private static IIcon fluidStillIcon(Fluid fluid) {
        try {
            IIcon icon = fluid.getStillIcon();
            if (icon != null) return icon;
        } catch (Throwable ignored) {}
        try {
            return fluid.getIcon();
        } catch (Throwable ignored) {
            return null;
        }
    }

    static byte[] createPlaceholderPng(String itemId) {
        BufferedImage img = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(32, 32, 40, 255));
        g.fillRect(0, 0, ICON_SIZE, ICON_SIZE);
        g.setColor(new Color(180, 180, 200, 255));
        g.setFont(new Font("SansSerif", Font.BOLD, 6));
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        String label = itemId;
        if (label.length() > 10) {
            int colon = label.indexOf(':');
            if (colon >= 0 && colon < label.length() - 1) {
                label = label.substring(colon + 1);
            }
            if (label.length() > 8) {
                label = label.substring(0, 7) + "\u2026";
            }
        }
        int tw = g.getFontMetrics()
            .stringWidth(label);
        g.drawString(label, Math.max(1, (ICON_SIZE - tw) / 2), ICON_SIZE / 2 + 3);
        g.dispose();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(512);
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    private static final class Task {

        final String itemId;
        final ItemStack stack;
        final Fluid fluid;

        Task(String itemId, ItemStack stack, Fluid fluid) {
            this.itemId = itemId;
            this.stack = stack;
            this.fluid = fluid;
        }
    }
}
