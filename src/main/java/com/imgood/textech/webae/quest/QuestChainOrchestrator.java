package com.imgood.textech.webae.quest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayerMP;

import com.imgood.textech.Config;
import com.imgood.textech.assistant.AssistantServerServices;
import com.imgood.textech.assistant.CraftingCandidate;
import com.imgood.textech.compat.bq.BqQuestingIdentity;
import com.imgood.textech.webae.craft.WebAeCraftService;
import com.imgood.textech.webae.dto.QuestAnalysisDto;
import com.imgood.textech.webae.dto.QuestAnalysisStepDto;
import com.imgood.textech.webae.dto.QuestChainPlanDto;
import com.imgood.textech.webae.dto.QuestChainStepDto;
import com.imgood.textech.webae.dto.QuestChainStepResultDto;
import com.imgood.textech.webae.dto.QuestChainSubmitResultDto;
import com.imgood.textech.webae.dto.QuestDetailDto;
import com.imgood.textech.webae.dto.QuestSubmitResultDto;

/**
 * Async chain submit that crafts missing items per quest then submits in topological order.
 */
public final class QuestChainOrchestrator {

    private static final ConcurrentHashMap<String, ChainJob> JOBS = new ConcurrentHashMap<String, ChainJob>();

    private QuestChainOrchestrator() {}

    public static QuestChainSubmitResultDto start(String ownerUuid, int networkId, String targetQuestId,
        boolean skipMissing, String cpuName, long waitTimeoutMs) {
        return start(ownerUuid, networkId, targetQuestId, skipMissing, cpuName, waitTimeoutMs, false);
    }

    public static QuestChainSubmitResultDto start(String ownerUuid, int networkId, String targetQuestId,
        boolean skipMissing, String cpuName, long waitTimeoutMs, boolean includeAllFluidContainers) {
        QuestChainSubmitResultDto dto = new QuestChainSubmitResultDto();
        dto.targetQuestId = targetQuestId;
        dto.dryRun = false;
        dto.jobId = UUID.randomUUID()
            .toString()
            .substring(0, 12);
        dto.complete = false;
        dto.phase = "crafting";
        dto.success = false;

        if (!Config.webQuestChainSubmitEnabled) {
            dto.complete = true;
            dto.phase = "done";
            dto.message = "Chain submit disabled";
            return dto;
        }

        EntityPlayerMP player = BqQuestingIdentity.resolvePlayer(ownerUuid);
        if (player == null) {
            dto.complete = true;
            dto.phase = "done";
            dto.message = "Player unavailable";
            return dto;
        }

        boolean includeAll = QuestFluidEquivalence.resolveIncludeAll(includeAllFluidContainers);
        QuestChainPlanDto plan = QuestChainService.buildPlan(ownerUuid, networkId, targetQuestId, includeAll);
        ChainJob job = new ChainJob();
        job.jobId = dto.jobId;
        job.ownerUuid = ownerUuid;
        job.networkId = networkId;
        job.targetQuestId = targetQuestId;
        job.skipMissing = skipMissing;
        job.cpuName = cpuName;
        job.includeAllFluidContainers = includeAll;
        job.deadlineMs = System.currentTimeMillis()
            + (waitTimeoutMs > 0 ? waitTimeoutMs : Config.webQuestCraftWaitTimeoutMs);

        for (QuestChainStepDto planned : plan.steps) {
            ChainQuestTrack track = new ChainQuestTrack();
            track.questId = planned.questId;
            track.name = planned.name;
            track.skipped = planned.skipped;
            track.skipReason = planned.skipReason;
            track.fullySatisfied = planned.fullySatisfied;
            if (planned.skipped) {
                track.done = true;
                track.action = "skipped";
                track.message = planned.skipReason;
            } else if (planned.fullySatisfied) {
                // Pre-lock available materials immediately.
                if (Config.webQuestEscrowEnabled && player != null) {
                    prelockTrack(job, track, player);
                }
                track.phase = "ready_submit";
            } else {
                track.phase = "need_craft";
                if (Config.webQuestEscrowEnabled && player != null) {
                    prelockTrack(job, track, player);
                }
                seedCraftOrders(job, track, ownerUuid, networkId, planned.questId, cpuName);
                if (track.orders.isEmpty()) {
                    if (skipMissing) {
                        if (track.escrowId != null) {
                            QuestInventoryEscrow.release(track.escrowId);
                            track.escrowId = null;
                        }
                        track.done = true;
                        track.action = "skipped";
                        track.message = "missing_items";
                    } else {
                        if (track.escrowId != null) {
                            QuestInventoryEscrow.release(track.escrowId);
                            track.escrowId = null;
                        }
                        track.done = true;
                        track.action = "failed";
                        track.message = "missing_items_uncraftable";
                        job.aborted = true;
                        job.abortMessage = "Cannot craft missing items for " + planned.name;
                    }
                }
            }
            job.queue.add(track);
        }

        JOBS.put(job.jobId, job);
        dto.message = "Chain craft started";
        dto.steps = snapshotSteps(job);
        return dto;
    }

