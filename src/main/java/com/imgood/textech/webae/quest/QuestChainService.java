package com.imgood.textech.webae.quest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayerMP;

import com.imgood.textech.Config;
import com.imgood.textech.compat.bq.BqQuestingIdentity;
import com.imgood.textech.webae.dto.QuestAnalysisDto;
import com.imgood.textech.webae.dto.QuestAnalysisStepDto;
import com.imgood.textech.webae.dto.QuestChainPlanDto;
import com.imgood.textech.webae.dto.QuestChainStepDto;
import com.imgood.textech.webae.dto.QuestChainStepResultDto;
import com.imgood.textech.webae.dto.QuestChainSubmitResultDto;
import com.imgood.textech.webae.dto.QuestDetailDto;
import com.imgood.textech.webae.dto.QuestSubmitResultDto;

/**
 * Builds prerequisite chains and executes ordered submit (optionally with skip-missing).
 * Craft-then-submit for chains is handled by {@link QuestChainOrchestrator}.
 */
public final class QuestChainService {

    private QuestChainService() {}

    public static QuestChainPlanDto buildPlan(String ownerUuid, int networkId, String targetQuestId) {
        return buildPlan(ownerUuid, networkId, targetQuestId, false);
    }

    public static QuestChainPlanDto buildPlan(String ownerUuid, int networkId, String targetQuestId,
        boolean includeAllFluidContainers) {
        QuestChainPlanDto plan = new QuestChainPlanDto();
        plan.targetQuestId = targetQuestId;
        plan.networkId = networkId;
        plan.chainEnabled = Config.webQuestChainSubmitEnabled;

        if (!Config.webQuestChainSubmitEnabled) {
            return plan;
        }

        EntityPlayerMP player = BqQuestingIdentity.resolvePlayer(ownerUuid);
        if (player == null || targetQuestId == null) {
            return plan;
        }

        boolean includeAll = QuestFluidEquivalence.resolveIncludeAll(includeAllFluidContainers);
        List<String> ordered = topologicalPrereqsThenTarget(targetQuestId, player);
        for (String questId : ordered) {
            QuestDetailDto detail = QuestDataCollector.collectQuestDetail(questId, player);
            QuestChainStepDto step = new QuestChainStepDto();
            step.questId = questId;
            step.name = detail.name != null ? detail.name : "";
            step.state = detail.state != null ? detail.state : "LOCKED";
            step.canSubmit = detail.canSubmit;
            step.target = questId.equals(targetQuestId);

            if ("COMPLETED".equals(step.state)) {
                step.skipped = true;
                step.skipReason = "already_completed";
                plan.steps.add(step);
                continue;
            }
            if ("UNCLAIMED".equals(step.state)) {
                step.skipped = true;
                step.skipReason = "unclaimed_in_game";
                plan.steps.add(step);
                continue;
            }
            if ("LOCKED".equals(step.state) && !step.target) {
                // May unlock after earlier steps; still include analysis when canSubmit becomes true later.
                step.skipped = !detail.canSubmit;
                if (step.skipped) {
                    step.skipReason = "locked";
                }
            }

            QuestAnalysisDto analysis = QuestRequirementAnalyzer.analyze(ownerUuid, networkId, detail, includeAll);
            step.analysis = analysis;
            int missingKinds = 0;
            boolean anyWeb = false;
            boolean allCraftable = true;
            boolean anyMissing = false;
            for (QuestAnalysisStepDto s : analysis.steps) {
                if (s.complete || !s.webCapable) {
                    continue;
                }
                anyWeb = true;
                if (s.missing > 0 || s.fluidMissing > 0) {
                    anyMissing = true;
                    missingKinds++;
                    if (s.missing > 0 && s.craftable < s.missing) {
                        allCraftable = false;
                    }
                    if (s.fluidMissing > 0) {
                        allCraftable = false;
                    }
                }
            }
            if (!anyWeb && !step.skipped) {
                step.skipped = true;
                step.skipReason = "in_game_only";
            }
            step.missingItemKinds = missingKinds;
            step.fullySatisfied = !anyMissing;
            step.craftable = anyMissing && allCraftable;
            if (!step.skipped && !detail.canSubmit && !step.target) {
                step.skipped = true;
                if (step.skipReason == null || step.skipReason.isEmpty()) {
                    step.skipReason = "cannot_submit";
                }
            }
            plan.steps.add(step);
        }
        return plan;
    }

    /**
     * Synchronous chain submit without waiting on AE craft jobs. When craftMissing is true,
     * callers should use {@link QuestChainOrchestrator#start} instead.
     */
    public static QuestChainSubmitResultDto submitSync(String ownerUuid, int networkId, String targetQuestId,
        boolean dryRun, boolean skipMissing) {
        return submitSync(ownerUuid, networkId, targetQuestId, dryRun, skipMissing, false);
    }

