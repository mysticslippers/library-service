package me.ifmo.backend.dto.fine.response;

import me.ifmo.backend.entities.enums.FineTariffStatus;
import me.ifmo.backend.entities.enums.ViolationType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FineTariffResponse(
        Long id,
        ViolationType violationType,
        BigDecimal amountPerDay,
        BigDecimal fixedAmount,
        BigDecimal maxAmount,
        FineTariffStatus status,
        LocalDateTime validFrom,
        LocalDateTime validTo
) {
}
