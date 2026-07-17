package me.ifmo.backend.fine.web.response;

import me.ifmo.backend.fine.domain.enums.FineTariffStatus;
import me.ifmo.backend.fine.domain.enums.ViolationType;

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
