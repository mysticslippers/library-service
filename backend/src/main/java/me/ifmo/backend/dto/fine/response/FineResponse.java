package me.ifmo.backend.dto.fine.response;

import me.ifmo.backend.dto.catalog.response.MaterialCopyResponse;
import me.ifmo.backend.user.web.response.UserShortResponse;
import me.ifmo.backend.entities.enums.FineStatus;
import me.ifmo.backend.entities.enums.ViolationType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FineResponse(
        Long id,
        UserShortResponse user,
        Long loanId,
        MaterialCopyResponse copy,
        Long tariffId,
        ViolationType reason,
        BigDecimal amount,
        FineStatus status,
        LocalDateTime createdAt,
        LocalDateTime paidAt,
        LocalDateTime cancelledAt,
        String cancellationReason
) {
}
