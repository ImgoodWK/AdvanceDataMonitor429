package com.imgood.advancedatamonitor.assistant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AssistantOrderLine {

    public int lineIndex;
    public String target = "";
    public long amount = 1L;
    public final List<CraftingCandidate> candidates = new ArrayList<CraftingCandidate>();
    public CraftingCandidate selectedCandidate;

    public AssistantOrderLine() {}

    public AssistantOrderLine(int lineIndex, String target, long amount) {
        this.lineIndex = lineIndex;
        this.target = target == null ? "" : target.trim();
        this.amount = Math.max(1L, amount);
    }

    public AssistantOrderLine copyWithoutCandidates() {
        return new AssistantOrderLine(this.lineIndex, this.target, this.amount);
    }

    public CraftingCandidate selectedOrFirstCandidate() {
        if (this.selectedCandidate != null) {
            return this.selectedCandidate;
        }
        return this.candidates.isEmpty() ? null : this.candidates.get(0);
    }

    public void setCandidates(List<CraftingCandidate> values) {
        this.candidates.clear();
        if (values != null) {
            this.candidates.addAll(values);
        }
        this.selectedCandidate = this.candidates.isEmpty() ? null : this.candidates.get(0);
    }

    public List<CraftingCandidate> getCandidates() {
        return Collections.unmodifiableList(this.candidates);
    }

    public boolean hasUsableCandidate() {
        return selectedOrFirstCandidate() != null;
    }
}