    public static QuestChainSubmitResultDto poll(String jobId) {
        QuestChainSubmitResultDto dto = new QuestChainSubmitResultDto();
        dto.jobId = jobId;
        ChainJob job = JOBS.get(jobId);
        if (job == null) {
            dto.complete = true;
            dto.phase = "done";
            dto.message = "Job not found";
            return dto;
        }
        dto.targetQuestId = job.targetQuestId;
        dto.dryRun = false;

        if (job.aborted) {
            releaseJobEscrows(job);
            dto.complete = true;
            dto.success = false;
            dto.phase = "done";
            dto.message = job.abortMessage;
            dto.steps = snapshotSteps(job);
            JOBS.remove(jobId);
            return dto;
        }

        if (System.currentTimeMillis() > job.deadlineMs) {
            releaseJobEscrows(job);
            dto.complete = true;
            dto.success = false;
            dto.phase = "timeout";
            dto.message = "Chain craft wait timeout";
            dto.steps = snapshotSteps(job);
            JOBS.remove(jobId);
            return dto;
        }

        EntityPlayerMP player = BqQuestingIdentity.resolvePlayer(job.ownerUuid);

        for (ChainQuestTrack track : job.queue) {
            if (track.done) {
                continue;
            }

            if ("need_craft".equals(track.phase)) {
                int doneOrders = 0;
                boolean anyNew = false;
                for (OrderTrack order : track.orders) {
                    if (order.completed) {
                        doneOrders++;
                        continue;
                    }
                    if (player != null) {
                        AssistantServerServices.OrderProgressResult progress = AssistantServerServices
                            .resolveOrderProgress(player, job.cpuName, order.itemName, order.submittedAt);
                        if (progress != null && progress.completed) {
                            order.completed = true;
                            doneOrders++;
                            anyNew = true;
                        }
                    }
                }
                if (anyNew && player != null && Config.webQuestEscrowEnabled) {
                    appendTrackLock(job, track, player);
                }
                if (doneOrders < track.orders.size()) {
                    dto.phase = "crafting";
                    dto.complete = false;
                    dto.message = "Crafting " + track.name + " (" + doneOrders + "/" + track.orders.size() + ")";
                    dto.steps = snapshotSteps(job);
                    return dto;
                }
                track.phase = "ready_submit";
            }

            if ("ready_submit".equals(track.phase)) {
                dto.phase = "locking";
                QuestSubmitResultDto submit = submitQuestWithEscrow(job, track, player);
                track.submitResult = submit;
                if (submit != null && submit.success) {
                    track.done = true;
                    track.action = "submitted";
                    track.message = submit.message != null ? submit.message : "ok";
                } else {
                    track.done = true;
                    track.action = "failed";
                    track.message = submit != null ? submit.message : "submit_failed";
                    if (!job.skipMissing) {
                        job.aborted = true;
                        job.abortMessage = track.message;
                        dto.complete = true;
                        dto.success = false;
                        dto.phase = "escrow_failed".equals(track.phase) ? "escrow_failed" : "done";
                        dto.message = job.abortMessage;
                        dto.steps = snapshotSteps(job);
                        JOBS.remove(jobId);
                        return dto;
                    }
                }
            }
        }

        boolean allDone = true;
        boolean allOk = true;
        for (ChainQuestTrack track : job.queue) {
            if (!track.done) {
                allDone = false;
            }
            if ("failed".equals(track.action)) {
                allOk = false;
            }
        }
        if (!allDone) {
            dto.complete = false;
            dto.phase = "submitting";
            dto.message = "Submitting chain";
            dto.steps = snapshotSteps(job);
            return dto;
        }

        dto.complete = true;
        dto.success = allOk;
        dto.phase = "done";
        dto.message = allOk ? "Chain complete" : "Chain finished with failures";
        dto.steps = snapshotSteps(job);
        JOBS.remove(jobId);
        return dto;
    }

