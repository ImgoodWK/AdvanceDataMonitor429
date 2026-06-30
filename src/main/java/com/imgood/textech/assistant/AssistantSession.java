package com.imgood.textech.assistant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AssistantSession {

    private static final AssistantSession CLIENT = new AssistantSession();
    private final List<CraftingCandidate> pendingCandidates = new ArrayList<>();
    private final List<AssistantOrderLine> pendingOrderLines = new ArrayList<AssistantOrderLine>();
    private final List<AssistantIntentTask> pendingTaskQueue = new ArrayList<AssistantIntentTask>();
    private final List<TeleportDestination> pendingTeleportDestinations = new ArrayList<TeleportDestination>();
    private String lastUserText = "";
    private AssistantSessionKind kind = AssistantSessionKind.NONE;
    private long pendingPartialAmount;
    private long pendingRequestedAmount;

    public static AssistantSession client() {
        return CLIENT;
    }

    public synchronized void setPendingCandidates(String userText, List<CraftingCandidate> candidates) {
        setPendingCandidates(userText, candidates, AssistantSessionKind.ORDER_CANDIDATES);
    }

    public synchronized void setPendingCandidates(String userText, List<CraftingCandidate> candidates,
        AssistantSessionKind kind) {
        this.lastUserText = userText == null ? "" : userText;
        this.kind = kind == null ? AssistantSessionKind.NONE : kind;
        this.pendingCandidates.clear();
        this.pendingOrderLines.clear();
        this.pendingPartialAmount = 0L;
        this.pendingRequestedAmount = 0L;
        if (candidates != null) {
            this.pendingCandidates.addAll(candidates);
        }
        if (this.pendingCandidates.isEmpty()) {
            this.kind = AssistantSessionKind.NONE;
        }
    }

    public synchronized void appendPendingCandidates(List<CraftingCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        this.pendingCandidates.addAll(candidates);
    }

    public synchronized void setPendingOrderLines(String userText, List<AssistantOrderLine> lines) {
        setPendingOrderLines(userText, lines, AssistantSessionKind.ORDER_BATCH_CANDIDATES);
    }

    public synchronized void setPendingOrderLines(String userText, List<AssistantOrderLine> lines,
        AssistantSessionKind kind) {
        this.lastUserText = userText == null ? "" : userText;
        this.kind = kind == null ? AssistantSessionKind.ORDER_BATCH_CANDIDATES : kind;
        this.pendingCandidates.clear();
        this.pendingOrderLines.clear();
        this.pendingPartialAmount = 0L;
        this.pendingRequestedAmount = 0L;
        if (lines != null) {
            this.pendingOrderLines.addAll(lines);
        }
        if (this.pendingOrderLines.isEmpty()) {
            this.kind = AssistantSessionKind.NONE;
        }
    }

    public synchronized void setPendingPartialWithdraw(String userText, List<CraftingCandidate> candidates,
        long requestedAmount, long fitAmount) {
        this.lastUserText = userText == null ? "" : userText;
        this.kind = AssistantSessionKind.WITHDRAW_PARTIAL_CONFIRM;
        this.pendingCandidates.clear();
        this.pendingOrderLines.clear();
        this.pendingRequestedAmount = Math.max(0L, requestedAmount);
        this.pendingPartialAmount = Math.max(0L, fitAmount);
        if (candidates != null) {
            this.pendingCandidates.addAll(candidates);
        }
        if (this.pendingCandidates.isEmpty() || this.pendingPartialAmount <= 0L) {
            clear();
        }
    }

    public synchronized List<CraftingCandidate> getPendingCandidates() {
        return Collections.unmodifiableList(new ArrayList<CraftingCandidate>(this.pendingCandidates));
    }

    public synchronized List<AssistantOrderLine> getPendingOrderLines() {
        return Collections.unmodifiableList(new ArrayList<AssistantOrderLine>(this.pendingOrderLines));
    }

    public synchronized AssistantSessionKind getKind() {
        return this.kind;
    }

    public synchronized CraftingCandidate select(int oneBasedIndex) {
        int index = oneBasedIndex - 1;
        if (index < 0 || index >= this.pendingCandidates.size()) {
            return null;
        }
        return this.pendingCandidates.get(index);
    }

    public synchronized CraftingCandidate findByName(String target) {
        if (target == null || target.trim()
            .isEmpty()) {
            return null;
        }
        for (CraftingCandidate candidate : this.pendingCandidates) {
            if (candidate != null && ItemStackUtils.fuzzyNameMatches(candidate.toItemStack(), target)) {
                return candidate;
            }
        }
        return null;
    }

    public synchronized String getLastUserText() {
        return this.lastUserText;
    }

    public synchronized long getPendingPartialAmount() {
        return this.pendingPartialAmount;
    }

    public synchronized long getPendingRequestedAmount() {
        return this.pendingRequestedAmount;
    }

    public synchronized void enqueueRemainingTasks(List<AssistantIntentTask> tasks) {
        if (tasks != null && !tasks.isEmpty()) {
            this.pendingTaskQueue.addAll(tasks);
        }
    }

    public synchronized AssistantIntentTask dequeueNextTask() {
        if (this.pendingTaskQueue.isEmpty()) {
            return null;
        }
        return this.pendingTaskQueue.remove(0);
    }

    public synchronized boolean hasPendingTasks() {
        return !this.pendingTaskQueue.isEmpty();
    }

    public synchronized void setPendingTeleportDestinations(String userText, List<TeleportDestination> destinations) {
        this.lastUserText = userText == null ? "" : userText;
        this.kind = AssistantSessionKind.TELEPORT_CANDIDATES;
        this.pendingCandidates.clear();
        this.pendingOrderLines.clear();
        this.pendingTeleportDestinations.clear();
        this.pendingPartialAmount = 0L;
        this.pendingRequestedAmount = 0L;
        if (destinations != null) {
            this.pendingTeleportDestinations.addAll(destinations);
        }
        if (this.pendingTeleportDestinations.isEmpty()) {
            this.kind = AssistantSessionKind.NONE;
        }
    }

    public synchronized List<TeleportDestination> getPendingTeleportDestinations() {
        return Collections.unmodifiableList(new ArrayList<TeleportDestination>(this.pendingTeleportDestinations));
    }

    public synchronized TeleportDestination selectTeleportDestination(int oneBasedIndex) {
        int index = oneBasedIndex - 1;
        if (index < 0 || index >= this.pendingTeleportDestinations.size()) {
            return null;
        }
        return this.pendingTeleportDestinations.get(index);
    }

    public synchronized TeleportDestination findTeleportDestinationByName(String target) {
        if (target == null || target.trim()
            .isEmpty()) {
            return null;
        }
        String normalized = target.trim()
            .toLowerCase();
        for (TeleportDestination dest : this.pendingTeleportDestinations) {
            if (dest.name.toLowerCase()
                .contains(normalized) || normalized.contains(dest.name.toLowerCase())) {
                return dest;
            }
        }
        return null;
    }

    public synchronized void clear() {
        this.pendingCandidates.clear();
        this.pendingOrderLines.clear();
        this.pendingTaskQueue.clear();
        this.pendingTeleportDestinations.clear();
        this.lastUserText = "";
        this.kind = AssistantSessionKind.NONE;
        this.pendingPartialAmount = 0L;
        this.pendingRequestedAmount = 0L;
    }
}
