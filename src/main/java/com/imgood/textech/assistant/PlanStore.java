package com.imgood.textech.assistant;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.imgood.textech.AdvanceDataMonitor;

public class PlanStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .create();
    private static final PlanStore INSTANCE = new PlanStore();
    private final List<PlanEntry> plans = new ArrayList<PlanEntry>();
    private boolean loaded;

    public static PlanStore instance() {
        return INSTANCE;
    }

    public synchronized String add(EntityPlayerMP player, String rawText, String title) {
        return add(player, rawText, title, "zh_CN");
    }

    public synchronized String add(EntityPlayerMP player, String rawText, String title, String locale) {
        load();
        AssistantTimeParser.Result parsed = AssistantTimeParser.parse(rawText, title);
        PlanEntry entry = new PlanEntry();
        entry.id = nextId(owner(player));
        entry.owner = owner(player);
        entry.rawText = rawText == null ? "" : rawText;
        entry.title = parsed.cleanedTitle == null || parsed.cleanedTitle.trim()
            .isEmpty() ? entry.rawText : parsed.cleanedTitle.trim();
        entry.status = "open";
        entry.createdAt = System.currentTimeMillis();
        entry.dueAt = parsed.dueAt;
        entry.completed = false;
        entry.reminded = false;
        plans.add(entry);
        save();
        AdvanceDataMonitor.LOG.info(
            "[ADM Assistant] Plan created: owner={}, id={}, title='{}', dueAt={}, raw='{}'",
            entry.owner,
            entry.id,
            safe(entry.title),
            entry.dueAt,
            safe(entry.rawText));
        return zh(locale)
            ? "已记录计�?#" + entry.id + "�? + entry.title + (entry.dueAt > 0 ? "（提醒时�?" + format(entry.dueAt) + "�? : "")
            : "Plan recorded #" + entry.id
                + ": "
                + entry.title
                + (entry.dueAt > 0 ? " (remind at " + format(entry.dueAt) + ")" : "");
    }

    public synchronized String add(String rawText, String title) {
        return add(null, rawText, title);
    }

    public synchronized String listOpen(EntityPlayerMP player) {
        return listOpen(player, "zh_CN");
    }

    public synchronized String listOpen(EntityPlayerMP player, String locale) {
        load();
        String owner = owner(player);
        StringBuilder builder = new StringBuilder();
        for (PlanEntry plan : plans) {
            normalizeLoaded(plan);
            if (!owner.equals(plan.owner) || plan.completed) {
                continue;
            }
            if (builder.length() == 0) {
                builder.append(zh(locale) ? "待办计划�? : "Open plans:");
            }
            builder.append("\n")
                .append(plan.id)
                .append(". ")
                .append(plan.title)
                .append(" (")
                .append(format(plan.createdAt))
                .append(")");
            if (plan.dueAt > 0) {
                builder.append(zh(locale) ? " 截止 " : " due ")
                    .append(format(plan.dueAt));
            }
        }
        return builder.length() == 0 ? (zh(locale) ? "没有待办计划�? : "No open plans.") : builder.toString();
    }

    public synchronized String listOpen() {
        return listOpen(null);
    }

    public synchronized String complete(EntityPlayerMP player, int id, String text) {
        return complete(player, id, text, "zh_CN");
    }

    public synchronized String complete(EntityPlayerMP player, int id, String text, String locale) {
        load();
        String owner = owner(player);
        if (id <= 0) {
            id = inferId(owner, text);
        }
        for (PlanEntry plan : plans) {
            normalizeLoaded(plan);
            if (owner.equals(plan.owner) && plan.id == id && !plan.completed) {
                plan.status = "done";
                plan.completed = true;
                plan.completedAt = System.currentTimeMillis();
                save();
                AdvanceDataMonitor.LOG.info(
                    "[ADM Assistant] Plan completed: owner={}, id={}, title='{}'",
                    plan.owner,
                    plan.id,
                    safe(plan.title));
                return zh(locale) ? "已完成计�?#" + plan.id + "�? + plan.title
                    : "Plan completed #" + plan.id + ": " + plan.title;
            }
        }
        return zh(locale) ? "没有找到匹配的待办计划�? : "No matching open plan was found.";
    }

    public synchronized String complete(int id, String text) {
        return complete(null, id, text);
    }

    public synchronized List<PlanEntry> dueReminders(EntityPlayerMP player) {
        load();
        long now = System.currentTimeMillis();
        String owner = owner(player);
        List<PlanEntry> due = new ArrayList<PlanEntry>();
        for (PlanEntry plan : plans) {
            normalizeLoaded(plan);
            if (owner.equals(plan.owner) && !plan.completed && !plan.reminded && plan.dueAt > 0 && plan.dueAt <= now) {
                plan.reminded = true;
                due.add(plan.copy());
            }
        }
        if (!due.isEmpty()) {
            save();
        }
        return due;
    }

    private int nextId(String owner) {
        int max = 0;
        for (PlanEntry plan : plans) {
            normalizeLoaded(plan);
            if (owner.equals(plan.owner)) {
                max = Math.max(max, plan.id);
            }
        }
        return max + 1;
    }

    private int inferId(String owner, String text) {
        if (text != null) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)")
                .matcher(text);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        }
        for (PlanEntry plan : plans) {
            normalizeLoaded(plan);
            if (owner.equals(plan.owner) && !plan.completed) {
                return plan.id;
            }
        }
        return -1;
    }

    private String owner(EntityPlayerMP player) {
        UUID uuid = player == null ? new UUID(0L, 0L) : player.getUniqueID();
        return uuid.toString();
    }

    private String format(long time) {
        return new SimpleDateFormat("MM-dd HH:mm").format(new Date(time));
    }

    private void normalizeLoaded(PlanEntry plan) {
        if (plan.owner == null || plan.owner.trim()
            .isEmpty()) {
            plan.owner = new UUID(0L, 0L).toString();
        }
        plan.completed = "done".equals(plan.status) || plan.completed;
        if (plan.status == null || plan.status.trim()
            .isEmpty()) {
            plan.status = plan.completed ? "done" : "open";
        }
    }

    private void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        File file = AssistantDataFiles.dataFile("plans.json");
        if (!file.exists()) {
            return;
        }
        try (java.io.InputStreamReader reader = new java.io.InputStreamReader(new FileInputStream(file), "UTF-8")) {
            List<PlanEntry> loadedPlans = GSON.fromJson(reader, new TypeToken<List<PlanEntry>>() {}.getType());
            if (loadedPlans != null) {
                plans.clear();
                plans.addAll(loadedPlans);
                for (PlanEntry plan : plans) {
                    normalizeLoaded(plan);
                }
            }
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("Failed to load assistant plans", e);
        }
    }

    private void save() {
        File file = AssistantDataFiles.dataFile("plans.json");
        try (java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(new FileOutputStream(file), "UTF-8")) {
            GSON.toJson(plans, writer);
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("Failed to save assistant plans", e);
        }
    }

    private boolean zh(String locale) {
        return locale == null || locale.trim()
            .isEmpty()
            || locale.toLowerCase()
                .startsWith("zh");
    }

    private String safe(String text) {
        if (text == null) {
            return "";
        }
        return text.replace((char) 10, ' ')
            .replace((char) 13, ' ');
    }

    public static class PlanEntry {

        public int id;
        public String owner = "";
        public String rawText = "";
        public String title = "";
        public String status = "open";
        public long createdAt;
        public long dueAt = -1L;
        public long completedAt;
        public boolean completed;
        public boolean reminded;

        private PlanEntry copy() {
            PlanEntry copy = new PlanEntry();
            copy.id = this.id;
            copy.owner = this.owner;
            copy.rawText = this.rawText;
            copy.title = this.title;
            copy.status = this.status;
            copy.createdAt = this.createdAt;
            copy.dueAt = this.dueAt;
            copy.completedAt = this.completedAt;
            copy.completed = this.completed;
            copy.reminded = this.reminded;
            return copy;
        }
    }
}
