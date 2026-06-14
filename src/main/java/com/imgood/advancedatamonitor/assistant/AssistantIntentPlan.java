package com.imgood.advancedatamonitor.assistant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AssistantIntentPlan {

    private static final AssistantIntentPlan EMPTY = new AssistantIntentPlan(
        Collections.<AssistantIntentTask>emptyList());

    private final List<AssistantIntentTask> tasks;

    public AssistantIntentPlan(List<AssistantIntentTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            this.tasks = Collections.emptyList();
        } else {
            this.tasks = Collections.unmodifiableList(new ArrayList<AssistantIntentTask>(tasks));
        }
    }

    public static AssistantIntentPlan empty() {
        return EMPTY;
    }

    public List<AssistantIntentTask> getTasks() {
        return this.tasks;
    }

    public boolean isEmpty() {
        return this.tasks.isEmpty();
    }

    public int size() {
        return this.tasks.size();
    }

    public boolean isChatOnly() {
        if (this.tasks.isEmpty()) {
            return false;
        }
        for (AssistantIntentTask task : this.tasks) {
            if (task == null || task.type != AssistantIntentType.CHAT) {
                return false;
            }
        }
        return true;
    }
}
