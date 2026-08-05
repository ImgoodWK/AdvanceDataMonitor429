package com.imgood.textech.assistant;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.network.packet.PacketAssistantResponse;

public final class AssistantCandidateDelivery {

    /** Client decodes via {@code I18n.format("adm.ai.assistant.candidates_truncated", shown, limit)}. */
    public static final String I18N_TRUNCATED_PREFIX = "adm.ai.assistant.candidates_truncated|";
    /** Client decodes this marker without exposing a hard-coded server locale. */
    public static final String I18N_PACKET_LIMIT_MESSAGE = "adm.ai.assistant.candidates_packet_limit";
    private static final int TARGET_PACKET_BODY_BYTES = 28 * 1024;

    private AssistantCandidateDelivery() {}

    public static void sendCandidateBatches(EntityPlayerMP player, String rawText, CandidateQueryResult result,
        AssistantSessionKind kind, String locale) {
        if (player == null) {
            return;
        }
        List<CraftingCandidate> all = result == null || result.candidates == null ? new ArrayList<CraftingCandidate>()
            : new ArrayList<CraftingCandidate>(result.candidates);
        boolean truncated = result != null && result.truncated;
        AssistantSessionKind effectiveKind = kind == null ? AssistantSessionKind.ORDER_CANDIDATES : kind;
        if (all.isEmpty()) {
            PacketAssistantResponse empty = PacketAssistantResponse
                .candidates(rawText, all, effectiveKind, 0, 1, 0, false, 1, 0);
            if (empty.fitsPacketBudget(PacketAssistantResponse.MAX_PACKET_BODY_BYTES)) {
                AdvanceDataMonitor.ADMCHANEL.sendTo(empty, player);
            } else {
                sendPacketLimitMessage(player);
            }
            return;
        }
        int batchSize = Math.max(1, Config.assistantQueryCandidateBatchSize);
        int totalCount = all.size();
        List<CandidateSlice> slices = new ArrayList<CandidateSlice>();
        for (int from = 0; from < totalCount; from += batchSize) {
            int to = Math.min(from + batchSize, totalCount);
            if (!splitToWireSafeSlices(rawText, effectiveKind, all, from, to, totalCount, slices)) {
                AdvanceDataMonitor.LOG.warn(
                    "[ADM Assistant] Candidate {} cannot fit the bounded response packet.",
                    from);
                sendPacketLimitMessage(player);
                return;
            }
        }
        int batchCount = slices.size();
        AdvanceDataMonitor.LOG.info(
            "[ADM Assistant] Sending candidate batches: total={}, batches={}, batchSize={}, truncated={}",
            totalCount,
            batchCount,
            batchSize,
            truncated);
        List<PacketAssistantResponse> packets = new ArrayList<PacketAssistantResponse>(batchCount);
        for (int batchIndex = 0; batchIndex < batchCount; batchIndex++) {
            CandidateSlice slice = slices.get(batchIndex);
            PacketAssistantResponse packet = PacketAssistantResponse.candidates(
                rawText,
                slice.candidates,
                effectiveKind,
                batchIndex,
                batchCount,
                totalCount,
                batchIndex > 0,
                slice.rangeStart,
                slice.rangeEnd);
            if (!packet.fitsPacketBudget(PacketAssistantResponse.MAX_PACKET_BODY_BYTES)) {
                sendPacketLimitMessage(player);
                return;
            }
            packets.add(packet);
        }
        for (PacketAssistantResponse packet : packets) {
            AdvanceDataMonitor.ADMCHANEL.sendTo(packet, player);
        }
        if (truncated) {
            AdvanceDataMonitor.ADMCHANEL
                .sendTo(PacketAssistantResponse.message(truncationMessage(locale, totalCount)), player);
        }
    }

    private static String truncationMessage(String locale, int shownCount) {
        return I18N_TRUNCATED_PREFIX + shownCount + "|" + Config.assistantMaxQueryCandidates;
    }

    private static boolean splitToWireSafeSlices(String rawText, AssistantSessionKind kind,
        List<CraftingCandidate> all, int from, int to, int totalCount, List<CandidateSlice> output) {
        List<CraftingCandidate> candidates = new ArrayList<CraftingCandidate>(all.subList(from, to));
        PacketAssistantResponse probe = PacketAssistantResponse
            .candidates(rawText, candidates, kind, 0, 1, totalCount, false, from + 1, to);
        if (probe.fitsPacketBudget(TARGET_PACKET_BODY_BYTES)) {
            output.add(new CandidateSlice(candidates, from + 1, to));
            return true;
        }
        if (to - from <= 1) {
            return false;
        }
        int middle = from + (to - from) / 2;
        return splitToWireSafeSlices(rawText, kind, all, from, middle, totalCount, output)
            && splitToWireSafeSlices(rawText, kind, all, middle, to, totalCount, output);
    }

    private static void sendPacketLimitMessage(EntityPlayerMP player) {
        AdvanceDataMonitor.ADMCHANEL
            .sendTo(PacketAssistantResponse.message(I18N_PACKET_LIMIT_MESSAGE), player);
    }

    private static final class CandidateSlice {

        final List<CraftingCandidate> candidates;
        final int rangeStart;
        final int rangeEnd;

        CandidateSlice(List<CraftingCandidate> candidates, int rangeStart, int rangeEnd) {
            this.candidates = candidates;
            this.rangeStart = rangeStart;
            this.rangeEnd = rangeEnd;
        }
    }
}
