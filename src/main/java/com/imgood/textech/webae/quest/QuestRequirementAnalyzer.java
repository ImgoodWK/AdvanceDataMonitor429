package com.imgood.textech.webae.quest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.imgood.textech.compat.bq.BqApiFacade;
import com.imgood.textech.compat.bq.BqQuestingIdentity;
import com.imgood.textech.webae.cache.SnapshotCache;
import com.imgood.textech.webae.cache.SnapshotScheduler;
import com.imgood.textech.webae.craft.CraftTreeCalculator;
import com.imgood.textech.webae.craft.CraftTreeNodeDto;
import com.imgood.textech.webae.dto.QuestAnalysisDto;
import com.imgood.textech.webae.dto.QuestAnalysisStepDto;
import com.imgood.textech.webae.dto.QuestDetailDto;
import com.imgood.textech.webae.dto.QuestTaskDto;
import com.imgood.textech.webae.dto.StorageDto;
import com.imgood.textech.webae.dto.StorageDto.FluidEntry;
import com.imgood.textech.webae.dto.StorageDto.ItemEntry;

/**
 * Compares quest task requirements against AE snapshot + craft tree.
 */
public final class QuestRequirementAnalyzer {

    private QuestRequirementAnalyzer() {}

    public static QuestAnalysisDto analyze(String ownerUuid, int networkId, QuestDetailDto detail) {
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
        StorageDto storage = ownerUuid != null && !ownerUuid.isEmpty()
            ? SnapshotCache.instance()
                .getStale(ownerUuid, networkId, SnapshotScheduler.TYPE_STORAGE)
            : null;
        for (QuestTaskDto task : detail.tasks) {
            out.steps.add(analyzeStep(ownerUuid, networkId, task, storage));
        }
        return out;
    }

    private static QuestAnalysisStepDto analyzeStep(String ownerUuid, int networkId, QuestTaskDto task,
        StorageDto storage) {
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
            step.fluidAvailable = findFluidAvailable(storage, task.fluidName);
            step.fluidRemaining = Math.max(0L, task.fluidRequired - task.fluidProgress);
            step.fluidMissing = Math.max(0L, step.fluidRemaining - step.fluidAvailable);
            return step;
        }

        if (task.registryName != null && !task.registryName.isEmpty()) {
            step.available = findItemAvailable(storage, task.registryName, task.meta, task.acceptAnyMeta);
            step.remaining = Math.max(0L, task.required - task.progress);
            step.missing = Math.max(0L, step.remaining - step.available);
            if (step.missing > 0 && ownerUuid != null && !ownerUuid.isEmpty()) {
                CraftTreeNodeDto tree = CraftTreeCalculator.build(
                    ownerUuid,
                    networkId,
                    task.registryName,
                    step.missing,
                    6);
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

    private static long findFluidAvailable(StorageDto storage, String fluidName) {
        if (storage == null || storage.fluids == null || fluidName == null) {
            return 0L;
        }
        long total = 0L;
        for (FluidEntry fluid : storage.fluids) {
            if (fluid != null && fluidName.equalsIgnoreCase(fluid.fluidName)) {
                total += fluid.amount;
            }
        }
        return total;
    }
}
