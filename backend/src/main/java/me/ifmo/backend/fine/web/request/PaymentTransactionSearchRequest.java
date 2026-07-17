package me.ifmo.backend.fine.web.request;

import me.ifmo.backend.fine.domain.enums.PaymentStatus;

import java.time.LocalDateTime;

public record PaymentTransactionSearchRequest(
        Long fineId,
        PaymentStatus status,
        LocalDateTime createdFrom,
        LocalDateTime createdTo
) {
}
