package com.imgood.textech.webae.quest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayerMP;

import com.imgood.textech.Config;
import com.imgood.textech.assistant.AssistantServerServices;
import com.imgood.textech.assistant.CraftingCandidate;
import com.imgood.textech.webae.craft.WebAeCraftService;
import com.imgood.textech.webae.dto.QuestAnalysisDto;
import com.imgood.textech.webae.dto.QuestAnalysisStepDto;
import com.imgood.textech.webae.dto.QuestCraftJobDto;
import com.imgood.textech.webae.dto.QuestDetailDto;
import com.imgood.textech.webae.dto.QuestSubmitResultDto;

/**
 * Orchestrates AE craft orders then quest submission (Phase C).
 */
public final class QuestCraftOrchestrator {

    private static final ConcurrentHashMap<String, CraftJob> JOBS = new ConcurrentHashMap<String, CraftJob>();

    private QuestCraftOrchestrator() {}

    public static QuestCraftJobDto start(String ownerUuid, int networkId, String questId, String cpuName,
        long waitTimeoutMs) {
        QuestCraftJobDto dto = new QuestCraftJobDto();
        dto.questId = questId;
        dto.jobId = UUID.randomUUID()
            .toString()
            .substring(0, 12);
        dto.phase = "crafting";
        dto.complete = false;
        dto.success = false;

        EntityPlayerMP player = com.imgood.textech.compat.bq.BqQuestingIdentity.resolvePlayer(ownerUuid);
        if (player == null) {
            dto.complete = true;
            dto.message = "Player unavailable";
            return dto;
        }

        QuestDetailDto detail = QuestDataCollector.collectQuestDetail(questId, player);
        QuestAnalysisDto analysis = QuestRequirementAnalyzer.analyze(ownerUuid, networkId, detail);

        CraftJob job = new CraftJob();
        job.jobId = dto.jobId;
        job.ownerUuid = ownerUuid;
        job.networkId = networkId;
        job.questId = questId;
        job.cpuName = cpuName;
        job.deadlineMs = System.currentTimeMillis() + (waitTimeoutMs > 0 ? waitTimeoutMs : Config.webQuestCraftWaitTimeoutMs);
        job.submittedAt = System.currentTimeMillis();

        for (QuestAnalysisStepDto step : analysis.steps) {
            if (step.complete || !step.webCapable || step.missing <= 0) {
                continue;
            }
            if (step.registryName == null || step.registryName.isEmpty()) {
                continue;
            }
            List<CraftingCandidate> candidates = WebAeCraftService.craftingCandidates(
                ownerUuid,
                networkId,
                step.registryName,
                step.registryName,
                step.missing);
            if (candidates == null || candidates.isEmpty()) {
                continue;
            }
            CraftingCandidate candidate = candidates.get(0);
            String orderJobId = UUID.randomUUID()
                .toString()
                .substring(0, 8);
            String craftResult = WebAeCraftService.submitCraft(
                ownerUuid,
                networkId,
                candidate,
                step.missing,
                step.registryName,
                "en_US",
                cpuName);
            OrderTrack track = new OrderTrack();
            track.orderJobId = orderJobId;
            track.itemName = candidate.displayName != null ? candidate.displayName : step.registryName;
            track.amount = step.missing;
            track.craftMessage = craftResult;
            track.submittedAt = System.currentTimeMillis();
            job.orders.add(track);
        }

        job.ordersTotal = job.orders.size();
        dto.ordersTotal = job.ordersTotal;
        if (job.orders.isEmpty()) {
            return finishWithEscrowSubmit(dto, job);
        }

        JOBS.put(job.jobId, job);
        dto.message = "Craft orders submitted: " + job.ordersTotal;
        return dto;
    }

