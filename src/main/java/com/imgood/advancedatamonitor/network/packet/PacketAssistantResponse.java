package com.imgood.advancedatamonitor.network.packet;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.assistant.AssistantController;
import com.imgood.advancedatamonitor.assistant.AssistantOrderLine;
import com.imgood.advancedatamonitor.assistant.AssistantSessionKind;
import com.imgood.advancedatamonitor.assistant.CraftingCandidate;
import com.imgood.advancedatamonitor.assistant.TeleportDestination;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketAssistantResponse implements IMessage {

    private static final int MESSAGE = 1;
    private static final int CANDIDATES = 2;
    private static final int BATCH_CANDIDATES = 3;
    private static final int WITHDRAW_PARTIAL = 4;
    private static final int TELEPORT_CANDIDATES = 5;
    private static final int COMPRESSION_THRESHOLD = 2048;

    private int type;
    private String message = "";
    private String rawText = "";
    private NBTTagCompound payload = new NBTTagCompound();

    public PacketAssistantResponse() {}

    public static PacketAssistantResponse message(String message) {
        PacketAssistantResponse packet = new PacketAssistantResponse();
        packet.type = MESSAGE;
        packet.message = message == null ? "" : message;
        return packet;
    }

    public static PacketAssistantResponse candidates(String rawText, List<CraftingCandidate> candidates) {
        return candidates(rawText, candidates, AssistantSessionKind.ORDER_CANDIDATES);
    }

    public static PacketAssistantResponse candidates(String rawText, List<CraftingCandidate> candidates,
        AssistantSessionKind kind) {
        PacketAssistantResponse packet = new PacketAssistantResponse();
        packet.type = CANDIDATES;
        packet.rawText = rawText == null ? "" : rawText;
        packet.payload.setString("kind", (kind == null ? AssistantSessionKind.ORDER_CANDIDATES : kind).name());
        packet.payload.setTag("candidates", writeCandidates(candidates));
        return packet;
    }

    public static PacketAssistantResponse batchCandidates(String rawText, List<AssistantOrderLine> lines) {
        return batchCandidates(rawText, lines, AssistantSessionKind.ORDER_BATCH_CANDIDATES);
    }

    public static PacketAssistantResponse batchCandidates(String rawText, List<AssistantOrderLine> lines,
        AssistantSessionKind kind) {
        PacketAssistantResponse packet = new PacketAssistantResponse();
        packet.type = BATCH_CANDIDATES;
        packet.rawText = rawText == null ? "" : rawText;
        packet.payload.setString("kind", (kind == null ? AssistantSessionKind.ORDER_BATCH_CANDIDATES : kind).name());
        NBTTagList list = new NBTTagList();
        if (lines != null) {
            for (AssistantOrderLine line : lines) {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setInteger("lineIndex", line.lineIndex);
                tag.setString("target", line.target == null ? "" : line.target);
                tag.setLong("amount", line.amount);
                tag.setTag("candidates", writeCandidates(line.getCandidates()));
                CraftingCandidate selected = line.selectedOrFirstCandidate();
                if (selected != null) {
                    tag.setTag("selected", writeCandidate(selected));
                }
                list.appendTag(tag);
            }
        }
        packet.payload.setTag("lines", list);
        return packet;
    }

    public static PacketAssistantResponse withdrawPartial(String rawText, String message, CraftingCandidate candidate,
        long requestedAmount, long fitAmount, long storageAmount) {
        PacketAssistantResponse packet = new PacketAssistantResponse();
        packet.type = WITHDRAW_PARTIAL;
        packet.rawText = rawText == null ? "" : rawText;
        packet.message = message == null ? "" : message;
        packet.payload.setLong("requestedAmount", requestedAmount);
        packet.payload.setLong("fitAmount", fitAmount);
        packet.payload.setLong("storageAmount", storageAmount);
        packet.payload.setTag("candidate", writeCandidate(candidate));
        return packet;
    }

    public static PacketAssistantResponse teleportCandidates(String rawText, List<TeleportDestination> destinations) {
        PacketAssistantResponse packet = new PacketAssistantResponse();
        packet.type = TELEPORT_CANDIDATES;
        packet.rawText = rawText == null ? "" : rawText;
        NBTTagList list = new NBTTagList();
        if (destinations != null) {
            for (TeleportDestination dest : destinations) {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setInteger("index", dest.index);
                tag.setString("name", dest.name);
                tag.setInteger("dimensionId", dest.dimensionId);
                tag.setString("dimensionName", dest.dimensionName);
                tag.setInteger("x", dest.x);
                tag.setInteger("y", dest.y);
                tag.setInteger("z", dest.z);
                list.appendTag(tag);
            }
        }
        packet.payload.setTag("destinations", list);
        return packet;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.type = buf.readByte();
        this.message = ByteBufUtils.readUTF8String(buf);
        this.rawText = ByteBufUtils.readUTF8String(buf);
        byte compressedFlag = buf.readByte();
        if (compressedFlag == 1) {
            // Decompress gzipped NBT payload
            int compressedLength = buf.readInt();
            byte[] compressedBytes = new byte[compressedLength];
            buf.readBytes(compressedBytes);
            try {
                ByteArrayInputStream bis = new ByteArrayInputStream(compressedBytes);
                GZIPInputStream gzis = new GZIPInputStream(bis);
                DataInputStream dis = new DataInputStream(gzis);
                this.payload = CompressedStreamTools.read(dis);
                dis.close();
            } catch (IOException e) {
                AdvanceDataMonitor.LOG.error("[ADM Assistant] Failed to decompress response payload", e);
                this.payload = new NBTTagCompound();
            }
        } else {
            this.payload = ByteBufUtils.readTag(buf);
            if (this.payload == null) {
                this.payload = new NBTTagCompound();
            }
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(this.type);
        ByteBufUtils.writeUTF8String(buf, this.message == null ? "" : this.message);
        ByteBufUtils.writeUTF8String(buf, this.rawText == null ? "" : this.rawText);
        NBTTagCompound tag = this.payload == null ? new NBTTagCompound() : this.payload;
        // Check if payload is large enough to warrant compression
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(bos);
            CompressedStreamTools.write(tag, dos);
            dos.close();
            byte[] uncompressedBytes = bos.toByteArray();
            if (uncompressedBytes.length > COMPRESSION_THRESHOLD) {
                // Gzip compress the raw NBT bytes
                ByteArrayOutputStream compressedBos = new ByteArrayOutputStream();
                GZIPOutputStream gzos = new GZIPOutputStream(compressedBos);
                gzos.write(uncompressedBytes);
                gzos.finish();
                gzos.close();
                byte[] compressedBytes = compressedBos.toByteArray();
                buf.writeByte(1); // compression flag
                buf.writeInt(compressedBytes.length);
                buf.writeBytes(compressedBytes);
            } else {
                buf.writeByte(0); // no compression
                ByteBufUtils.writeTag(buf, tag);
            }
        } catch (IOException e) {
            AdvanceDataMonitor.LOG.error("[ADM Assistant] Failed to compress response payload", e);
            buf.writeByte(0);
            ByteBufUtils.writeTag(buf, tag);
        }
    }

    private static NBTTagCompound writeCandidate(CraftingCandidate candidate) {
        NBTTagCompound tag = new NBTTagCompound();
        if (candidate == null) {
            return tag;
        }
        tag.setInteger("index", candidate.index);
        tag.setLong("amount", candidate.amount);
        tag.setTag("item", candidate.itemNbt);
        return tag;
    }

    private static NBTTagList writeCandidates(List<CraftingCandidate> candidates) {
        NBTTagList list = new NBTTagList();
        if (candidates != null) {
            for (CraftingCandidate candidate : candidates) {
                list.appendTag(writeCandidate(candidate));
            }
        }
        return list;
    }

    public static class Handler implements IMessageHandler<PacketAssistantResponse, IMessage> {

        @Override
        public IMessage onMessage(final PacketAssistantResponse message, MessageContext ctx) {
            AdvanceDataMonitor.LOG.info(
                "[ADM Assistant] PacketAssistantResponse received: type={}, raw='{}', messageLength={}",
                message.type,
                safe(message.rawText),
                message.message == null ? 0 : message.message.length());
            scheduleClient(new Runnable() {

                @Override
                public void run() {
                    handleOnClientThread(message);
                }
            });
            return null;
        }

        private void handleOnClientThread(PacketAssistantResponse message) {
            if (message.type == CANDIDATES) {
                AssistantController
                    .handleCandidates(message.rawText, readCandidates(message.payload), readKind(message.payload));
            } else if (message.type == BATCH_CANDIDATES) {
                AssistantController.handleBatchCandidates(
                    message.rawText,
                    readOrderLines(message.payload),
                    readBatchKind(message.payload));
            } else if (message.type == WITHDRAW_PARTIAL) {
                AssistantController.handleWithdrawPartial(
                    message.rawText,
                    message.message,
                    readCandidate(message.payload == null ? null : message.payload.getCompoundTag("candidate")),
                    message.payload == null ? 0L : message.payload.getLong("requestedAmount"),
                    message.payload == null ? 0L : message.payload.getLong("fitAmount"),
                    message.payload == null ? 0L : message.payload.getLong("storageAmount"));
            } else if (message.type == TELEPORT_CANDIDATES) {
                AssistantController
                    .handleTeleportCandidates(message.rawText, readTeleportDestinations(message.payload));
            } else {
                AssistantController.handleServerMessage(message.message);
            }
        }

        private void scheduleClient(Runnable runnable) {
            Minecraft mc = Minecraft.getMinecraft();
            try {
                Method method = mc.getClass()
                    .getMethod("func_152344_a", Runnable.class);
                method.invoke(mc, runnable);
            } catch (Exception ignored) {
                runnable.run();
            }
        }

        private AssistantSessionKind readBatchKind(NBTTagCompound payload) {
            return readKind(payload, AssistantSessionKind.ORDER_BATCH_CANDIDATES);
        }

        private AssistantSessionKind readKind(NBTTagCompound payload) {
            return readKind(payload, AssistantSessionKind.ORDER_CANDIDATES);
        }

        private AssistantSessionKind readKind(NBTTagCompound payload, AssistantSessionKind fallback) {
            if (payload == null || !payload.hasKey("kind")) {
                return fallback;
            }
            try {
                return AssistantSessionKind.valueOf(payload.getString("kind"));
            } catch (IllegalArgumentException ignored) {
                return fallback;
            }
        }

        private List<AssistantOrderLine> readOrderLines(NBTTagCompound payload) {
            List<AssistantOrderLine> result = new ArrayList<AssistantOrderLine>();
            if (payload == null || !payload.hasKey("lines")) {
                return result;
            }
            NBTTagList list = payload.getTagList("lines", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound tag = list.getCompoundTagAt(i);
                AssistantOrderLine line = new AssistantOrderLine(
                    tag.getInteger("lineIndex"),
                    tag.getString("target"),
                    tag.getLong("amount"));
                line.setCandidates(readCandidates(tag));
                if (tag.hasKey("selected")) {
                    line.selectedCandidate = readCandidate(tag.getCompoundTag("selected"));
                }
                result.add(line);
            }
            return result;
        }

        private List<CraftingCandidate> readCandidates(NBTTagCompound payload) {
            List<CraftingCandidate> result = new ArrayList<CraftingCandidate>();
            if (payload == null || !payload.hasKey("candidates")) {
                return result;
            }
            NBTTagList list = payload.getTagList("candidates", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < list.tagCount(); i++) {
                CraftingCandidate candidate = readCandidate(list.getCompoundTagAt(i));
                if (candidate != null) {
                    result.add(candidate);
                }
            }
            AdvanceDataMonitor.LOG.info("[ADM Assistant] Decoded {} candidates from response payload.", result.size());
            return result;
        }

        private CraftingCandidate readCandidate(NBTTagCompound tag) {
            if (tag == null || !tag.hasKey("item")) {
                return null;
            }
            net.minecraft.item.ItemStack stack = net.minecraft.item.ItemStack
                .loadItemStackFromNBT(tag.getCompoundTag("item"));
            if (stack == null) {
                return null;
            }
            return new CraftingCandidate(tag.getInteger("index"), stack, tag.getLong("amount"));
        }

        private List<TeleportDestination> readTeleportDestinations(NBTTagCompound payload) {
            List<TeleportDestination> result = new ArrayList<TeleportDestination>();
            if (payload == null || !payload.hasKey("destinations")) {
                return result;
            }
            NBTTagList list = payload.getTagList("destinations", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound tag = list.getCompoundTagAt(i);
                TeleportDestination dest = new TeleportDestination(
                    tag.getInteger("index"),
                    tag.getString("name"),
                    tag.getInteger("dimensionId"),
                    tag.getString("dimensionName"),
                    tag.getInteger("x"),
                    tag.getInteger("y"),
                    tag.getInteger("z"),
                    null);
                result.add(dest);
            }
            return result;
        }
    }

    private static String safe(String text) {
        if (text == null) {
            return "";
        }
        return text.replace((char) 10, ' ')
            .replace((char) 13, ' ');
    }
}
