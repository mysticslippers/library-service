package me.ifmo.backend.dto.circulation.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record CreateLoanRequest(
        @NotNull
        Long userId,

        @NotNull
        Long copyId,

        Long reservationId,

        @NotNull
        Long branchId,

        @NotNull
        Long issuedByUserId,

        LocalDateTime dueAt
) {
}