    public static QuestCraftJobDto poll(String jobId) {
        QuestCraftJobDto dto = new QuestCraftJobDto();
        dto.jobId = jobId;
        CraftJob job = JOBS.get(jobId);
        if (job == null) {
            dto.complete = true;
            dto.message = "Job not found";
            return dto;
        }
        dto.questId = job.questId;
        dto.ordersTotal = job.ordersTotal;

        EntityPlayerMP player = com.imgood.textech.compat.bq.BqQuestingIdentity.resolvePlayer(job.ownerUuid);
        int done = 0;
        for (OrderTrack track : job.orders) {
            if (track.completed) {
                done++;
                continue;
            }
            if (player != null) {
                AssistantServerServices.OrderProgressResult progress = AssistantServerServices.resolveOrderProgress(
                    player,
                    job.cpuName,
                    track.itemName,
                    track.submittedAt);
                if (progress != null && progress.completed) {
                    track.completed = true;
                    done++;
                }
            }
        }
        dto.ordersDone = done;

        if (System.currentTimeMillis() > job.deadlineMs) {
            if (job.escrowId != null && !job.escrowId.isEmpty()) {
                QuestInventoryEscrow.release(job.escrowId);
                job.escrowId = null;
            }
            dto.complete = true;
            dto.success = false;
            dto.message = "Craft wait timeout";
            dto.phase = "timeout";
            JOBS.remove(jobId);
            return dto;
        }

        if (done < job.ordersTotal) {
            dto.phase = "crafting";
            dto.complete = false;
            dto.message = "Crafting " + done + "/" + job.ordersTotal;
            return dto;
        }

        return finishWithEscrowSubmit(dto, job);
    }

    public static QuestCraftJobDto resolveSubmitJob(String jobId) {
        return poll(jobId);
    }

    private static QuestCraftJobDto finishWithEscrowSubmit(QuestCraftJobDto dto, CraftJob job) {
        dto.phase = "locking";
        dto.questId = job.questId;
        dto.ordersTotal = job.ordersTotal;
        EntityPlayerMP player = com.imgood.textech.compat.bq.BqQuestingIdentity.resolvePlayer(job.ownerUuid);
        if (player == null) {
            dto.complete = true;
            dto.success = false;
            dto.phase = "escrow_failed";
            dto.message = "Player unavailable for escrow";
            JOBS.remove(job.jobId);
            return dto;
        }

        QuestDetailDto detail = QuestDataCollector.collectQuestDetail(job.questId, player);
        QuestAnalysisDto analysis = QuestRequirementAnalyzer.analyze(job.ownerUuid, job.networkId, detail);
        String escrowId = null;
        if (Config.webQuestEscrowEnabled) {
            QuestInventoryEscrow.LockResult lock = QuestInventoryEscrow.lock(
                job.ownerUuid,
                job.networkId,
                player,
                analysis.steps);
            if (!lock.success) {
                dto.complete = true;
                dto.success = false;
                dto.phase = "escrow_failed";
                dto.message = lock.message != null ? lock.message : "Escrow lock failed";
                JOBS.remove(job.jobId);
                return dto;
            }
            escrowId = lock.escrowId;
            job.escrowId = escrowId;
        }

        dto.phase = "submitting";
        QuestSubmitResultDto submit;
        if (escrowId != null && !escrowId.isEmpty()) {
            submit = QuestSubmitService.submitFromEscrow(job.ownerUuid, job.networkId, job.questId, escrowId, null);
        } else {
            submit = QuestSubmitService.submit(job.ownerUuid, job.networkId, job.questId, false, null);
        }
        if (!submit.success && escrowId != null && !escrowId.isEmpty()) {
            QuestInventoryEscrow.release(escrowId);
        }
        job.escrowId = null;
        dto.submitResult = submit;
        dto.complete = true;
        dto.success = submit.success;
        dto.message = submit.message;
        dto.phase = "done";
        JOBS.remove(job.jobId);
        return dto;
    }

    private static final class CraftJob {
        String jobId;
        String ownerUuid;
        int networkId;
        String questId;
        String cpuName;
        long submittedAt;
        long deadlineMs;
        int ordersTotal;
        String escrowId;
        List<OrderTrack> orders = new ArrayList<OrderTrack>();
    }

    private static final class OrderTrack {
        String orderJobId;
        String itemName;
        long amount;
        String craftMessage;
        long submittedAt;
        boolean completed;
    }
}
