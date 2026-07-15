package com.imgood.textech.webae.api.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import net.minecraft.entity.player.EntityPlayerMP;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.imgood.textech.compat.bq.BqCompat;
import com.imgood.textech.compat.bq.BqQuestingIdentity;
import com.imgood.textech.handler.HandlerTick;
import com.imgood.textech.webae.dto.QuestAnalysisDto;
import com.imgood.textech.webae.dto.QuestChainPlanDto;
import com.imgood.textech.webae.dto.QuestChainSubmitResultDto;
import com.imgood.textech.webae.dto.QuestCraftJobDto;
import com.imgood.textech.webae.dto.QuestDetailDto;
import com.imgood.textech.webae.dto.QuestLineGraphDto;
import com.imgood.textech.webae.dto.QuestMetaDto;
import com.imgood.textech.webae.dto.QuestProgressDto;
import com.imgood.textech.webae.dto.QuestSubmitResultDto;
import com.imgood.textech.webae.quest.QuestChainOrchestrator;
import com.imgood.textech.webae.quest.QuestChainService;
import com.imgood.textech.webae.quest.QuestCraftOrchestrator;
import com.imgood.textech.webae.quest.QuestDataCollector;
import com.imgood.textech.webae.quest.QuestRequirementAnalyzer;
import com.imgood.textech.webae.quest.QuestSubmitService;
import com.imgood.textech.webae.recipe.RecipeCacheStore;

import fi.iki.elonen.NanoHTTPD;

/**
 * BetterQuesting REST API for WebAE quest book.
 */
