package me.ifmo.backend.dto.circulation.request;

import jakarta.validation.constraints.Positive;

public record RenewLoanRequest(
        @Positive
        Integer renewalDays
) {
}
