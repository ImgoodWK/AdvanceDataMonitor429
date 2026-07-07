package com.imgood.textech.webae.icon;

import java.util.ArrayDeque;
import java.util.Deque;

import net.minecraft.client.Minecraft;

import com.imgood.textech.AdvanceDataMonitor;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Rate-limits lazy single-icon render/upload so WebAE 404 backfill cannot flood the client main thread.
 */
@SideOnly(Side.CLIENT)
public final class IconLazyRenderQueue {

    private static final int MAX_QUEUE = 2048;
    /** Lazy 404 backfill renders per client tick (keeps main thread responsive in full GTNH). */
    private static final int LAZY_RENDERS_PER_TICK = 2;
    private static final IconLazyRenderQueue INSTANCE = new IconLazyRenderQueue();

    private final Deque<LazyTask> queue = new ArrayDeque<LazyTask>();

    private IconLazyRenderQueue() {}

    public static IconLazyRenderQueue instance() {
        return INSTANCE;
    }

    public void enqueue(String pack, String mode, IconItemEnumerator.StackTask task) {
        if (task == null) return;
        synchronized (this) {
            if (queue.size() >= MAX_QUEUE) {
                queue.pollFirst();
            }
            queue.offerLast(new LazyTask(pack, mode, task));
        }
    }

    public int pendingCount() {
        synchronized (this) {
            return queue.size();
        }
    }

    public void clear() {
        synchronized (this) {
            queue.clear();
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (IconRenderer.instance()
            .isRunning()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null) return;

        int budget = LAZY_RENDERS_PER_TICK;
        for (int i = 0; i < budget; i++) {
            LazyTask task = poll();
            if (task == null) break;
            try {
                String uuid = mc.thePlayer.getUniqueID()
                    .toString();
                IconRenderer.instance()
                    .renderAndUploadSingle(task.pack, uuid, task.mode, task.stackTask);
            } catch (Throwable t) {
                AdvanceDataMonitor.LOG.warn("[WebAE] Lazy icon render failed for {}", task.stackTask.itemId, t);
                IconRenderGuard.afterRender(mc);
            }
        }
    }

    private LazyTask poll() {
        synchronized (this) {
            return queue.pollFirst();
        }
    }

    private static final class LazyTask {

        final String pack;
        final String mode;
        final IconItemEnumerator.StackTask stackTask;

        LazyTask(String pack, String mode, IconItemEnumerator.StackTask stackTask) {
            this.pack = pack;
            this.mode = mode;
            this.stackTask = stackTask;
        }
    }
}
