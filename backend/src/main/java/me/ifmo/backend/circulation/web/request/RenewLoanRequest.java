package me.ifmo.backend.circulation.web.request;

import jakarta.validation.constraints.Positive;

public record RenewLoanRequest(
        @Positive
        Integer renewalDays
) {
}
