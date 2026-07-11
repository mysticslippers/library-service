package me.ifmo.backend.dto.fine.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import me.ifmo.backend.entities.enums.ViolationType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateFineTariffRequest(
        @NotNull
        ViolationType violationType,

        @DecimalMin("0.01")
        BigDecimal amountPerDay,

        @DecimalMin("0.01")
        BigDecimal fixedAmount,

        @DecimalMin("0.01")
        BigDecimal maxAmount,

        LocalDateTime validTo
) {
}
