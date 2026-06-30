package com.imgood.textech.assistant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CandidateQueryResult {

    public final List<CraftingCandidate> candidates;
    public final boolean truncated;

    public CandidateQueryResult(List<CraftingCandidate> candidates, boolean truncated) {
        this.candidates = candidates == null ? Collections.<CraftingCandidate>emptyList()
            : Collections.unmodifiableList(new ArrayList<CraftingCandidate>(candidates));
        this.truncated = truncated;
    }

    public static CandidateQueryResult empty() {
        return new CandidateQueryResult(new ArrayList<CraftingCandidate>(), false);
    }
}
