package com.imgood.advancedatamonitor.tileentity;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.handler.GrappleAnchorPositions;
import com.imgood.advancedatamonitor.handler.GrappleNodeIndex;
import com.imgood.advancedatamonitor.network.packet.PacketGrappleAnchorConfig;

/**
 * Display names / 显示名称:
 * - EN: Grapple Anchor
 * - ZH: 挂索节点
 * Lang keys: tile.grappleAnchor.name (parent block)
 */
public class TileEntityGrappleAnchor extends TileEntity {

    public static final int DEFAULT_ICON_CURSOR_COLOR = 0x00FFFF;

    private ForgeDirection attachFace = ForgeDirection.NORTH;

    private String displayName = "";

    private int iconCursorColor = DEFAULT_ICON_CURSOR_COLOR;

    private boolean indexed;

    public ForgeDirection getAttachFace() {

        return attachFace == null ? ForgeDirection.NORTH : attachFace;

    }

    public void setAttachFace(ForgeDirection face) {

        if (face != null && face != ForgeDirection.UNKNOWN) {

            this.attachFace = face;

            markDirty();

            if (worldObj != null && !worldObj.isRemote) {

                worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);

            }

        }

    }

    public String getDisplayName() {

        return displayName == null ? "" : displayName;

    }

    public void setDisplayName(String name) {

        this.displayName = name == null ? "" : name;

    }

    public String getResolvedDisplayName() {

        String name = getDisplayName().trim();

        if (!name.isEmpty()) {

            return name;

        }

        return StatCollector.translateToLocal("adm.title.grappleAnchor");

    }

    public int getIconCursorColor() {

        return iconCursorColor;

    }

    public void setIconCursorColor(int color) {

        this.iconCursorColor = color & 0xFFFFFF;

    }

    public static TileEntityGrappleAnchor get(World world, int x, int y, int z) {

        if (world == null) {

            return null;

        }

        TileEntity te = world.getTileEntity(x, y, z);

        if (te instanceof TileEntityGrappleAnchor) {

            return (TileEntityGrappleAnchor) te;

        }

        return null;

    }

    public static String resolveDisplayName(World world, int x, int y, int z) {

        TileEntityGrappleAnchor te = get(world, x, y, z);

        if (te != null) {

            return te.getResolvedDisplayName();

        }

        return StatCollector.translateToLocal("adm.title.grappleAnchor");

    }

    public static int resolveIconCursorColor(World world, int x, int y, int z) {

        TileEntityGrappleAnchor te = get(world, x, y, z);

        return te != null ? te.getIconCursorColor() : DEFAULT_ICON_CURSOR_COLOR;

    }

    public static String colorToHex(int color) {

        return String.format("%06X", color & 0xFFFFFF);

    }

    @Override

    public void updateEntity() {

        super.updateEntity();

        if (!worldObj.isRemote && !indexed) {

            GrappleNodeIndex.INSTANCE.addNode(

                worldObj.provider.dimensionId,

                xCoord,

                yCoord,

                zCoord);

            indexed = true;

        }

    }

    public double[] getHangPosition() {

        return GrappleAnchorPositions.resolveHangPosition(worldObj, xCoord, yCoord, zCoord);

    }

    public double[] getNodeRenderPosition() {

        return GrappleAnchorPositions.resolveNodeRenderPosition(worldObj, xCoord, yCoord, zCoord);

    }

    public void applyConfig(String name, int cursorColor) {

        setDisplayName(name);

        setIconCursorColor(cursorColor);

        markDirty();

        if (!worldObj.isRemote) {

            syncToClients();

        }

    }

    public void syncToClients() {

        if (worldObj == null || worldObj.isRemote) {

            return;

        }

        AdvanceDataMonitor.ADMCHANEL.sendToDimension(

            new PacketGrappleAnchorConfig(

                xCoord,

                yCoord,

                zCoord,

                getDisplayName(),

                getIconCursorColor()),

            worldObj.provider.dimensionId);

    }

    @Override

    public Packet getDescriptionPacket() {

        NBTTagCompound tag = new NBTTagCompound();

        writeSyncTag(tag);

        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, tag);

    }

    @Override

    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {

        readSyncTag(pkt.func_148857_g());

    }

    private void writeSyncTag(NBTTagCompound tag) {

        tag.setString("displayName", getDisplayName());

        tag.setInteger("iconCursorColor", getIconCursorColor());

        tag.setInteger("attachFace", getAttachFace().ordinal());

    }

    private void readSyncTag(NBTTagCompound tag) {

        if (tag == null) {

            return;

        }

        if (tag.hasKey("displayName")) {

            setDisplayName(tag.getString("displayName"));

        }

        if (tag.hasKey("iconCursorColor")) {

            setIconCursorColor(tag.getInteger("iconCursorColor"));

        }

        if (tag.hasKey("attachFace")) {

            int ord = tag.getInteger("attachFace");

            if (ord >= 0 && ord < ForgeDirection.values().length) {

                attachFace = ForgeDirection.values()[ord];

            }

        }

    }

    @Override

    public void writeToNBT(NBTTagCompound tag) {

        super.writeToNBT(tag);

        tag.setInteger(
            "attachFace",
            getAttachFace()

                .ordinal());

        tag.setString("displayName", getDisplayName());

        tag.setInteger("iconCursorColor", getIconCursorColor());

    }

    @Override

    public void readFromNBT(NBTTagCompound tag) {

        super.readFromNBT(tag);

        int ord = tag.getInteger("attachFace");

        if (ord >= 0 && ord < ForgeDirection.values().length) {

            attachFace = ForgeDirection.values()[ord];

        }

        if (tag.hasKey("displayName")) {

            setDisplayName(tag.getString("displayName"));

        }

        if (tag.hasKey("iconCursorColor")) {

            setIconCursorColor(tag.getInteger("iconCursorColor"));

        }

    }

}