    private static void prelockTrack(ChainJob job, ChainQuestTrack track, EntityPlayerMP player) {
        QuestDetailDto detail = QuestDataCollector.collectQuestDetail(track.questId, player);
        QuestAnalysisDto analysis = QuestRequirementAnalyzer.analyze(
            job.ownerUuid,
            job.networkId,
            detail,
            job.includeAllFluidContainers);
        QuestInventoryEscrow.LockResult lock = QuestInventoryEscrow.lockPartial(
            job.ownerUuid,
            job.networkId,
            player,
            analysis.steps,
            job.includeAllFluidContainers,
            null);
        if (lock.success && lock.escrowId != null && !lock.escrowId.isEmpty()) {
            track.escrowId = lock.escrowId;
        }
    }

    private static void appendTrackLock(ChainJob job, ChainQuestTrack track, EntityPlayerMP player) {
        QuestDetailDto detail = QuestDataCollector.collectQuestDetail(track.questId, player);
        QuestAnalysisDto analysis = QuestRequirementAnalyzer.analyze(
            job.ownerUuid,
            job.networkId,
            detail,
            job.includeAllFluidContainers);
        if (track.escrowId != null && !track.escrowId.isEmpty()) {
            QuestInventoryEscrow.appendLock(
                track.escrowId,
                job.ownerUuid,
                job.networkId,
                player,
                analysis.steps,
                job.includeAllFluidContainers);
        } else {
            prelockTrack(job, track, player);
        }
    }

