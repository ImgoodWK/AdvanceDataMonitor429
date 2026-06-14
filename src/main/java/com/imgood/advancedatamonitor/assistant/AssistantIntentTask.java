package com.imgood.advancedatamonitor.assistant;

public class AssistantIntentTask {

    public final AssistantIntentType type;
    public final String target;
    public final long amount;
    public final int optionNumber;
    public final int storageScope;
    public final double confidence;

    public AssistantIntentTask(AssistantIntentType type, String target, long amount, int optionNumber, int storageScope,
        double confidence) {
        this.type = type == null ? AssistantIntentType.CHAT : type;
        this.target = target == null ? "" : target.trim();
        this.amount = amount;
        this.optionNumber = optionNumber;
        this.storageScope = normalizeStorageScope(storageScope);
        this.confidence = confidence;
    }

    public boolean isChat() {
        return this.type == AssistantIntentType.CHAT;
    }

    public boolean isOrderItem() {
        return this.type == AssistantIntentType.ORDER_ITEM;
    }

    public boolean isWithdrawItem() {
        return this.type == AssistantIntentType.WITHDRAW_ITEM;
    }

    public AssistantIntent toIntent(String rawText) {
        return new AssistantIntent(this.type, rawText, this.target, this.amount, this.optionNumber, this.storageScope);
    }

    public AssistantOrderLine toOrderLine(int lineIndex) {
        return new AssistantOrderLine(lineIndex, this.target, this.amount);
    }

    public static int storageScopeFromString(String value) {
        String normalized = value == null ? ""
            : value.trim()
                .toLowerCase();
        if ("items".equals(normalized)) {
            return AssistantIntent.STORAGE_SCOPE_ITEMS;
        }
        if ("fluids".equals(normalized)) {
            return AssistantIntent.STORAGE_SCOPE_FLUIDS;
        }
        return AssistantIntent.STORAGE_SCOPE_ALL;
    }

    public static boolean isValidStorageScope(String value) {
        if (value == null || value.trim()
            .isEmpty()) {
            return true;
        }
        String normalized = value.trim()
            .toLowerCase();
        return "all".equals(normalized) || "items".equals(normalized) || "fluids".equals(normalized);
    }

    public static int normalizeStorageScope(int value) {
        if (value == AssistantIntent.STORAGE_SCOPE_ITEMS || value == AssistantIntent.STORAGE_SCOPE_FLUIDS) {
            return value;
        }
        return AssistantIntent.STORAGE_SCOPE_ALL;
    }
}
