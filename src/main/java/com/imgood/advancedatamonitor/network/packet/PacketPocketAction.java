package com.imgood.advancedatamonitor.network.packet;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.handler.HandlerTick;
import com.imgood.advancedatamonitor.handler.PocketSlotInteraction;
import com.imgood.advancedatamonitor.handler.PocketState;
import com.imgood.advancedatamonitor.handler.PocketStore;
import com.imgood.advancedatamonitor.handler.PocketUpgradeRules;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * C→S action packet for the Dimensional Pocket. All actions operate on the
 * player-bound PocketState (never on item NBT). The server handler mutates the
 * state, persists via PocketStore, and broadcasts a PacketPocketSync back.
 */
public class PacketPocketAction implements IMessage {

    public static final byte REQUEST_SYNC = 0;
    public static final byte TOGGLE_ENABLED = 1;
    public static final byte SET_PAGE = 2;
    public static final byte SET_WINDOW_POS = 3;
    public static final byte SET_COLLAPSED = 4;
    public static final byte ADD_SPACE_UPGRADE = 5;
    public static final byte REMOVE_SPACE_UPGRADE = 6;
    public static final byte ADD_PAGE_UPGRADE = 7;
    public static final byte REMOVE_PAGE_UPGRADE = 8;
    /** Deposit player's cursor stack into pocket slot. intValue=slotIndex, floatValue1=pageIndex. */
    public static final byte DEPOSIT_FROM_CURSOR = 9;
    /** Withdraw stack from pocket slot to player's cursor. intValue=slotIndex, floatValue1=pageIndex. */
    public static final byte WITHDRAW_TO_CURSOR = 10;
    /** Quick-deposit: transfer one matching item from player inv to pocket slot. */
    public static final byte QUICK_DEPOSIT = 11;
    /** Quick-withdraw: transfer whole stack from pocket slot to player inventory. */
    public static final byte QUICK_WITHDRAW = 12;
    /** Deposit a single item from cursor (right-click). */
    public static final byte DEPOSIT_SINGLE_FROM_CURSOR = 13;
    /** Add stack upgrade card. intValue=amount. */
    public static final byte ADD_STACK_UPGRADE = 14;
    /** Remove stack upgrade card. intValue=amount. */
    public static final byte REMOVE_STACK_UPGRADE = 15;
    /** Add infinite stack upgrade card. */
    public static final byte ADD_INFINITE_STACK_UPGRADE = 16;
    /** Remove infinite stack upgrade card. */
    public static final byte REMOVE_INFINITE_STACK_UPGRADE = 17;
    /** Request the server to open the pocket config (upgrade) GUI. Handled server-side so
     *  the container gets a proper windowId and the server's openContainer is the real
     *  ContainerDimensionalPocket — same reason onItemRightClick opens storage server-side. */
    public static final byte OPEN_CONFIG_GUI = 18;

    private byte action;
    private int intValue;
    private float floatValue1;
    private float floatValue2;
    private boolean boolValue;

    public PacketPocketAction() {}

    public static PacketPocketAction requestSync() {
        PacketPocketAction p = new PacketPocketAction();
        p.action = REQUEST_SYNC;
        return p;
    }

    public static PacketPocketAction toggleEnabled() {
        PacketPocketAction p = new PacketPocketAction();
        p.action = TOGGLE_ENABLED;
        return p;
    }

    public static PacketPocketAction setPage(int page) {
        PacketPocketAction p = new PacketPocketAction();
        p.action = SET_PAGE;
        p.intValue = page;
        return p;
    }

    public static PacketPocketAction setWindowPos(float x, float y) {
        PacketPocketAction p = new PacketPocketAction();
        p.action = SET_WINDOW_POS;
        p.floatValue1 = x;
        p.floatValue2 = y;
        return p;
    }

    public static PacketPocketAction setCollapsed(boolean collapsed) {
        PacketPocketAction p = new PacketPocketAction();
        p.action = SET_COLLAPSED;
        p.boolValue = collapsed;
        return p;
    }

    public static PacketPocketAction addSpaceUpgrade(int amount) {
        PacketPocketAction p = new PacketPocketAction();
        p.action = ADD_SPACE_UPGRADE;
        p.intValue = amount;
        return p;
    }

    public static PacketPocketAction removeSpaceUpgrade(int amount) {
        PacketPocketAction p = new PacketPocketAction();
        p.action = REMOVE_SPACE_UPGRADE;
        p.intValue = amount;
        return p;
    }

