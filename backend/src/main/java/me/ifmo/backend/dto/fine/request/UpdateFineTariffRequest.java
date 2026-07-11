package me.ifmo.backend.dto.fine.request;

import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record UpdateFineTariffRequest(
        @DecimalMin("0.01")
        BigDecimal amountPerDay,

        @DecimalMin("0.01")
        BigDecimal fixedAmount,

        @DecimalMin("0.01")
        BigDecimal maxAmount,

        LocalDateTime validTo
) {
}
