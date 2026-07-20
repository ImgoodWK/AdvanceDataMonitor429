package com.imgood.textech.webae.assistant;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.imgood.textech.assistant.CraftingCandidate;
import com.imgood.textech.assistant.TeleportDestination;

/** Short-lived server-side confirmation state; item NBT never crosses the Web API. */
final class WebAssistantPendingStore {

    private static final long TTL_MS = 5L * 60L * 1000L;
    private static final ConcurrentHashMap<String, PendingAction> ACTIONS = new ConcurrentHashMap<String, PendingAction>();
    private static final ConcurrentHashMap<String, String> LATEST_BY_ACTOR = new ConcurrentHashMap<String, String>();

    private WebAssistantPendingStore() {}

    static PendingAction createCandidates(String actorUuid, String ownerUuid, String kind, String rawText,
        String locale, long amount, List<CraftingCandidate> candidates) {
        PendingAction action = base(actorUuid, ownerUuid, kind, rawText, locale, amount);
        action.candidates = candidates;
        put(action);
        return action;
    }

    static PendingAction createTeleport(String actorUuid, String ownerUuid, String rawText, String locale,
        List<TeleportDestination> destinations) {
        PendingAction action = base(actorUuid, ownerUuid, "teleport", rawText, locale, 1L);
        action.destinations = destinations;
        put(action);
        return action;
    }

    static PendingAction createPartial(String actorUuid, String ownerUuid, String rawText, String locale,
        CraftingCandidate candidate, long amount) {
        PendingAction action = base(actorUuid, ownerUuid, "withdraw-partial", rawText, locale, amount);
        action.candidates = java.util.Collections.singletonList(candidate);
        action.confirmPartial = true;
        put(action);
        return action;
    }

    static PendingAction take(String token, String actorUuid, String ownerUuid) {
        prune();
        PendingAction action = ACTIONS.get(token);
        if (!matches(action, actorUuid, ownerUuid)) return null;
        if (!ACTIONS.remove(token, action)) return null;
        LATEST_BY_ACTOR.remove(actorUuid, token);
        return action;
    }

    static PendingAction takeLatest(String actorUuid, String ownerUuid) {
        String token = LATEST_BY_ACTOR.get(actorUuid);
        return token == null ? null : take(token, actorUuid, ownerUuid);
    }

    static void clearActor(String actorUuid) {
        LATEST_BY_ACTOR.remove(actorUuid);
        for (PendingAction action : ACTIONS.values()) {
            if (action != null && safe(actorUuid).equals(action.actorUuid)) {
                ACTIONS.remove(action.token, action);
            }
        }
    }

    private static PendingAction base(String actorUuid, String ownerUuid, String kind, String rawText, String locale,
        long amount) {
        PendingAction action = new PendingAction();
        action.token = UUID.randomUUID()
            .toString()
            .replace("-", "");
        action.actorUuid = safe(actorUuid);
        action.ownerUuid = safe(ownerUuid);
        action.kind = kind;
        action.rawText = safe(rawText);
        action.locale = safe(locale);
        action.amount = Math.max(1L, amount);
        action.createdAt = System.currentTimeMillis();
        return action;
    }

    private static void put(PendingAction action) {
        prune();
        LATEST_BY_ACTOR.put(action.actorUuid, action.token);
        ACTIONS.put(action.token, action);
    }

    private static boolean matches(PendingAction action, String actorUuid, String ownerUuid) {
        return action != null && safe(actorUuid).equals(action.actorUuid)
            && safe(ownerUuid).equals(action.ownerUuid)
            && System.currentTimeMillis() - action.createdAt <= TTL_MS;
    }

    private static void prune() {
        long now = System.currentTimeMillis();
        for (PendingAction action : ACTIONS.values()) {
            if (action == null || now - action.createdAt > TTL_MS) {
                if (action != null) {
                    ACTIONS.remove(action.token, action);
                    LATEST_BY_ACTOR.remove(action.actorUuid, action.token);
                }
            }
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    static final class PendingAction {

        String token;
        String actorUuid;
        String ownerUuid;
        String kind;
        String rawText;
        String locale;
        long amount;
        long createdAt;
        boolean confirmPartial;
        List<CraftingCandidate> candidates;
        List<TeleportDestination> destinations;
    }
}
