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
            AdvanceDataMonitor.ADMCHANEL
                .sendTo(PacketAssistantResponse.candidates(rawText, all, effectiveKind, 0, 1, 0, false), player);
            return;
        }
        int batchSize = Math.max(1, Config.assistantQueryCandidateBatchSize);
        int totalCount = all.size();
        int batchCount = (totalCount + batchSize - 1) / batchSize;
        AdvanceDataMonitor.LOG.info(
            "[ADM Assistant] Sending candidate batches: total={}, batches={}, batchSize={}, truncated={}",
            totalCount,
            batchCount,
            batchSize,
            truncated);
        for (int batchIndex = 0; batchIndex < batchCount; batchIndex++) {
            int from = batchIndex * batchSize;
            int to = Math.min(from + batchSize, totalCount);
            List<CraftingCandidate> batch = new ArrayList<CraftingCandidate>(all.subList(from, to));
            AdvanceDataMonitor.ADMCHANEL.sendTo(
                PacketAssistantResponse
                    .candidates(rawText, batch, effectiveKind, batchIndex, batchCount, totalCount, batchIndex > 0),
                player);
        }
        if (truncated) {
            AdvanceDataMonitor.ADMCHANEL
                .sendTo(PacketAssistantResponse.message(truncationMessage(locale, totalCount)), player);
        }
    }

    private static String truncationMessage(String locale, int shownCount) {
        return I18N_TRUNCATED_PREFIX + shownCount + "|" + Config.assistantMaxQueryCandidates;
    }
}
