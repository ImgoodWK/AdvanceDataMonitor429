package com.imgood.textech.assistant;

public class WithdrawSubmitOutcome {

    public enum Kind {
        SUCCESS,
        FAILURE,
        PARTIAL_CONFIRM
    }

    public final Kind kind;
    public final String message;
    public final CraftingCandidate candidate;
    public final long requestedAmount;
    public final long fitAmount;
    public final long storageAmount;

    public WithdrawSubmitOutcome(Kind kind, String message) {
        this(kind, message, null, 0L, 0L, 0L);
    }

    public WithdrawSubmitOutcome(Kind kind, String message, CraftingCandidate candidate, long requestedAmount,
        long fitAmount, long storageAmount) {
        this.kind = kind == null ? Kind.FAILURE : kind;
        this.message = message == null ? "" : message;
        this.candidate = candidate;
        this.requestedAmount = requestedAmount;
        this.fitAmount = fitAmount;
        this.storageAmount = storageAmount;
    }
}
