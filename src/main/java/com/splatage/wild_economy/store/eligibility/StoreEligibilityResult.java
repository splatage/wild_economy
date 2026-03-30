package com.splatage.wild_economy.store.eligibility;

import java.util.List;
import java.util.Objects;

public record StoreEligibilityResult(
    boolean visible,
    boolean acquirable,
    String blockedMessage,
    List<String> progressLines,
    String inspirationalMessage
) {
    public StoreEligibilityResult {
        progressLines = List.copyOf(Objects.requireNonNull(progressLines, "progressLines"));
    }

    public static StoreEligibilityResult allowed() {
        return new StoreEligibilityResult(true, true, null, List.of(), null);
    }

    public static StoreEligibilityResult hidden() {
        return new StoreEligibilityResult(false, false, null, List.of(), null);
    }

    public static StoreEligibilityResult locked(
        final String blockedMessage,
        final List<String> progressLines,
        final String inspirationalMessage
    ) {
        return new StoreEligibilityResult(true, false, blockedMessage, progressLines, inspirationalMessage);
    }
}
