package me.ifmo.backend.dto.fine.request;

import me.ifmo.backend.entities.enums.PaymentStatus;

import java.time.LocalDateTime;

public record PaymentTransactionSearchRequest(
        Long fineId,
        PaymentStatus status,
        LocalDateTime createdFrom,
        LocalDateTime createdTo
) {
}
