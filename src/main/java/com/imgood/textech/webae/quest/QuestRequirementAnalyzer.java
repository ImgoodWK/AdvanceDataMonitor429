package com.imgood.textech.webae.quest;

import com.imgood.textech.webae.cache.SnapshotCache;
import com.imgood.textech.webae.cache.SnapshotScheduler;
import com.imgood.textech.webae.craft.CraftTreeCalculator;
import com.imgood.textech.webae.craft.CraftTreeNodeDto;
import com.imgood.textech.webae.dto.QuestAnalysisDto;
import com.imgood.textech.webae.dto.QuestAnalysisStepDto;
import com.imgood.textech.webae.dto.QuestDetailDto;
import com.imgood.textech.webae.dto.QuestTaskDto;
import com.imgood.textech.webae.dto.StorageDto;
import com.imgood.textech.webae.dto.StorageDto.ItemEntry;
import com.imgood.textech.webae.quest.QuestFluidEquivalence.StockBreakdown;

/**
 * Compares quest task requirements against AE snapshot + craft tree.
 * Supports fluid ↔ fluid-cell equivalence when analyzing DETECT/SUBMIT steps.
 */
public final class QuestRequirementAnalyzer {

    private QuestRequirementAnalyzer() {}

    public static QuestAnalysisDto analyze(String ownerUuid, int networkId, QuestDetailDto detail) {
        return analyze(ownerUuid, networkId, detail, false);
    }

    public static QuestAnalysisDto analyze(String ownerUuid, int networkId, QuestDetailDto detail,
        boolean includeAllFluidContainers) {
        QuestAnalysisDto out = new QuestAnalysisDto();
        if (detail == null) {
            return out;
        }
        out.questId = detail.questId;
        out.networkId = networkId;
        out.state = detail.state;
        out.canSubmit = detail.canSubmit;
        if (detail.tasks == null) {
            return out;
        }
        boolean includeAll = QuestFluidEquivalence.resolveIncludeAll(includeAllFluidContainers);
        StorageDto storage = ownerUuid != null && !ownerUuid.isEmpty() ? SnapshotCache.instance()
            .getStale(ownerUuid, networkId, SnapshotScheduler.TYPE_STORAGE) : null;
        for (QuestTaskDto task : detail.tasks) {
            out.steps.add(analyzeStep(ownerUuid, networkId, task, storage, includeAll));
        }
        return out;
    }

    private static QuestAnalysisStepDto analyzeStep(String ownerUuid, int networkId, QuestTaskDto task,
        StorageDto storage, boolean includeAll) {
        QuestAnalysisStepDto step = new QuestAnalysisStepDto();
        step.index = task.index;
        step.webAction = task.webAction;
        step.reasonKey = task.reasonKey;
        step.complete = task.complete;
        step.webCapable = QuestTaskDeserializer.WEB_SUBMIT.equals(task.webAction)
            || QuestTaskDeserializer.WEB_DETECT.equals(task.webAction);
        step.itemId = task.itemId;
        step.registryName = task.registryName;
        step.meta = task.meta;
        step.displayName = task.displayName;
        step.iconItemId = task.iconItemId;
        step.required = task.required;
        step.fluidName = task.fluidName;
        step.fluidRequired = task.fluidRequired;

        if (task.complete) {
            step.available = task.required;
            step.remaining = 0;
            step.missing = 0;
            return step;
        }

        if (QuestTaskDeserializer.WEB_IN_GAME.equals(task.webAction)) {
            return step;
        }

        if (task.fluidName != null && !task.fluidName.isEmpty()) {
            StockBreakdown stock = QuestFluidEquivalence.analyzeTrueFluid(storage, task.fluidName, includeAll);
            step.fluidAvailable = stock.totalFluidMb;
            step.fluidFromFreeMb = stock.freeMb;
            step.fluidFromCellsMb = stock.fromCellsMb;
            step.fluidRemaining = Math.max(0L, task.fluidRequired - task.fluidProgress);
            step.fluidMissing = Math.max(0L, step.fluidRemaining - step.fluidAvailable);
            return step;
        }

        if (task.registryName != null && !task.registryName.isEmpty()) {
            boolean cellTask = QuestFluidEquivalence.isFluidCellTask(task.registryName, task.meta);
            step.fluidCellTask = cellTask;
            if (cellTask) {
                StockBreakdown stock = QuestFluidEquivalence
                    .analyzeCellItem(storage, task.registryName, task.meta, includeAll);
                step.fluidCellCapacityMb = stock.capacityMb;
                step.emptyCellAvailable = stock.emptyCellCount;
                step.fluidFromFreeMb = stock.freeMb;
                step.fluidFromCellsMb = stock.fromCellsMb;
                boolean detect = QuestTaskDeserializer.WEB_DETECT.equals(task.webAction);
                step.available = detect ? stock.detectAvailable : stock.submitAvailable;
            } else {
                step.available = findItemAvailable(storage, task.registryName, task.meta, task.acceptAnyMeta);
            }
            step.remaining = Math.max(0L, task.required - task.progress);
            step.missing = Math.max(0L, step.remaining - step.available);
            if (step.missing > 0 && ownerUuid != null && !ownerUuid.isEmpty()) {
                CraftTreeNodeDto tree = CraftTreeCalculator
                    .build(ownerUuid, networkId, task.registryName, step.missing, 6);
                if (tree != null) {
                    step.craftable = CraftTreeCalculator.computeCraftableAmount(tree);
                    if (step.craftable > step.missing) {
                        step.craftable = step.missing;
                    }
                }
            }
        }
        return step;
    }

    private static long findItemAvailable(StorageDto storage, String registryName, int meta, boolean acceptAnyMeta) {
        if (storage == null || storage.items == null || registryName == null) {
            return 0L;
        }
        long total = 0L;
        for (ItemEntry item : storage.items) {
            if (item == null) {
                continue;
            }
            if (registryName.equals(item.registryName) || registryName.equals(item.itemId)) {
                if (acceptAnyMeta) {
                    total += item.amount;
                } else {
                    if (meta <= 0 || item.meta == meta) {
                        total += item.amount;
                    }
                }
            }
        }
        return total;
    }
}
