package me.ifmo.backend.fine.web.response;

import me.ifmo.backend.fine.domain.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentTransactionResponse(
        Long id,
        Long fineId,
        String externalPayment,
        BigDecimal amount,
        PaymentStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