    private static void seedCraftOrders(ChainJob job, ChainQuestTrack track, String ownerUuid, int networkId,
        String questId, String cpuName) {
        EntityPlayerMP player = BqQuestingIdentity.resolvePlayer(ownerUuid);
        if (player == null) {
            return;
        }
        QuestDetailDto detail = QuestDataCollector.collectQuestDetail(questId, player);
        QuestAnalysisDto analysis = QuestRequirementAnalyzer.analyze(
            ownerUuid,
            networkId,
            detail,
            job.includeAllFluidContainers);
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
            String craftResult = WebAeCraftService.submitCraft(
                ownerUuid,
                networkId,
                candidate,
                step.missing,
                step.registryName,
                "en_US",
                cpuName);
            OrderTrack order = new OrderTrack();
            order.itemName = candidate.displayName != null ? candidate.displayName : step.registryName;
            order.amount = step.missing;
            order.craftMessage = craftResult;
            order.submittedAt = System.currentTimeMillis();
            track.orders.add(order);
        }
    }

    private static QuestSubmitResultDto submitQuestWithEscrow(ChainJob job, ChainQuestTrack track,
        EntityPlayerMP player) {
        if (player == null) {
            QuestSubmitResultDto fail = new QuestSubmitResultDto();
            fail.questId = track.questId;
            fail.success = false;
            fail.message = "Player unavailable for escrow";
            track.phase = "escrow_failed";
            return fail;
        }
        String escrowId = track.escrowId;
        QuestDetailDto detail = QuestDataCollector.collectQuestDetail(track.questId, player);
        QuestAnalysisDto analysis = QuestRequirementAnalyzer.analyze(
            job.ownerUuid,
            job.networkId,
            detail,
            job.includeAllFluidContainers);
        if (Config.webQuestEscrowEnabled) {
            if (escrowId != null && !escrowId.isEmpty()) {
                QuestInventoryEscrow.LockResult append = QuestInventoryEscrow.appendLock(
                    escrowId,
                    job.ownerUuid,
                    job.networkId,
                    player,
                    analysis.steps,
                    job.includeAllFluidContainers);
                if (!append.success) {
                    QuestInventoryEscrow.release(escrowId);
                    QuestSubmitResultDto fail = new QuestSubmitResultDto();
                    fail.questId = track.questId;
                    fail.success = false;
                    fail.message = append.message != null ? append.message : "Escrow append failed";
                    track.phase = "escrow_failed";
                    track.escrowId = null;
                    return fail;
                }
                if (!QuestInventoryEscrow.allStepsCovered(analysis.steps, QuestInventoryEscrow.get(escrowId))) {
                    QuestInventoryEscrow.release(escrowId);
                    QuestSubmitResultDto fail = new QuestSubmitResultDto();
                    fail.questId = track.questId;
                    fail.success = false;
                    fail.message = "Insufficient AE stock for escrow";
                    track.phase = "escrow_failed";
                    track.escrowId = null;
                    return fail;
                }
            } else {
                QuestInventoryEscrow.LockResult lock = QuestInventoryEscrow.lock(
                    job.ownerUuid,
                    job.networkId,
                    player,
                    analysis.steps,
                    job.includeAllFluidContainers);
                if (!lock.success) {
                    QuestSubmitResultDto fail = new QuestSubmitResultDto();
                    fail.questId = track.questId;
                    fail.success = false;
                    fail.message = lock.message != null ? lock.message : "Escrow lock failed";
                    track.phase = "escrow_failed";
                    return fail;
                }
                escrowId = lock.escrowId;
                track.escrowId = escrowId;
            }
        }
        QuestSubmitResultDto submit;
        if (escrowId != null && !escrowId.isEmpty()) {
            submit = QuestSubmitService.submitFromEscrow(
                job.ownerUuid,
                job.networkId,
                track.questId,
                escrowId,
                null,
                job.includeAllFluidContainers);
        } else {
            submit = QuestSubmitService.submit(
                job.ownerUuid,
                job.networkId,
                track.questId,
                false,
                null,
                job.includeAllFluidContainers);
        }
        if ((submit == null || !submit.success) && escrowId != null && !escrowId.isEmpty()) {
            QuestInventoryEscrow.release(escrowId);
        }
        track.escrowId = null;
        return submit;
    }

    private static void releaseJobEscrows(ChainJob job) {
        if (job == null) {
            return;
        }
        for (ChainQuestTrack track : job.queue) {
            if (track != null && track.escrowId != null && !track.escrowId.isEmpty()) {
                QuestInventoryEscrow.release(track.escrowId);
                track.escrowId = null;
            }
        }
    }

    private static List<QuestChainStepResultDto> snapshotSteps(ChainJob job) {
        List<QuestChainStepResultDto> list = new ArrayList<QuestChainStepResultDto>();
        for (ChainQuestTrack track : job.queue) {
            QuestChainStepResultDto s = new QuestChainStepResultDto();
            s.questId = track.questId;
            s.name = track.name;
            s.action = track.action != null ? track.action : "pending";
            s.message = track.message != null ? track.message : "";
            s.submitResult = track.submitResult;
            list.add(s);
        }
        return list;
    }

    private static final class ChainJob {
        String jobId;
        String ownerUuid;
        int networkId;
        String targetQuestId;
        boolean skipMissing;
        String cpuName;
        long deadlineMs;
        boolean aborted;
        String abortMessage = "";
        boolean includeAllFluidContainers;
        List<ChainQuestTrack> queue = new ArrayList<ChainQuestTrack>();
    }

    private static final class ChainQuestTrack {
        String questId;
        String name;
        boolean skipped;
        String skipReason;
        boolean fullySatisfied;
        String phase = "pending";
        boolean done;
        String action = "pending";
        String message = "";
        String escrowId;
        QuestSubmitResultDto submitResult;
        List<OrderTrack> orders = new ArrayList<OrderTrack>();
    }

    private static final class OrderTrack {
        String itemName;
        long amount;
        String craftMessage;
        long submittedAt;
        boolean completed;
    }
}
