package com.imgood.textech.network.packet;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

import com.imgood.textech.assistant.AssistantMenuState;
import com.imgood.textech.gui.guiscreen.GuiAIChat;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Server-to-client packet: carries the AI assistant menu state (per-connector
 * availability and AE security permission flags). The client caches it in
 * {@link AssistantMenuState} and refreshes the feature menu display.
 *
 * Lang keys: none (consumed by menu rendering logic).
 */
public class PacketAssistantMenuStateResponse implements IMessage {

    private boolean craftingAvailable;
    private boolean craftingHasPermission;
    private boolean storageAvailable;
    private boolean storageHasPermission;
    private boolean networkAvailable;
    private boolean networkHasPermission;

    public PacketAssistantMenuStateResponse() {}

    public PacketAssistantMenuStateResponse(boolean craftingAvailable, boolean craftingHasPermission,
        boolean storageAvailable, boolean storageHasPermission, boolean networkAvailable,
        boolean networkHasPermission) {
        this.craftingAvailable = craftingAvailable;
        this.craftingHasPermission = craftingHasPermission;
        this.storageAvailable = storageAvailable;
        this.storageHasPermission = storageHasPermission;
        this.networkAvailable = networkAvailable;
        this.networkHasPermission = networkHasPermission;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(craftingAvailable);
        buf.writeBoolean(craftingHasPermission);
        buf.writeBoolean(storageAvailable);
        buf.writeBoolean(storageHasPermission);
        buf.writeBoolean(networkAvailable);
        buf.writeBoolean(networkHasPermission);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        craftingAvailable = buf.readBoolean();
        craftingHasPermission = buf.readBoolean();
        storageAvailable = buf.readBoolean();
        storageHasPermission = buf.readBoolean();
        networkAvailable = buf.readBoolean();
        networkHasPermission = buf.readBoolean();
    }

    public static class Handler implements IMessageHandler<PacketAssistantMenuStateResponse, IMessage> {

        @Override
        public IMessage onMessage(final PacketAssistantMenuStateResponse message, MessageContext ctx) {
            // Update the cached menu state on the client thread.
            AssistantMenuState state = GuiAIChat.getCachedMenuState();
            state.setCrafting(message.craftingAvailable, message.craftingHasPermission);
            state.setStorage(message.storageAvailable, message.storageHasPermission);
            state.setNetwork(message.networkAvailable, message.networkHasPermission);
            state.markValid();
            // Notify any open GuiAIChat that the menu state has refreshed.
            EntityPlayer player = Minecraft.getMinecraft().thePlayer;
            if (player != null && Minecraft.getMinecraft().currentScreen instanceof GuiAIChat) {
                ((GuiAIChat) Minecraft.getMinecraft().currentScreen).onMenuStateRefreshed();
            }
            return null;
        }
    }
}
