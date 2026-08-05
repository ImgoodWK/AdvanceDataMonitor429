package com.imgood.textech.network.packet;

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
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.assistant.AssistantController;
import com.imgood.textech.assistant.AssistantOrderLine;
import com.imgood.textech.assistant.AssistantSessionKind;
import com.imgood.textech.assistant.CandidateBatchMeta;
import com.imgood.textech.assistant.CraftingCandidate;
import com.imgood.textech.assistant.TeleportDestination;
import com.imgood.textech.utils.NetworkPacketCodec;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketAssistantResponse implements IMessage {

    private static final int MESSAGE = 1;
    private static final int CANDIDATES = 2;
    private static final int BATCH_CANDIDATES = 3;
    private static final int WITHDRAW_PARTIAL = 4;
    private static final int TELEPORT_CANDIDATES = 5;
    private static final int COMPRESSION_THRESHOLD = 2048;
    private static final int MAX_MESSAGE_BYTES = 4096;
    private static final int MAX_RAW_TEXT_BYTES = 1024;
    public static final int MAX_PACKET_BODY_BYTES = 30000;
    public static final int MAX_COMPRESSED_PAYLOAD_BYTES = 24 * 1024;
    private static final int MAX_NBT_COMPRESSED_BYTES = MAX_COMPRESSED_PAYLOAD_BYTES;
    private static final int MAX_NBT_BYTES = 2 * 1024 * 1024;

    private int type;
    private String message = "";
    private String rawText = "";
    private NBTTagCompound payload = new NBTTagCompound();
    public boolean malformed;

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
        return candidates(rawText, candidates, kind, 0, 1, candidates == null ? 0 : candidates.size(), false);
    }

    public static PacketAssistantResponse candidates(String rawText, List<CraftingCandidate> candidates,
        AssistantSessionKind kind, int batchIndex, int batchCount, int totalCount, boolean append) {
        return candidates(rawText, candidates, kind, batchIndex, batchCount, totalCount, append, 0, 0);
    }

    public static PacketAssistantResponse candidates(String rawText, List<CraftingCandidate> candidates,
        AssistantSessionKind kind, int batchIndex, int batchCount, int totalCount, boolean append, int rangeStart,
        int rangeEnd) {
        PacketAssistantResponse packet = new PacketAssistantResponse();
        packet.type = CANDIDATES;
        packet.rawText = rawText == null ? "" : rawText;
        packet.payload.setString("kind", (kind == null ? AssistantSessionKind.ORDER_CANDIDATES : kind).name());
        packet.payload.setInteger("batchIndex", Math.max(0, batchIndex));
        packet.payload.setInteger("batchCount", Math.max(1, batchCount));
        packet.payload.setInteger("totalCount", Math.max(0, totalCount));
        packet.payload.setBoolean("append", append);
        if (rangeStart > 0 && rangeEnd >= rangeStart) {
            packet.payload.setInteger("rangeStart", rangeStart);
            packet.payload.setInteger("rangeEnd", rangeEnd);
        }
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
        malformed = false;
        try {
            int start = buf.readerIndex();
            this.type = buf.readByte();
            this.message = NetworkPacketCodec.readVarUtf8(buf, MAX_MESSAGE_BYTES);
            this.rawText = NetworkPacketCodec.readVarUtf8(buf, MAX_RAW_TEXT_BYTES);
            byte compressedFlag = buf.readByte();
            if (compressedFlag == 1) {
                int compressedLength = buf.readInt();
                if (compressedLength < 0 || compressedLength > MAX_COMPRESSED_PAYLOAD_BYTES
                    || compressedLength > buf.readableBytes()) {
                    throw new IllegalArgumentException("Invalid compressed assistant response length");
                }
                byte[] compressedBytes = new byte[compressedLength];
                if (compressedLength > 0) {
                    buf.readBytes(compressedBytes);
                }
                byte[] uncompressedBytes = gunzipBounded(compressedBytes, MAX_NBT_BYTES);
                DataInputStream dis = new DataInputStream(new ByteArrayInputStream(uncompressedBytes));
                this.payload = CompressedStreamTools.func_152456_a(dis, new NBTSizeTracker(MAX_NBT_BYTES));
                if (dis.available() != 0) {
                    throw new IllegalArgumentException("Compressed assistant response has trailing NBT bytes");
                }
                dis.close();
                if (this.payload == null) {
                    this.payload = new NBTTagCompound();
                }
            } else if (compressedFlag == 0) {
                this.payload = NetworkPacketCodec.readTag(buf, MAX_NBT_COMPRESSED_BYTES, MAX_NBT_BYTES);
                if (this.payload == null) {
                    this.payload = new NBTTagCompound();
                }
            } else {
                throw new IllegalArgumentException("Unknown assistant response compression flag");
            }
            if (buf.readerIndex() - start > MAX_PACKET_BODY_BYTES || buf.isReadable()) {
                throw new IllegalArgumentException("Assistant response has trailing bytes");
            }
        } catch (IOException e) {
            malformed = true;
            AdvanceDataMonitor.LOG.warn("[ADM Assistant] Rejected malformed response payload", e);
            this.message = "";
            this.rawText = "";
            this.payload = new NBTTagCompound();
        } catch (RuntimeException e) {
            malformed = true;
            this.message = "";
            this.rawText = "";
            this.payload = new NBTTagCompound();
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        int start = buf.writerIndex();
        buf.writeByte(this.type);
        NetworkPacketCodec.writeVarUtf8(buf, this.message == null ? "" : this.message, MAX_MESSAGE_BYTES);
        NetworkPacketCodec.writeVarUtf8(buf, this.rawText == null ? "" : this.rawText, MAX_RAW_TEXT_BYTES);
        NBTTagCompound tag = this.payload == null ? new NBTTagCompound() : this.payload;
        // Check if payload is large enough to warrant compression
        try {
            BoundedByteArrayOutputStream bos = new BoundedByteArrayOutputStream(MAX_NBT_BYTES);
            DataOutputStream dos = new DataOutputStream(bos);
            CompressedStreamTools.write(tag, dos);
            dos.close();
            byte[] uncompressedBytes = bos.toByteArray();
            if (uncompressedBytes.length > MAX_NBT_BYTES) {
                throw new IllegalArgumentException("Assistant response NBT exceeds packet limit");
            }
            if (uncompressedBytes.length > COMPRESSION_THRESHOLD) {
                // Gzip compress the raw NBT bytes
                BoundedByteArrayOutputStream compressedBos = new BoundedByteArrayOutputStream(
                    MAX_COMPRESSED_PAYLOAD_BYTES);
                GZIPOutputStream gzos = new GZIPOutputStream(compressedBos);
                gzos.write(uncompressedBytes);
                gzos.finish();
                gzos.close();
                byte[] compressedBytes = compressedBos.toByteArray();
                if (compressedBytes.length > MAX_COMPRESSED_PAYLOAD_BYTES) {
                    throw new IllegalArgumentException("Compressed assistant response exceeds packet limit");
                }
                buf.writeByte(1); // compression flag
                buf.writeInt(compressedBytes.length);
                buf.writeBytes(compressedBytes);
            } else {
                buf.writeByte(0); // no compression
                NetworkPacketCodec.writeTag(buf, tag, MAX_NBT_COMPRESSED_BYTES);
            }
            if (buf.writerIndex() - start > MAX_PACKET_BODY_BYTES) {
                throw new IllegalArgumentException("Assistant response exceeds FML packet limit");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not encode assistant response payload", e);
        }
    }

    /** Preflight helper used to split candidate responses before FML sees them. */
    public boolean fitsPacketBudget(int maxBodyBytes) {
        if (maxBodyBytes <= 0 || maxBodyBytes > MAX_PACKET_BODY_BYTES) {
            return false;
        }
        ByteBuf scratch = Unpooled.buffer(Math.min(maxBodyBytes, 8192));
        try {
            toBytes(scratch);
            return scratch.readableBytes() <= maxBodyBytes;
        } catch (RuntimeException error) {
            return false;
        } finally {
            scratch.release();
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
            if (message == null || message.malformed) {
                return null;
            }
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
                CandidateBatchMeta batchMeta = readCandidateBatchMeta(message.payload);
                AssistantController.handleCandidates(
                    message.rawText,
                    readCandidates(message.payload),
                    readKind(message.payload),
                    batchMeta);
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

        private CandidateBatchMeta readCandidateBatchMeta(NBTTagCompound payload) {
            if (payload == null) {
                return CandidateBatchMeta.single(0);
            }
            int batchIndex = payload.hasKey("batchIndex") ? payload.getInteger("batchIndex") : 0;
            int batchCount = payload.hasKey("batchCount") ? payload.getInteger("batchCount") : 1;
            int totalCount = payload.hasKey("totalCount") ? payload.getInteger("totalCount") : 0;
            boolean append = payload.hasKey("append") && payload.getBoolean("append");
            int rangeStart = payload.hasKey("rangeStart") ? payload.getInteger("rangeStart") : 0;
            int rangeEnd = payload.hasKey("rangeEnd") ? payload.getInteger("rangeEnd") : 0;
            return new CandidateBatchMeta(batchIndex, batchCount, totalCount, append, rangeStart, rangeEnd);
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

    private static byte[] gunzipBounded(byte[] compressed, int maxBytes) throws IOException {
        GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(compressed));
        BoundedByteArrayOutputStream output = new BoundedByteArrayOutputStream(maxBytes);
        byte[] buffer = new byte[8192];
        try {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } finally {
            input.close();
        }
    }

    private static final class BoundedByteArrayOutputStream extends ByteArrayOutputStream {

        private final int limit;

        private BoundedByteArrayOutputStream(int limit) {
            super(Math.min(limit, 8192));
            this.limit = limit;
        }

        @Override
        public synchronized void write(int value) {
            ensureWithinLimit(1);
            super.write(value);
        }

        @Override
        public synchronized void write(byte[] bytes, int offset, int length) {
            ensureWithinLimit(length);
            super.write(bytes, offset, length);
        }

        private void ensureWithinLimit(int additionalBytes) {
            if (additionalBytes < 0 || count > limit - additionalBytes) {
                throw new IllegalArgumentException("Assistant response payload exceeds bounded buffer");
            }
        }
    }
}