    public static QuestChainSubmitResultDto submitSync(String ownerUuid, int networkId, String targetQuestId,
        boolean dryRun, boolean skipMissing, boolean includeAllFluidContainers) {
        QuestChainSubmitResultDto result = new QuestChainSubmitResultDto();
        result.targetQuestId = targetQuestId;
        result.dryRun = dryRun;
        result.complete = true;
        result.phase = "done";

        if (!Config.webQuestChainSubmitEnabled) {
            result.message = "Chain submit disabled";
            return result;
        }
        if (!Config.webQuestSubmitEnabled) {
            result.message = "Quest submit disabled";
            return result;
        }

        boolean includeAll = QuestFluidEquivalence.resolveIncludeAll(includeAllFluidContainers);
        QuestChainPlanDto plan = buildPlan(ownerUuid, networkId, targetQuestId, includeAll);
        boolean allOk = true;

        for (QuestChainStepDto planned : plan.steps) {
            QuestChainStepResultDto stepResult = new QuestChainStepResultDto();
            stepResult.questId = planned.questId;
            stepResult.name = planned.name;

            if (planned.skipped) {
                stepResult.action = "skipped";
                stepResult.message = planned.skipReason;
                result.steps.add(stepResult);
                continue;
            }

            if (!planned.fullySatisfied) {
                if (skipMissing) {
                    stepResult.action = "skipped";
                    stepResult.message = "missing_items";
                    result.steps.add(stepResult);
                    continue;
                }
                stepResult.action = "failed";
                stepResult.message = "missing_items";
                result.steps.add(stepResult);
                allOk = false;
                result.success = false;
                result.message = "Missing items for " + planned.name;
                return result;
            }

            if (dryRun) {
                stepResult.action = "submitted";
                stepResult.message = "dry_run_ok";
                result.steps.add(stepResult);
                continue;
            }

            QuestSubmitResultDto submit = QuestSubmitService
                .submit(ownerUuid, networkId, planned.questId, false, null, includeAll);
            stepResult.submitResult = submit;
            if (submit != null && submit.success) {
                stepResult.action = "submitted";
                stepResult.message = submit.message != null ? submit.message : "ok";
            } else {
                stepResult.action = "failed";
                stepResult.message = submit != null ? submit.message : "submit_failed";
                allOk = false;
                result.steps.add(stepResult);
                if (!skipMissing) {
                    result.success = false;
                    result.message = stepResult.message;
                    return result;
                }
            }
            result.steps.add(stepResult);
        }

        result.success = allOk;
        result.message = allOk ? "Chain complete" : "Chain finished with failures";
        return result;
    }

    /**
     * Topological order of all transitive prerequisites, then the target quest.
     */
    public static List<String> topologicalPrereqsThenTarget(String targetQuestId, EntityPlayerMP player) {
        List<String> ordered = new ArrayList<String>();
        if (targetQuestId == null || player == null) {
            return ordered;
        }

        Set<String> visited = new HashSet<String>();
        Map<String, List<String>> deps = new HashMap<String, List<String>>();
        collectClosure(targetQuestId, player, visited, deps);

        Map<String, Integer> indegree = new HashMap<String, Integer>();
        for (String id : visited) {
            indegree.put(id, Integer.valueOf(0));
        }
        for (Map.Entry<String, List<String>> e : deps.entrySet()) {
            String quest = e.getKey();
            List<String> prereqs = e.getValue();
            if (prereqs == null) {
                continue;
            }
            indegree.put(quest, Integer.valueOf(prereqs.size()));
            for (String p : prereqs) {
                if (!indegree.containsKey(p)) {
                    indegree.put(p, Integer.valueOf(0));
                }
            }
        }

        Queue<String> queue = new LinkedList<String>();
        for (Map.Entry<String, Integer> e : indegree.entrySet()) {
            if (e.getValue()
                .intValue() == 0) {
                queue.add(e.getKey());
            }
        }
        while (!queue.isEmpty()) {
            String id = queue.poll();
            ordered.add(id);
            for (Map.Entry<String, List<String>> e : deps.entrySet()) {
                if (e.getValue() != null && e.getValue()
                    .contains(id)) {
                    int next = indegree.get(e.getKey())
                        .intValue() - 1;
                    indegree.put(e.getKey(), Integer.valueOf(next));
                    if (next == 0) {
                        queue.add(e.getKey());
                    }
                }
            }
        }
        // Cycle fallback: append any remaining in visit order
        for (String id : visited) {
            if (!ordered.contains(id)) {
                ordered.add(id);
            }
        }
        // Ensure target is last
        ordered.remove(targetQuestId);
        ordered.add(targetQuestId);
        return ordered;
    }

    private static void collectClosure(String questId, EntityPlayerMP player, Set<String> visited,
        Map<String, List<String>> deps) {
        if (questId == null || visited.contains(questId)) {
            return;
        }
        visited.add(questId);
        QuestDetailDto detail = QuestDataCollector.collectQuestDetail(questId, player);
        List<String> prereqs = new ArrayList<String>();
        if (detail.requirementQuestIds != null) {
            for (String req : detail.requirementQuestIds) {
                if (req == null || req.isEmpty()) {
                    continue;
                }
                prereqs.add(req);
                collectClosure(req, player, visited, deps);
            }
        }
        deps.put(questId, prereqs);
    }
}
