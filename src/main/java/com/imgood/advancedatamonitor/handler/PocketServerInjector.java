package com.imgood.advancedatamonitor.handler;

import java.lang.reflect.Field;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerOpenContainerEvent;

import com.imgood.advancedatamonitor.items.ItemDimensionalPocket;
import com.imgood.advancedatamonitor.network.packet.PacketPocketSync;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Server-side counterpart of PocketOverlayHandler. When a player opens any
 * Container and has a pocket in their inventory with the switch enabled, this
 * injects the same number of extension slots into the server's openContainer
 * so vanilla windowClick addressing stays consistent between client and
 * server. Slot backends are PocketInventory bound to the player's PocketState,
 * so item movement on these slots persists to the player JSON file.
 *
 * On container close the injected slots are naturally discarded with the
 * openContainer, and PocketStore has already been saving on each mutation.
 */
public class PocketServerInjector {

    public static final PocketServerInjector INSTANCE = new PocketServerInjector();

    private PocketServerInjector() {}

    @SubscribeEvent
    public void onPlayerOpenContainer(PlayerOpenContainerEvent event) {
        // This event fires on the server when a player opens a container.
        EntityPlayer player = event.entityPlayer;
        if (player == null || player.worldObj == null || player.worldObj.isRemote) return;
        if (!(player instanceof EntityPlayerMP)) return;
        EntityPlayerMP mp = (EntityPlayerMP) player;
        Container container = mp.openContainer;
        if (container == null) return;
        if (!ItemDimensionalPocket.hasPocketInInventory(mp)) return;
        PocketState state = PocketStore.instance()
            .getOrCreate(mp);
        if (!state.isEnabled()) return;
        inject(mp, container, state);
    }

    private void inject(EntityPlayerMP player, Container container, PocketState state) {
        try {
            List<Slot> slots = (List<Slot>) getField(container.getClass(), "inventorySlots").get(container);
            if (slots == null) return;
            // Avoid double-injection if this container was already handled.
            for (Slot s : slots) {
                if (s instanceof SlotPocketServer) return;
            }
            String uuid = player.getUniqueID()
                .toString();
            PocketInventory inv = new PocketInventory(state, uuid, true);
            int slotsPerPage = state.getSlotsPerPage();
            java.lang.reflect.Method addSlot = Container.class.getDeclaredMethod("addSlotToContainer", Slot.class);
            addSlot.setAccessible(true);
            for (int i = 0; i < slotsPerPage; i++) {
                addSlot.invoke(container, new SlotPocketServer(inv, i, 0, 0));
            }
            // Push a fresh sync so the client mirror matches the just-opened container.
            com.imgood.advancedatamonitor.AdvanceDataMonitor.ADMCHANEL
                .sendTo(PacketPocketSync.fullState(state), player);
        } catch (Throwable t) {
            com.imgood.advancedatamonitor.AdvanceDataMonitor.LOG
                .error("PocketServerInjector failed to inject slots", t);
        }
    }

    private static Field getField(Class<?> clazz, String name) {
        Class<?> c = clazz;
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    /**
     * Server-side slot backed by PocketInventory. Position (xDisplay, yDisplay)
     * is unused server-side; the client overlay renders its own visuals and
     * routes clicks via windowClick using slotNumber.
     */
    public static final class SlotPocketServer extends Slot {

        private final PocketInventory pocketInv;

        public SlotPocketServer(PocketInventory inv, int index, int xDisplay, int yDisplay) {
            super(inv, index, xDisplay, yDisplay);
            this.pocketInv = inv;
        }

        public PocketInventory getPocketInventory() {
            return pocketInv;
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return true;
        }
    }
}
