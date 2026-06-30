package com.imgood.textech.assistant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AssistantIntent {

    public static final int STORAGE_SCOPE_ALL = 0;
    public static final int STORAGE_SCOPE_ITEMS = 1;
    public static final int STORAGE_SCOPE_FLUIDS = 2;

    public final AssistantIntentType type;
    public final String rawText;
    public final String target;
    public final long amount;
    public final int optionNumber;
    public final int storageScope;
    public final List<AssistantOrderLine> orderLines;

    public AssistantIntent(AssistantIntentType type, String rawText, String target, long amount, int optionNumber) {
        this(type, rawText, target, amount, optionNumber, STORAGE_SCOPE_ALL, null);
    }

    public AssistantIntent(AssistantIntentType type, String rawText, String target, long amount, int optionNumber,
        int storageScope) {
        this(type, rawText, target, amount, optionNumber, storageScope, null);
    }

    public AssistantIntent(AssistantIntentType type, String rawText, String target, long amount, int optionNumber,
        int storageScope, List<AssistantOrderLine> orderLines) {
        this.type = type == null ? AssistantIntentType.CHAT : type;
        this.rawText = rawText == null ? "" : rawText;
        this.target = target == null ? "" : target;
        this.amount = amount;
        this.optionNumber = optionNumber;
        this.storageScope = storageScope;
        if (orderLines == null) {
            this.orderLines = Collections.emptyList();
        } else {
            this.orderLines = Collections.unmodifiableList(new ArrayList<AssistantOrderLine>(orderLines));
        }
    }

    public static AssistantIntent chat(String rawText) {
        return new AssistantIntent(AssistantIntentType.CHAT, rawText, rawText, 0, -1);
    }

    public static AssistantIntent orderBatch(String rawText, List<AssistantOrderLine> orderLines) {
        return new AssistantIntent(AssistantIntentType.ORDER_BATCH, rawText, "", 0, -1, STORAGE_SCOPE_ALL, orderLines);
    }

    public static AssistantIntent withdrawBatch(String rawText, List<AssistantOrderLine> withdrawLines) {
        return new AssistantIntent(
            AssistantIntentType.WITHDRAW_BATCH,
            rawText,
            "",
            0,
            -1,
            STORAGE_SCOPE_ALL,
            withdrawLines);
    }
}
