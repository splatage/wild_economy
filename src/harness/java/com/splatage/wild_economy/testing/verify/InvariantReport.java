package com.splatage.wild_economy.testing.verify;

import java.util.List;

public record InvariantReport(boolean success, List<InvariantViolation> violations) {
    public String describe() {
        if (this.success) {
            return "Invariant report: success";
        }
        final StringBuilder builder = new StringBuilder("Invariant report: FAILED");
        for (final InvariantViolation violation : this.violations) {
            builder.append(System.lineSeparator())
                    .append(" - ")
                    .append(violation.code())
                    .append(": ")
                    .append(violation.message());
        }
        return builder.toString();
    }
}
