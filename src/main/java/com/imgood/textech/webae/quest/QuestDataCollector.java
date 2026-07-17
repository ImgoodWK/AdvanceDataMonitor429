package com.imgood.textech.webae.quest;

import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import com.imgood.textech.compat.bq.BqApiFacade;
import com.imgood.textech.compat.bq.BqCompat;
import com.imgood.textech.webae.dto.QuestDetailDto;
import com.imgood.textech.webae.dto.QuestLineGraphDto;
import com.imgood.textech.webae.dto.QuestLineSummaryDto;
import com.imgood.textech.webae.dto.QuestMetaDto;
import com.imgood.textech.webae.dto.QuestProgressDto;
import com.imgood.textech.webae.dto.QuestSearchHitDto;

/**
 * Read-only quest definition collector (server main thread).
 */
public final class QuestDataCollector {

    private QuestDataCollector() {}

    public static QuestMetaDto collectMeta() {
        QuestMetaDto meta = new QuestMetaDto();
        meta.questEnabled = com.imgood.textech.Config.webQuestEnabled;
        meta.questSubmitEnabled = com.imgood.textech.Config.webQuestSubmitEnabled;
        meta.questChainSubmitEnabled = com.imgood.textech.Config.webQuestChainSubmitEnabled;
        meta.questFluidAllContainersOption = com.imgood.textech.Config.webQuestFluidAllContainersOption;
        meta.questsAvailable = BqCompat.isFeatureEnabled();
        meta.standardExpansionLoaded = BqCompat.isStandardExpansionLoaded();
        meta.modVersion = BqCompat.readModVersion();
        meta.lineCount = BqCompat.isModLoaded() ? BqApiFacade.countLines() : 0;
        return meta;
    }

    public static List<QuestLineSummaryDto> collectLines() {
        if (!BqCompat.isModLoaded()) {
            return new java.util.ArrayList<QuestLineSummaryDto>();
        }
        return QuestCacheStore.instance()
            .getLines(BqApiFacade.collectLines());
    }

    public static QuestLineGraphDto collectLineGraph(String lineId, EntityPlayerMP player) {
        if (!BqCompat.isModLoaded() || lineId == null) {
            return new QuestLineGraphDto();
        }
        UUID uuid = parseUuid(lineId);
        if (uuid == null) {
            return new QuestLineGraphDto();
        }
        return BqApiFacade.collectLineGraph(uuid, player);
    }

    public static QuestDetailDto collectQuestDetail(String questId, EntityPlayerMP player) {
        if (!BqCompat.isModLoaded() || questId == null) {
            return new QuestDetailDto();
        }
        UUID uuid = parseUuid(questId);
        if (uuid == null) {
            return new QuestDetailDto();
        }
        return BqApiFacade.collectQuestDetail(uuid, player);
    }

    public static QuestProgressDto collectProgress(EntityPlayerMP player) {
        if (!BqCompat.isModLoaded() || player == null) {
            return new QuestProgressDto();
        }
        final EntityPlayerMP p = player;
        return QuestCacheStore.instance()
            .getProgress(
                com.imgood.textech.compat.bq.BqQuestingIdentity.resolveQuestingUuid(p) != null
                    ? com.imgood.textech.compat.bq.BqQuestingIdentity.resolveQuestingUuid(p)
                        .toString()
                    : "",
                new QuestCacheStore.ProgressLoader<QuestProgressDto>() {
                    @Override
                    public QuestProgressDto load() {
                        return BqApiFacade.collectProgress(p);
                    }
                });
    }

    public static List<QuestSearchHitDto> search(String query, EntityPlayerMP player) {
        if (!BqCompat.isModLoaded()) {
            return new java.util.ArrayList<QuestSearchHitDto>();
        }
        return BqApiFacade.search(query, player);
    }

    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