    public static PacketPocketAction addPageUpgrade(int amount) {
        PacketPocketAction p = new PacketPocketAction();
        p.action = ADD_PAGE_UPGRADE;
        p.intValue = amount;
        return p;
    }

    public static PacketPocketAction removePageUpgrade(int amount) {
        PacketPocketAction p = new PacketPocketAction();
        p.action = REMOVE_PAGE_UPGRADE;
        p.intValue = amount;
        return p;
    }

    public static PacketPocketAction depositFromCursor(int page, int slot) {
        PacketPocketAction p = new PacketPocketAction();
        p.action = DEPOSIT_FROM_CURSOR;
        p.intValue = slot;
        p.floatValue1 = page;
        return p;
    }

    public static PacketPocketAction withdrawToCursor(int page, int slot) {
        PacketPocketAction p = new PacketPocketAction();
        p.action = WITHDRAW_TO_CURSOR;
        p.intValue = slot;
        p.floatValue1 = page;
        return p;
    }

    public static PacketPocketAction quickDeposit(int page, int slot) {
        PacketPocketAction p = new PacketPocketAction();
        p.action = QUICK_DEPOSIT;
        p.intValue = slot;
        p.floatValue1 = page;
        return p;
    }

    public static PacketPocketAction quickWithdraw(int page, int slot) {
        PacketPocketAction p = new PacketPocketAction();
        p.action = QUICK_WITHDRAW;
        p.intValue = slot;
        p.floatValue1 = page;
        return p;
    }

    public static PacketPocketAction depositSingleFromCursor(int page, int slot) {
        PacketPocketAction p = new PacketPocketAction();
        p.action = DEPOSIT_SINGLE_FROM_CURSOR;
        p.intValue = slot;
        p.floatValue1 = page;
        return p;
    }

    public static PacketPocketAction addStackUpgrade(int amount) {
        PacketPocketAction p = new PacketPocketAction();
        p.action = ADD_STACK_UPGRADE;
        p.intValue = amount;
        return p;
    }

    public static PacketPocketAction removeStackUpgrade(int amount) {
        PacketPocketAction p = new PacketPocketAction();
        p.action = REMOVE_STACK_UPGRADE;
        p.intValue = amount;
        return p;
    }

    public static PacketPocketAction addInfiniteStackUpgrade() {
        PacketPocketAction p = new PacketPocketAction();
        p.action = ADD_INFINITE_STACK_UPGRADE;
        return p;
    }

    public static PacketPocketAction removeInfiniteStackUpgrade() {
        PacketPocketAction p = new PacketPocketAction();
        p.action = REMOVE_INFINITE_STACK_UPGRADE;
        return p;
    }

    public static PacketPocketAction openConfigGui() {
        PacketPocketAction p = new PacketPocketAction();
        p.action = OPEN_CONFIG_GUI;
        return p;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(action);
        buf.writeInt(intValue);
        buf.writeFloat(floatValue1);
        buf.writeFloat(floatValue2);
        buf.writeBoolean(boolValue);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        action = buf.readByte();
        intValue = buf.readInt();
        floatValue1 = buf.readFloat();
        floatValue2 = buf.readFloat();
        boolValue = buf.readBoolean();
    }

    public static class ServerHandler implements IMessageHandler<PacketPocketAction, IMessage> {

        @Override
        public IMessage onMessage(final PacketPocketAction message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            HandlerTick.enqueueServerTask(new Runnable() {

                @Override
                public void run() {
                    handleServer(player, message);
                }
            });
            return null;
        }
    }

