package com.imgood.textech.webae.alerts;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cooldown and hourly rate limits for automation rules (Phase 3.3).
 */
public final class AutomationCooldownTracker {

    private static final AutomationCooldownTracker INSTANCE = new AutomationCooldownTracker();
    private static final int DEFAULT_MAX_PER_HOUR = 12;

    private final ConcurrentHashMap<String, Long> lastTriggerMs = new ConcurrentHashMap<String, Long>();
    private final ConcurrentHashMap<String, List<Long>> hourlyTriggers = new ConcurrentHashMap<String, List<Long>>();

    private AutomationCooldownTracker() {}

    public static AutomationCooldownTracker instance() {
        return INSTANCE;
    }

    public boolean canTrigger(String ownerUuid, WebAlertsConfig.AutomationRule rule, long now) {
        if (ownerUuid == null || rule == null || rule.id == null || rule.id.isEmpty()) {
            return false;
        }
        String key = ownerUuid + ":" + rule.id;
        Long last = lastTriggerMs.get(key);
        long cooldownMs = Math.max(1, rule.cooldownSeconds) * 1000L;
        if (last != null && now - last < cooldownMs) {
            return false;
        }
        int maxPerHour = rule.maxTriggersPerHour > 0 ? rule.maxTriggersPerHour : DEFAULT_MAX_PER_HOUR;
        List<Long> triggers = hourlyTriggers.get(key);
        if (triggers == null) {
            triggers = new ArrayList<Long>();
            hourlyTriggers.put(key, triggers);
        }
        pruneOlderThanHour(triggers, now);
        return triggers.size() < maxPerHour;
    }

    public void recordTrigger(String ownerUuid, WebAlertsConfig.AutomationRule rule, long now) {
        if (ownerUuid == null || rule == null || rule.id == null || rule.id.isEmpty()) {
            return;
        }
        String key = ownerUuid + ":" + rule.id;
        lastTriggerMs.put(key, now);
        List<Long> triggers = hourlyTriggers.get(key);
        if (triggers == null) {
            triggers = new ArrayList<Long>();
            hourlyTriggers.put(key, triggers);
        }
        triggers.add(now);
        pruneOlderThanHour(triggers, now);
    }

    private static void pruneOlderThanHour(List<Long> triggers, long now) {
        long cutoff = now - 3_600_000L;
        Iterator<Long> it = triggers.iterator();
        while (it.hasNext()) {
            Long ts = it.next();
            if (ts == null || ts < cutoff) {
                it.remove();
            }
        }
    }
}
