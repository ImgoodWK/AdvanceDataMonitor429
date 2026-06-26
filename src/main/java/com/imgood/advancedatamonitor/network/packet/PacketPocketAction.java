package com.imgood.advancedatamonitor.network.packet;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.handler.HandlerTick;
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

        switch (message.action) {
            case REQUEST_SYNC:
                // Sync is handled by sending a full sync packet below; nothing to mutate.
                changed = true;
                break;
            case TOGGLE_ENABLED:
                state.setEnabled(!state.isEnabled());
                changed = true;
                break;
            case SET_PAGE:
                // Page is client-display-only; we don't persist it in the player file.
                // Still ack so the client can confirm range.
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
                    errorKey = "adm.error.pocket.cannotRemoveSpace";
                    errorArg = PocketUpgradeRules.computeMinSpaceUpgrades(state);
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
                    errorKey = "adm.error.pocket.cannotRemovePage";
                    errorArg = PocketUpgradeRules.computeMinPageUpgrades(state);
                }
                break;
            }
            default:
                break;
        }

        if (changed) {
            PocketStore.instance()
                .save(player);
        }
        if (error && errorKey != null) {
            player.addChatMessage(new ChatComponentTranslation(errorKey, errorArg));
        }
        // Always reply with a sync so the client stays in step.
        PacketPocketSync sync = PacketPocketSync.fullState(state);
        AdvanceDataMonitor.ADMCHANEL.sendTo(sync, player);
    }

    @SideOnly(Side.CLIENT)
    public static class ClientHandler implements IMessageHandler<PacketPocketAction, IMessage> {

        @Override
        public IMessage onMessage(PacketPocketAction message, MessageContext ctx) {
            // Client side does not expect PacketPocketAction replies; sync comes via PacketPocketSync.
            return null;
        }
    }
}