    private static void handleServer(EntityPlayerMP player, PacketPocketAction message) {
        if (player == null) return;
        PocketState state = PocketStore.instance()
            .getOrCreate(player);
        boolean changed = false;
        boolean error = false;
        String errorKey = null;
        int errorArg = 0;
        boolean skipDefaultSync = false;

        switch (message.action) {
            case REQUEST_SYNC:
                changed = true;
                break;
            case TOGGLE_ENABLED:
                state.setEnabled(!state.isEnabled());
                changed = true;
                break;
            case SET_PAGE:
                if (player.openContainer instanceof com.imgood.advancedatamonitor.gui.container.ContainerPocketStorage) {
                    ((com.imgood.advancedatamonitor.gui.container.ContainerPocketStorage) player.openContainer)
                        .setPage(message.intValue);
                    skipDefaultSync = true;
                }
                changed = true;
                break;
            case SET_WINDOW_POS:
                state.setWindowX(message.floatValue1);
                state.setWindowY(message.floatValue2);
                changed = true;
                break;
            case SET_COLLAPSED:
                state.setCollapsed(message.boolValue);
                changed = true;
                break;
            case ADD_SPACE_UPGRADE: {
                int amount = Math.max(0, message.intValue);
                if (PocketUpgradeRules.canAddSpaceUpgrade(state, amount)) {
                    state.setSpaceUpgrades(state.getSpaceUpgrades() + amount);
                    changed = true;
                } else {
                    error = true;
                }
                break;
            }
            case REMOVE_SPACE_UPGRADE: {
                int amount = Math.max(0, message.intValue);
                if (PocketUpgradeRules.canRemoveSpaceUpgrade(state, amount)) {
                    state.setSpaceUpgrades(state.getSpaceUpgrades() - amount);
                    changed = true;
                } else {
                    error = true;
                    if (PocketUpgradeRules.hasStoredItems(state)) {
                        errorKey = "adm.error.pocket.cannotRemoveUpgradeWhileStored";
                    } else {
                        errorKey = "adm.error.pocket.cannotRemoveSpace";
                        errorArg = PocketUpgradeRules.computeMinSpaceUpgrades(state);
                    }
                }
                break;
            }
            case ADD_PAGE_UPGRADE: {
                int amount = Math.max(0, message.intValue);
                if (PocketUpgradeRules.canAddPageUpgrade(state, amount)) {
                    state.setPageUpgrades(state.getPageUpgrades() + amount);
                    changed = true;
                } else if (state.getSpaceUpgrades() < PocketState.MAX_SPACE_UPGRADES) {
                    error = true;
                    errorKey = "adm.error.pocket.pageUpgradeBlocked";
                } else {
                    error = true;
                }
                break;
            }
            case REMOVE_PAGE_UPGRADE: {
                int amount = Math.max(0, message.intValue);
                if (PocketUpgradeRules.canRemovePageUpgrade(state, amount)) {
                    state.setPageUpgrades(state.getPageUpgrades() - amount);
                    changed = true;
                } else {
                    error = true;
                    if (PocketUpgradeRules.hasStoredItems(state)) {
                        errorKey = "adm.error.pocket.cannotRemoveUpgradeWhileStored";
                    } else {
                        errorKey = "adm.error.pocket.cannotRemovePage";
                        errorArg = PocketUpgradeRules.computeMinPageUpgrades(state);
                    }
                }
                break;
            }
            case DEPOSIT_FROM_CURSOR:
            case WITHDRAW_TO_CURSOR:
            case DEPOSIT_SINGLE_FROM_CURSOR: {
                int page = (int) message.floatValue1;
                int slot = message.intValue;
                int mouseButton = message.action == DEPOSIT_SINGLE_FROM_CURSOR ? 1 : 0;
                if (PocketSlotInteraction.applySlotClick(state, page, slot, mouseButton, player)) {
                    changed = true;
                }
                break;
            }
            case QUICK_DEPOSIT: {
                int page = (int) message.floatValue1;
                int slot = message.intValue;
                ItemStack pocketStack = state.getStack(page, slot);
                if (pocketStack != null && state.isValid(page, slot)) {
                    int maxStack = PocketSlotInteraction.getStackLimit(state);
                    int space = maxStack - pocketStack.stackSize;
                    if (space <= 0) break;
                    for (int i = 0; i < player.inventory.mainInventory.length; i++) {
                        ItemStack invStack = player.inventory.mainInventory[i];
                        if (invStack != null && invStack.isItemEqual(pocketStack)
                            && ItemStack.areItemStackTagsEqual(invStack, pocketStack)) {
                            int toMove = Math.min(space, invStack.stackSize);
                            pocketStack.stackSize += toMove;
                            invStack.stackSize -= toMove;
                            if (invStack.stackSize <= 0) player.inventory.mainInventory[i] = null;
                            space -= toMove;
                            if (space <= 0) break;
                        }
                    }
                    changed = true;
                }
                break;
            }
            case QUICK_WITHDRAW: {
                int page = (int) message.floatValue1;
                int slot = message.intValue;
                if (PocketSlotInteraction.quickMoveFromPocketToPlayer(state, page, slot, player)) {
                    changed = true;
                }
                break;
            }
            case ADD_STACK_UPGRADE: {
                int amount = Math.max(0, message.intValue);
                if (PocketUpgradeRules.canAddStackUpgrade(state, amount)) {
                    state.setStackUpgrades(state.getStackUpgrades() + amount);
                    changed = true;
                } else {
                    error = true;
                }
                break;
            }
            case REMOVE_STACK_UPGRADE: {
                int amount = Math.max(0, message.intValue);
                if (PocketUpgradeRules.canRemoveStackUpgrade(state, amount)) {
                    state.setStackUpgrades(state.getStackUpgrades() - amount);
                    changed = true;
                } else {
                    error = true;
                    if (PocketUpgradeRules.hasStoredItems(state)) {
                        errorKey = "adm.error.pocket.cannotRemoveUpgradeWhileStored";
                    }
                }
                break;
            }
            case ADD_INFINITE_STACK_UPGRADE: {
                if (PocketUpgradeRules.canAddInfiniteStackUpgrade(state)) {
                    state.setInfiniteStackUpgrade(true);
                    changed = true;
                } else {
                    error = true;
                }
                break;
            }
            case REMOVE_INFINITE_STACK_UPGRADE: {
                if (PocketUpgradeRules.canRemoveInfiniteStackUpgrade(state)) {
                    state.setInfiniteStackUpgrade(false);
                    changed = true;
                } else {
                    error = true;
                    if (PocketUpgradeRules.hasStoredItems(state)) {
                        errorKey = "adm.error.pocket.cannotRemoveUpgradeWhileStored";
                    }
                }
                break;
            }
            case OPEN_CONFIG_GUI: {
                // Open the config GUI server-side so the container gets a real windowId
                // and the server's openContainer is the authoritative ContainerDimensionalPocket.
                // Forge will sync S2D_OPEN_WINDOW to the client, which calls getClientGuiElement.
                player.openGui(com.imgood.advancedatamonitor.AdvanceDataMonitor.instance,
                    com.imgood.advancedatamonitor.gui.handler.GuiHandler.POCKET_CONFIG_GUI_ID,
                    player.worldObj, 0, 0, 0);
                // openGui triggers its own container sync; skip the default pocket sync.
                skipDefaultSync = true;
                break;
            }
            default:
                break;
        }

        if (changed) {
            PocketStore.instance()
                .save(player);
        }
        // Cursor/mainInventory operations mutate the player's cursor stack or inventory
        // outside the vanilla windowClick path. The default PacketPocketSync only mirrors
        // pocket contents, NOT the player's cursor — so the client's
        // mc.thePlayer.inventory.getItemStack() would stay stale (cursor appears empty
        // even though the server moved a stack onto it). Force the open container to
        // detect & send its slot 0 (cursor) plus any changed inventory slots via the
        // vanilla S2FPacketSetSlot mechanism.
        if (changed && (message.action == DEPOSIT_FROM_CURSOR || message.action == WITHDRAW_TO_CURSOR
            || message.action == DEPOSIT_SINGLE_FROM_CURSOR || message.action == QUICK_DEPOSIT
            || message.action == QUICK_WITHDRAW)) {
            player.openContainer.detectAndSendChanges();
        }
        if (error && errorKey != null) {
            player.addChatMessage(new ChatComponentTranslation(errorKey, errorArg));
        }
        if (skipDefaultSync) return;
        // Always reply with a sync so the client stays in step.
        PacketPocketSync sync = PacketPocketSync.fullState(state);
        // detectAndSendChanges() above only syncs container SLOT contents — it does NOT
        // sync the player's carried item (player.inventory.itemStack), which lives outside
        // Container.inventorySlots. So a withdraw that calls setItemStack() on the server
        // never reaches the client cursor, and the held item is invisible. Attach the
        // authoritative server cursor to the sync for the actions that mutate it, and the
        // client handler will call mc.thePlayer.inventory.setItemStack() to apply it.
        if (message.action == WITHDRAW_TO_CURSOR || message.action == DEPOSIT_FROM_CURSOR
            || message.action == DEPOSIT_SINGLE_FROM_CURSOR) {
            sync.hasCursor = true;
            sync.cursorStack = player.inventory.getItemStack();
        }
        AdvanceDataMonitor.ADMCHANEL.sendTo(sync, player);
    }

    @SideOnly(Side.CLIENT)
    public static class ClientHandler implements IMessageHandler<PacketPocketAction, IMessage> {

        @Override
        public IMessage onMessage(PacketPocketAction message, MessageContext ctx) {
            return null;
        }
    }
}