public final class QuestHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();
    private static final long MAIN_THREAD_TIMEOUT_MS = 15_000L;

    private QuestHandler() {}

    public static NanoHTTPD.Response handle(String uri, NanoHTTPD.Method method, Map<String, String> params, String body,
        String ownerUuid, boolean guest) {
        if (!BqCompat.isFeatureEnabled() && !"/api/quests/meta".equals(uri)) {
            return json(
                NanoHTTPD.Response.Status.OK,
                "{\"success\":true,\"questsAvailable\":false,\"message\":\"BetterQuesting not available\"}");
        }

        if ("/api/quests/meta".equals(uri)) {
            if (method != NanoHTTPD.Method.GET) {
                return methodNotAllowed("GET");
            }
            QuestMetaDto meta = QuestDataCollector.collectMeta();
            return json(NanoHTTPD.Response.Status.OK, "{\"success\":true,\"meta\":" + GSON.toJson(meta) + "}");
        }

        if ("/api/quests/lines".equals(uri)) {
            if (method != NanoHTTPD.Method.GET) {
                return methodNotAllowed("GET");
            }
            return onMainThread(ownerUuid, new MainThreadTask<Object>() {
                @Override
                public Object run(EntityPlayerMP player) {
                    return QuestDataCollector.collectLines();
                }
            }, "lines");
        }

        if (uri.startsWith("/api/quests/lines/") && uri.length() > "/api/quests/lines/".length()) {
            if (method != NanoHTTPD.Method.GET) {
                return methodNotAllowed("GET");
            }
            String lineId = uri.substring("/api/quests/lines/".length());
            return onMainThread(ownerUuid, new MainThreadTask<QuestLineGraphDto>() {
                @Override
                public QuestLineGraphDto run(EntityPlayerMP player) {
                    return QuestDataCollector.collectLineGraph(lineId, player);
                }
            }, "line");
        }

        if ("/api/quests/progress".equals(uri)) {
            if (method != NanoHTTPD.Method.GET) {
                return methodNotAllowed("GET");
            }
            return onMainThread(ownerUuid, new MainThreadTask<QuestProgressDto>() {
                @Override
                public QuestProgressDto run(EntityPlayerMP player) {
                    return QuestDataCollector.collectProgress(player);
                }
            }, "progress");
        }

        if ("/api/quests/search".equals(uri)) {
            if (method != NanoHTTPD.Method.GET) {
                return methodNotAllowed("GET");
            }
            final String q = params.get("q");
            return onMainThread(ownerUuid, new MainThreadTask<Object>() {
                @Override
                public Object run(EntityPlayerMP player) {
                    return QuestDataCollector.search(q, player);
                }
            }, "search");
        }

        if (uri.startsWith("/api/quests/submit-jobs/")) {
            if (method != NanoHTTPD.Method.GET) {
                return methodNotAllowed("GET");
            }
            String jobId = uri.substring("/api/quests/submit-jobs/".length());
            if (jobId.startsWith("chain-")) {
                QuestChainSubmitResultDto chainJob = QuestChainOrchestrator.poll(jobId.substring("chain-".length()));
                return json(NanoHTTPD.Response.Status.OK, "{\"success\":true,\"chain\":" + GSON.toJson(chainJob) + "}");
            }
            QuestCraftJobDto job = QuestCraftOrchestrator.resolveSubmitJob(jobId);
            return json(NanoHTTPD.Response.Status.OK, "{\"success\":true,\"job\":" + GSON.toJson(job) + "}");
        }

        if (uri.startsWith("/api/quests/chain-jobs/")) {
            if (method != NanoHTTPD.Method.GET) {
                return methodNotAllowed("GET");
            }
            String jobId = uri.substring("/api/quests/chain-jobs/".length());
            QuestChainSubmitResultDto chainJob = QuestChainOrchestrator.poll(jobId);
            return json(NanoHTTPD.Response.Status.OK, "{\"success\":true,\"chain\":" + GSON.toJson(chainJob) + "}");
        }

        String questId = parseQuestId(uri);
        if (questId == null) {
            return json(NanoHTTPD.Response.Status.NOT_FOUND, "{\"success\":false,\"message\":\"Unknown quest route\"}");
        }

        if (uri.endsWith("/chain-plan")) {
            if (method != NanoHTTPD.Method.GET) {
                return methodNotAllowed("GET");
            }
            int networkId = parseInt(params.get("network"), 0);
            prefetchRecipesForCraftTree();
            return onMainThread(ownerUuid, new MainThreadTask<QuestChainPlanDto>() {
                @Override
                public QuestChainPlanDto run(EntityPlayerMP player) {
                    return QuestChainService.buildPlan(ownerUuid, networkId, questId);
                }
            }, "plan");
        }

        if (uri.endsWith("/submit-chain")) {
            if (method != NanoHTTPD.Method.POST) {
                return methodNotAllowed("POST");
            }
            if (guest) {
                return guestDenied();
            }
            if (!com.imgood.textech.Config.webQuestChainSubmitEnabled) {
                return json(
                    NanoHTTPD.Response.Status.FORBIDDEN,
                    "{\"success\":false,\"code\":\"chain_disabled\",\"message\":\"Chain submit disabled\"}");
            }
            ChainSubmitBody req = parseChainSubmitBody(body);
            final int networkId = req.networkId;
            final boolean dryRun = req.dryRun;
            final boolean skipMissing = req.skipMissing;
            final boolean craftMissing = req.craftMissing;
            final String cpuName = req.cpuName;
            final long timeout = req.waitTimeoutMs;
            prefetchRecipesForCraftTree();
            if (craftMissing && !dryRun) {
                QuestChainSubmitResultDto chainJob = QuestChainOrchestrator.start(
                    ownerUuid,
                    networkId,
                    questId,
                    skipMissing,
                    cpuName,
                    timeout);
                return json(NanoHTTPD.Response.Status.OK, "{\"success\":true,\"chain\":" + GSON.toJson(chainJob) + "}");
            }
            return onMainThread(ownerUuid, new MainThreadTask<QuestChainSubmitResultDto>() {
                @Override
                public QuestChainSubmitResultDto run(EntityPlayerMP player) {
                    return QuestChainService.submitSync(ownerUuid, networkId, questId, dryRun, skipMissing);
                }
            }, "chain");
        }

        if (uri.endsWith("/analysis")) {
            if (method != NanoHTTPD.Method.GET) {
                return methodNotAllowed("GET");
            }
            int networkId = parseInt(params.get("network"), 0);
            prefetchRecipesForCraftTree();
            return onMainThread(ownerUuid, new MainThreadTask<QuestAnalysisDto>() {
                @Override
                public QuestAnalysisDto run(EntityPlayerMP player) {
                    QuestDetailDto detail = QuestDataCollector.collectQuestDetail(questId, player);
                    return QuestRequirementAnalyzer.analyze(ownerUuid, networkId, detail);
                }
            }, "analysis");
        }

        if (uri.endsWith("/detect")) {
            if (method != NanoHTTPD.Method.POST) {
                return methodNotAllowed("POST");
            }
            if (guest) {
                return guestDenied();
            }
            DetectBody detectReq = parseDetectBody(body);
            final int detectNetworkId = detectReq.networkId;
            prefetchRecipesForCraftTree();
            return onMainThread(ownerUuid, new MainThreadTask<QuestSubmitResultDto>() {
                @Override
                public QuestSubmitResultDto run(EntityPlayerMP player) {
                    return QuestSubmitService.detectOnly(ownerUuid, questId, detectNetworkId);
                }
            }, "detect");
        }

        if (uri.endsWith("/submit")) {
            if (method != NanoHTTPD.Method.POST) {
                return methodNotAllowed("POST");
            }
            if (guest) {
                return guestDenied();
            }
            SubmitBody req = parseSubmitBody(body);
            int networkId = req != null ? req.networkId : 0;
            boolean dryRun = req == null || req.dryRun;
            List<Integer> steps = req != null ? req.steps : null;
            prefetchRecipesForCraftTree();
            return onMainThread(ownerUuid, new MainThreadTask<QuestSubmitResultDto>() {
                @Override
                public QuestSubmitResultDto run(EntityPlayerMP player) {
                    return QuestSubmitService.submit(ownerUuid, networkId, questId, dryRun, steps);
                }
            }, "submit");
        }

        if (uri.endsWith("/submit-craft")) {
            if (method != NanoHTTPD.Method.POST) {
                return methodNotAllowed("POST");
            }
            if (guest) {
                return guestDenied();
            }
            SubmitCraftBody req = parseSubmitCraftBody(body);
            int networkId = req != null ? req.networkId : 0;
            String cpuName = req != null ? req.cpuName : null;
            long timeout = req != null ? req.waitTimeoutMs : 0L;
            prefetchRecipesForCraftTree();
            QuestCraftJobDto job = QuestCraftOrchestrator.start(ownerUuid, networkId, questId, cpuName, timeout);
            return json(NanoHTTPD.Response.Status.OK, "{\"success\":true,\"job\":" + GSON.toJson(job) + "}");
        }

        if (method != NanoHTTPD.Method.GET) {
            return methodNotAllowed("GET");
        }
        return onMainThread(ownerUuid, new MainThreadTask<QuestDetailDto>() {
            @Override
            public QuestDetailDto run(EntityPlayerMP player) {
                return QuestDataCollector.collectQuestDetail(questId, player);
            }
        }, "quest");
    }

    private static String parseQuestId(String uri) {
        if (uri == null || !uri.startsWith("/api/quests/")) {
            return null;
        }
        String tail = uri.substring("/api/quests/".length());
        if (tail.isEmpty() || tail.startsWith("lines") || tail.startsWith("progress") || tail.startsWith("search")
            || tail.startsWith("meta") || tail.startsWith("submit-jobs") || tail.startsWith("chain-jobs")) {
            return null;
        }
        int slash = tail.indexOf('/');
        if (slash >= 0) {
            return tail.substring(0, slash);
        }
        return tail;
    }

    private interface MainThreadTask<T> {
        T run(EntityPlayerMP player);
    }

    /** Block on HTTP thread so craft-tree analyze never sync-parses recipes on the server tick. */
    private static void prefetchRecipesForCraftTree() {
        RecipeCacheStore.instance()
            .ensureLoaded();
    }

    private static <T> NanoHTTPD.Response onMainThread(String ownerUuid, MainThreadTask<T> task, String envelopeKey) {
        final Object[] holder = new Object[1];
        final CountDownLatch latch = new CountDownLatch(1);
        HandlerTick.enqueueServerTask(new Runnable() {
            @Override
            public void run() {
                try {
                    EntityPlayerMP player = BqQuestingIdentity.resolvePlayer(ownerUuid);
                    holder[0] = task.run(player);
                } catch (Throwable t) {
                    holder[0] = t;
                } finally {
                    latch.countDown();
                }
            }
        });
        try {
            if (!latch.await(MAIN_THREAD_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                return json(
                    NanoHTTPD.Response.Status.INTERNAL_ERROR,
                    "{\"success\":false,\"message\":\"Quest operation timed out\"}");
            }
        } catch (InterruptedException e) {
            Thread.currentThread()
                .interrupt();
            return json(NanoHTTPD.Response.Status.INTERNAL_ERROR, "{\"success\":false,\"message\":\"Interrupted\"}");
        }
        if (holder[0] instanceof Throwable) {
            return json(
                NanoHTTPD.Response.Status.INTERNAL_ERROR,
                "{\"success\":false,\"message\":\""
                    + escape(((Throwable) holder[0]).toString())
                    + "\"}");
        }
        return json(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"" + envelopeKey + "\":" + GSON.toJson(holder[0]) + "}");
    }

    private static DetectBody parseDetectBody(String body) {
        DetectBody req = new DetectBody();
        if (body == null || body.trim()
            .isEmpty()) {
            return req;
        }
        try {
            JsonObject obj = new JsonParser().parse(body)
                .getAsJsonObject();
            req.networkId = obj.has("networkId") ? obj.get("networkId")
                .getAsInt() : 0;
        } catch (Exception ignored) {}
        return req;
    }

    private static SubmitBody parseSubmitBody(String body) {
        if (body == null || body.trim()
            .isEmpty()) {
            SubmitBody defaults = new SubmitBody();
            defaults.dryRun = true;
            return defaults;
        }
        try {
            JsonObject obj = new JsonParser().parse(body)
                .getAsJsonObject();
            SubmitBody req = new SubmitBody();
            req.networkId = obj.has("networkId") ? obj.get("networkId")
                .getAsInt() : 0;
            req.dryRun = !obj.has("dryRun") || obj.get("dryRun")
                .getAsBoolean();
            if (obj.has("steps") && obj.get("steps")
                .isJsonArray()) {
                req.steps = new ArrayList<Integer>();
                JsonArray arr = obj.getAsJsonArray("steps");
                for (JsonElement el : arr) {
                    req.steps.add(Integer.valueOf(el.getAsInt()));
                }
            }
            return req;
        } catch (Exception e) {
            SubmitBody fallback = new SubmitBody();
            fallback.dryRun = true;
            return fallback;
        }
    }

    private static SubmitCraftBody parseSubmitCraftBody(String body) {
        SubmitCraftBody req = new SubmitCraftBody();
        if (body == null || body.trim()
            .isEmpty()) {
            return req;
        }
        try {
            JsonObject obj = new JsonParser().parse(body)
                .getAsJsonObject();
            req.networkId = obj.has("networkId") ? obj.get("networkId")
                .getAsInt() : 0;
            if (obj.has("cpuName") && !obj.get("cpuName")
                .isJsonNull()) {
                req.cpuName = obj.get("cpuName")
                    .getAsString();
            }
            if (obj.has("waitTimeoutMs")) {
                req.waitTimeoutMs = obj.get("waitTimeoutMs")
                    .getAsLong();
            }
        } catch (Exception ignored) {}
        return req;
    }

    private static ChainSubmitBody parseChainSubmitBody(String body) {
        ChainSubmitBody req = new ChainSubmitBody();
        if (body == null || body.trim()
            .isEmpty()) {
            return req;
        }
        try {
            JsonObject obj = new JsonParser().parse(body)
                .getAsJsonObject();
            req.networkId = obj.has("networkId") ? obj.get("networkId")
                .getAsInt() : 0;
            req.dryRun = obj.has("dryRun") && obj.get("dryRun")
                .getAsBoolean();
            req.skipMissing = !obj.has("skipMissing") || obj.get("skipMissing")
                .getAsBoolean();
            req.craftMissing = obj.has("craftMissing") && obj.get("craftMissing")
                .getAsBoolean();
            if (obj.has("cpuName") && !obj.get("cpuName")
                .isJsonNull()) {
                req.cpuName = obj.get("cpuName")
                    .getAsString();
            }
            if (obj.has("waitTimeoutMs")) {
                req.waitTimeoutMs = obj.get("waitTimeoutMs")
                    .getAsLong();
            }
        } catch (Exception ignored) {}
        return req;
    }

    private static int parseInt(String raw, int def) {
        if (raw == null || raw.isEmpty()) {
            return def;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static NanoHTTPD.Response guestDenied() {
        return json(
            NanoHTTPD.Response.Status.FORBIDDEN,
            "{\"success\":false,\"code\":\"guest_readonly\",\"message\":\"Guest token cannot submit quests\"}");
    }

    private static NanoHTTPD.Response methodNotAllowed(String allowed) {
        return json(
            NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,
            "{\"success\":false,\"message\":\"Use " + allowed + "\"}");
    }

    private static NanoHTTPD.Response json(NanoHTTPD.Response.Status status, String body) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body);
    }

    private static String escape(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\\", "\\\\")
            .replace("\"", "\\\"");
    }

    private static final class DetectBody {
        int networkId;
    }

    private static final class SubmitBody {
        int networkId;
        boolean dryRun = true;
        List<Integer> steps;
    }

    private static final class SubmitCraftBody {
        int networkId;
        String cpuName;
        long waitTimeoutMs;
    }

    private static final class ChainSubmitBody {
        int networkId;
        boolean dryRun;
        boolean skipMissing = true;
        boolean craftMissing;
        String cpuName;
        long waitTimeoutMs;
    }
}
