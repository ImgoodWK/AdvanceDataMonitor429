package com.imgood.advancedatamonitor.network.packet;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.assistant.AssistantIntentType;
import com.imgood.advancedatamonitor.assistant.AssistantOrderLine;
import com.imgood.advancedatamonitor.assistant.AssistantServerServices;
import com.imgood.advancedatamonitor.assistant.AssistantSessionKind;
import com.imgood.advancedatamonitor.assistant.CraftingCandidate;
import com.imgood.advancedatamonitor.assistant.TeleportDestination;
import com.imgood.advancedatamonitor.assistant.TeleportService;
import com.imgood.advancedatamonitor.handler.HandlerTick;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketAssistantAction implements IMessage {

    private static final int REQUEST_CRAFT_CANDIDATES = 1;
    private static final int SUBMIT_CRAFT = 2;
    private static final int QUERY = 3;
    private static final int QUERY_RECIPE_CANDIDATE = 4;
    private static final int REQUEST_BATCH_CANDIDATES = 5;
    private static final int SUBMIT_BATCH_CRAFT = 6;
    private static final int CANCEL_SERVER_JOBS = 7;
    private static final int REQUEST_WITHDRAW_CANDIDATES = 8;
    private static final int SUBMIT_WITHDRAW = 9;
    private static final int REQUEST_BATCH_WITHDRAW_CANDIDATES = 10;
    private static final int SUBMIT_BATCH_WITHDRAW = 11;
    private static final int REQUEST_TELEPORT_CANDIDATES = 12;
    private static final int SUBMIT_TELEPORT = 13;
    private static final int MAX_TEXT_LENGTH = 256;
    private static final int MAX_BATCH_LINES = 8;

    private int action;
    private String rawText = "";
    private String target = "";
    private long amount;
    private int intentTypeOrdinal;
    private String locale = "zh_CN";
    private NBTTagCompound candidateNbt = new NBTTagCompound();
    private NBTTagCompound payload = new NBTTagCompound();

    public PacketAssistantAction() {}

    public static PacketAssistantAction requestCraftCandidates(String rawText, String target, long amount) {
        PacketAssistantAction packet = new PacketAssistantAction();
        packet.action = REQUEST_CRAFT_CANDIDATES;
        packet.rawText = rawText == null ? "" : rawText;
        packet.target = target == null ? "" : target;
        packet.amount = amount;
        packet.locale = currentLocale();
        return packet;
    }

    public static PacketAssistantAction requestBatchCandidates(String rawText, List<AssistantOrderLine> lines) {
        PacketAssistantAction packet = new PacketAssistantAction();
        packet.action = REQUEST_BATCH_CANDIDATES;
        packet.rawText = rawText == null ? "" : rawText;
        packet.locale = currentLocale();
        packet.payload.setTag("lines", writeOrderLines(lines));
        return packet;
    }

    public static PacketAssistantAction submitCraft(CraftingCandidate candidate, long amount, String rawText) {
        PacketAssistantAction packet = new PacketAssistantAction();
        packet.action = SUBMIT_CRAFT;
        packet.rawText = rawText == null ? "" : rawText;
        packet.amount = amount;
        packet.locale = currentLocale();
        packet.candidateNbt = writeCandidate(candidate);
        return packet;
    }

    public static PacketAssistantAction submitBatchCraft(String rawText, List<AssistantOrderLine> lines) {
        PacketAssistantAction packet = new PacketAssistantAction();
        packet.action = SUBMIT_BATCH_CRAFT;
        packet.rawText = rawText == null ? "" : rawText;
        packet.locale = currentLocale();
        packet.payload.setTag("lines", writeOrderLines(lines));
        return packet;
    }

    public static PacketAssistantAction cancelServerJobs(String rawText) {
        PacketAssistantAction packet = new PacketAssistantAction();
        packet.action = CANCEL_SERVER_JOBS;
        packet.rawText = rawText == null ? "" : rawText;
        packet.locale = currentLocale();
        return packet;
    }

    public static PacketAssistantAction query(AssistantIntentType type, String rawText, String target, long amount) {
        PacketAssistantAction packet = new PacketAssistantAction();
        packet.action = QUERY;
        packet.intentTypeOrdinal = type == null ? AssistantIntentType.CHAT.ordinal() : type.ordinal();
        packet.rawText = rawText == null ? "" : rawText;
        packet.target = target == null ? "" : target;
        packet.amount = amount;
        packet.locale = currentLocale();
        return packet;
    }

    public static PacketAssistantAction query(AssistantIntentType type, String rawText, String target, long amount,
        int storageScope) {
        PacketAssistantAction packet = query(type, rawText, target, amount);
        packet.payload.setInteger("storageScope", storageScope);
        return packet;
    }

    public static PacketAssistantAction requestWithdrawCandidates(String rawText, String target, long amount) {
        PacketAssistantAction packet = new PacketAssistantAction();
        packet.action = REQUEST_WITHDRAW_CANDIDATES;
        packet.rawText = rawText == null ? "" : rawText;
        packet.target = target == null ? "" : target;
        packet.amount = amount;
        packet.locale = currentLocale();
        return packet;
    }

    public static PacketAssistantAction submitWithdraw(CraftingCandidate candidate, long amount, String rawText,
        boolean confirmPartial) {
        PacketAssistantAction packet = new PacketAssistantAction();
        packet.action = SUBMIT_WITHDRAW;
        packet.rawText = rawText == null ? "" : rawText;
        packet.amount = amount;
        packet.locale = currentLocale();
        packet.candidateNbt = writeCandidate(candidate);
        packet.payload.setBoolean("confirmPartial", confirmPartial);
        return packet;
    }

    public static PacketAssistantAction requestBatchWithdrawCandidates(String rawText, List<AssistantOrderLine> lines) {
        PacketAssistantAction packet = new PacketAssistantAction();
        packet.action = REQUEST_BATCH_WITHDRAW_CANDIDATES;
        packet.rawText = rawText == null ? "" : rawText;
        packet.locale = currentLocale();
        packet.payload.setTag("lines", writeOrderLines(lines));
        return packet;
    }

    public static PacketAssistantAction submitBatchWithdraw(String rawText, List<AssistantOrderLine> lines) {
        PacketAssistantAction packet = new PacketAssistantAction();
        packet.action = SUBMIT_BATCH_WITHDRAW;
        packet.rawText = rawText == null ? "" : rawText;
        packet.locale = currentLocale();
        packet.payload.setTag("lines", writeOrderLines(lines));
        return packet;
    }

    public static PacketAssistantAction queryRecipeCandidate(CraftingCandidate candidate, long amount, String rawText) {
        PacketAssistantAction packet = new PacketAssistantAction();
        packet.action = QUERY_RECIPE_CANDIDATE;
        packet.intentTypeOrdinal = AssistantIntentType.QUERY_RECIPE.ordinal();
        packet.rawText = rawText == null ? "" : rawText;
        packet.target = candidate == null ? "" : candidate.displayName;
        packet.amount = amount;
        packet.locale = currentLocale();
        packet.candidateNbt = writeCandidate(candidate);
        return packet;
    }

    public static PacketAssistantAction requestTeleportCandidates(String rawText, String target) {
        PacketAssistantAction packet = new PacketAssistantAction();
        packet.action = REQUEST_TELEPORT_CANDIDATES;
        packet.rawText = rawText == null ? "" : rawText;
        packet.target = target == null ? "" : target;
        packet.locale = currentLocale();
        return packet;
    }

    public static PacketAssistantAction submitTeleport(TeleportDestination dest, String rawText) {
        PacketAssistantAction packet = new PacketAssistantAction();
        packet.action = SUBMIT_TELEPORT;
        packet.rawText = rawText == null ? "" : rawText;
        packet.locale = currentLocale();
        packet.payload = writeTeleportDestination(dest);
        return packet;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.action = buf.readByte();
        this.intentTypeOrdinal = buf.readByte();
        this.amount = buf.readLong();
        this.rawText = trim(ByteBufUtils.readUTF8String(buf), MAX_TEXT_LENGTH);
        this.target = trim(ByteBufUtils.readUTF8String(buf), MAX_TEXT_LENGTH);
        this.locale = trim(ByteBufUtils.readUTF8String(buf), 32);
        this.candidateNbt = ByteBufUtils.readTag(buf);
        this.payload = ByteBufUtils.readTag(buf);
        if (this.candidateNbt == null) {
            this.candidateNbt = new NBTTagCompound();
        }
        if (this.payload == null) {
            this.payload = new NBTTagCompound();
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(this.action);
        buf.writeByte(this.intentTypeOrdinal);
        buf.writeLong(this.amount);
        ByteBufUtils.writeUTF8String(buf, this.rawText == null ? "" : this.rawText);
        ByteBufUtils.writeUTF8String(buf, this.target == null ? "" : this.target);
        ByteBufUtils.writeUTF8String(buf, this.locale == null ? "" : this.locale);
        ByteBufUtils.writeTag(buf, this.candidateNbt == null ? new NBTTagCompound() : this.candidateNbt);
        ByteBufUtils.writeTag(buf, this.payload == null ? new NBTTagCompound() : this.payload);
    }

    private static String currentLocale() {
        try {
            String locale = FMLCommonHandler.instance()
                .getCurrentLanguage();
            return locale == null || locale.trim()
                .isEmpty() ? "zh_CN" : locale;
        } catch (Throwable ignored) {
            return "zh_CN";
        }
    }

    static NBTTagCompound writeCandidate(CraftingCandidate candidate) {
        NBTTagCompound tag = new NBTTagCompound();
        if (candidate == null) {
            return tag;
        }
        tag.setInteger("index", candidate.index);
        tag.setString("displayName", candidate.displayName);
        tag.setString("registryName", candidate.registryName);
        tag.setInteger("meta", candidate.meta);
        tag.setLong("amount", candidate.amount);
        tag.setTag("item", candidate.itemNbt);
        return tag;
    }

    static NBTTagCompound writeTeleportDestination(TeleportDestination dest) {
        NBTTagCompound tag = new NBTTagCompound();
        if (dest == null) {
            return tag;
        }
        tag.setInteger("index", dest.index);
        tag.setString("name", dest.name);
        tag.setInteger("dimensionId", dest.dimensionId);
        tag.setString("dimensionName", dest.dimensionName);
        tag.setInteger("x", dest.x);
        tag.setInteger("y", dest.y);
        tag.setInteger("z", dest.z);
        return tag;
    }

    static TeleportDestination readTeleportDestination(NBTTagCompound tag) {
        if (tag == null || !tag.hasKey("name")) {
            return null;
        }
        return new TeleportDestination(
            tag.getInteger("index"),
            tag.getString("name"),
            tag.getInteger("dimensionId"),
            tag.getString("dimensionName"),
            tag.getInteger("x"),
            tag.getInteger("y"),
            tag.getInteger("z"),
            null);
    }

    static CraftingCandidate readCandidate(NBTTagCompound tag) {
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

    private static NBTTagList writeOrderLines(List<AssistantOrderLine> lines) {
        NBTTagList list = new NBTTagList();
        if (lines == null) {
            return list;
        }
        int written = 0;
        for (AssistantOrderLine line : lines) {
            if (written++ >= MAX_BATCH_LINES) {
                break;
            }
            if (line == null) {
                continue;
            }
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("lineIndex", line.lineIndex);
            tag.setString("target", line.target == null ? "" : line.target);
            tag.setLong("amount", line.amount);
            CraftingCandidate selected = line.selectedOrFirstCandidate();
            if (selected != null) {
                tag.setTag("selected", writeCandidate(selected));
            }
            list.appendTag(tag);
        }
        return list;
    }

    private static List<AssistantOrderLine> readOrderLines(NBTTagCompound payload) {
        List<AssistantOrderLine> result = new ArrayList<AssistantOrderLine>();
        if (payload == null || !payload.hasKey("lines")) {
            return result;
        }
        NBTTagList list = payload.getTagList("lines", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            if (result.size() >= MAX_BATCH_LINES) {
                break;
            }
            AssistantOrderLine line = new AssistantOrderLine(
                tag.getInteger("lineIndex"),
                trim(tag.getString("target"), MAX_TEXT_LENGTH),
                tag.getLong("amount"));
            if (tag.hasKey("selected")) {
                line.selectedCandidate = readCandidate(tag.getCompoundTag("selected"));
            }
            result.add(line);
        }
        return result;
    }

    public static class Handler implements IMessageHandler<PacketAssistantAction, IMessage> {

        @Override
        public IMessage onMessage(final PacketAssistantAction message, final MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            AdvanceDataMonitor.LOG.info(
                "[ADM Assistant] PacketAssistantAction received: action={}, player={}, typeOrdinal={}, raw='{}', target='{}', amount={}, locale={}",
                message.action,
                player == null ? "null" : player.getCommandSenderName(),
                message.intentTypeOrdinal,
                safe(message.rawText),
                safe(message.target),
                message.amount,
                safe(message.locale));
            HandlerTick.enqueueServerTask(new Runnable() {

                @Override
                public void run() {
                    IMessage response = handleOnServerThread(message, player);
                    if (response != null && player != null) {
                        AdvanceDataMonitor.ADMCHANEL.sendTo(response, player);
                    }
                }
            });
            return null;
        }

        private IMessage handleOnServerThread(PacketAssistantAction message, EntityPlayerMP player) {
            if (message.action == REQUEST_CRAFT_CANDIDATES) {
                List<CraftingCandidate> candidates = AssistantServerServices
                    .craftingCandidates(player, message.rawText, message.target, message.amount);
                return PacketAssistantResponse.candidates(message.rawText, candidates);
            }
            if (message.action == REQUEST_BATCH_CANDIDATES) {
                List<AssistantOrderLine> lines = AssistantServerServices
                    .batchCraftingCandidates(player, message.rawText, readOrderLines(message.payload));
                return PacketAssistantResponse.batchCandidates(message.rawText, lines);
            }
            if (message.action == SUBMIT_CRAFT) {
                CraftingCandidate candidate = readCandidate(message.candidateNbt);
                String result = AssistantServerServices
                    .submitCraft(player, candidate, message.amount, message.rawText, message.locale);
                return PacketAssistantResponse.message(result);
            }
            if (message.action == SUBMIT_BATCH_CRAFT) {
                String result = AssistantServerServices
                    .submitBatchCraft(player, message.rawText, readOrderLines(message.payload), message.locale);
                return PacketAssistantResponse.message(result);
            }
            if (message.action == CANCEL_SERVER_JOBS) {
                String result = AssistantServerServices.cancelPendingJobs(player, message.locale);
                return PacketAssistantResponse.message(result);
            }
            if (message.action == REQUEST_WITHDRAW_CANDIDATES) {
                List<CraftingCandidate> candidates = AssistantServerServices
                    .withdrawCandidates(player, message.rawText, message.target, message.amount);
                return PacketAssistantResponse
                    .candidates(message.rawText, candidates, AssistantSessionKind.WITHDRAW_CANDIDATES);
            }
            if (message.action == REQUEST_BATCH_WITHDRAW_CANDIDATES) {
                List<AssistantOrderLine> lines = AssistantServerServices
                    .batchWithdrawCandidates(player, message.rawText, readOrderLines(message.payload));
                return PacketAssistantResponse
                    .batchCandidates(message.rawText, lines, AssistantSessionKind.WITHDRAW_BATCH_CANDIDATES);
            }
            if (message.action == SUBMIT_WITHDRAW) {
                CraftingCandidate candidate = readCandidate(message.candidateNbt);
                boolean confirmPartial = message.payload != null && message.payload.getBoolean("confirmPartial");
                com.imgood.advancedatamonitor.assistant.WithdrawSubmitOutcome outcome = AssistantServerServices
                    .submitWithdraw(player, candidate, message.amount, message.rawText, message.locale, confirmPartial);
                if (outcome.kind
                    == com.imgood.advancedatamonitor.assistant.WithdrawSubmitOutcome.Kind.PARTIAL_CONFIRM) {
                    return PacketAssistantResponse.withdrawPartial(
                        message.rawText,
                        outcome.message,
                        outcome.candidate,
                        outcome.requestedAmount,
                        outcome.fitAmount,
                        outcome.storageAmount);
                }
                return PacketAssistantResponse.message(outcome.message);
            }
            if (message.action == SUBMIT_BATCH_WITHDRAW) {
                String result = AssistantServerServices
                    .submitBatchWithdraw(player, message.rawText, readOrderLines(message.payload), message.locale);
                return PacketAssistantResponse.message(result);
            }
            if (message.action == REQUEST_TELEPORT_CANDIDATES) {
                return handleTeleportCandidates(message, player);
            }
            if (message.action == SUBMIT_TELEPORT) {
                return handleTeleportSubmit(message, player);
            }
            if (message.action == QUERY_RECIPE_CANDIDATE) {
                CraftingCandidate candidate = readCandidate(message.candidateNbt);
                String result = AssistantServerServices
                    .recipeDetailsForCandidate(player, candidate, message.amount, message.locale);
                return PacketAssistantResponse.message(result);
            }
            if (message.action == QUERY) {
                AssistantIntentType[] values = AssistantIntentType.values();
                AssistantIntentType type = message.intentTypeOrdinal >= 0 && message.intentTypeOrdinal < values.length
                    ? values[message.intentTypeOrdinal]
                    : AssistantIntentType.CHAT;
                if (type == AssistantIntentType.QUERY_RECIPE && (message.target == null || message.target.trim()
                    .isEmpty())) {
                    List<CraftingCandidate> candidates = AssistantServerServices
                        .craftingCandidates(player, message.rawText, message.target, message.amount);
                    return PacketAssistantResponse
                        .candidates(message.rawText, candidates, AssistantSessionKind.RECIPE_CANDIDATES);
                }
                if (type == AssistantIntentType.QUERY_ITEM_COUNT) {
                    List<CraftingCandidate> candidates = AssistantServerServices
                        .queryItemCount(player, message.rawText, message.target, message.locale);
                    return PacketAssistantResponse
                        .candidates(message.rawText, candidates, AssistantSessionKind.ITEM_COUNT_CANDIDATES);
                }
                if (type == AssistantIntentType.QUERY_STORAGE) {
                    int storageScope = message.payload != null && message.payload.hasKey("storageScope")
                        ? message.payload.getInteger("storageScope")
                        : 0;
                    List<CraftingCandidate> candidates = AssistantServerServices
                        .queryStorageCandidates(player, message.rawText, message.target, storageScope, message.locale);
                    return PacketAssistantResponse
                        .candidates(message.rawText, candidates, AssistantSessionKind.STORAGE_CANDIDATES);
                }
                String result = AssistantServerServices
                    .query(player, type, message.rawText, message.target, message.amount, message.locale);
                return PacketAssistantResponse.message(result);
            }
            return PacketAssistantResponse.message("Unknown assistant request.");
        }

        private IMessage handleTeleportCandidates(PacketAssistantAction message, EntityPlayerMP player) {
            List<TeleportDestination> allDestinations = TeleportService.scanDislocators(player);
            if (allDestinations.isEmpty()) {
                return PacketAssistantResponse.message(
                    text(
                        message.locale,
                        "背包中没有找到高级错位宝石（Advanced Dislocator），或没有已保存的传送点。",
                        "No Advanced Dislocator found in inventory, or no saved destinations."));
            }
            List<TeleportDestination> filtered = TeleportService.filterDestinations(allDestinations, message.target);
            if (filtered.isEmpty()) {
                return PacketAssistantResponse.teleportCandidates(message.rawText, allDestinations);
            }
            if (filtered.size() == 1 && message.target != null
                && !message.target.trim()
                    .isEmpty()) {
                // Exact single match: teleport directly
                String result = TeleportService.executeTeleport(player, filtered.get(0), message.locale);
                return PacketAssistantResponse.message(result);
            }
            return PacketAssistantResponse.teleportCandidates(message.rawText, filtered);
        }

        private IMessage handleTeleportSubmit(PacketAssistantAction message, EntityPlayerMP player) {
            TeleportDestination dest = readTeleportDestination(message.payload);
            if (dest == null) {
                return PacketAssistantResponse
                    .message(text(message.locale, "传送失败：无效的传送目标。", "Teleport failed: invalid destination."));
            }
            String result = TeleportService.executeTeleport(player, dest, message.locale);
            return PacketAssistantResponse.message(result);
        }

        private static String text(String locale, String zhText, String enText) {
            boolean zh = locale == null || locale.trim()
                .isEmpty()
                || locale.toLowerCase()
                    .startsWith("zh");
            return zh ? zhText : enText;
        }
    }

    private static String trim(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private static String safe(String text) {
        if (text == null) {
            return "";
        }
        return text.replace((char) 10, ' ')
            .replace((char) 13, ' ');
    }
}
