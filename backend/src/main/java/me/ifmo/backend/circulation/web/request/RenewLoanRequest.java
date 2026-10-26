package me.ifmo.backend.circulation.web.request;

import jakarta.validation.constraints.Positive;
import io.swagger.v3.oas.annotations.media.Schema;

public record RenewLoanRequest(
        @Positive
        @Schema(description = "Days to add, from 1 to the branch's current renewalPeriodDays. Omit to use the full period.")
        Integer renewalDays
) {
}
