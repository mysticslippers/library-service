package me.ifmo.backend.dto.circulation.request;

import me.ifmo.backend.entities.enums.LoanStatus;

import java.time.LocalDateTime;

public record LoanSearchRequest(
        Long userId,
        Long copyId,
        Long branchId,
        Long issuedByUserId,
        LoanStatus status,
        LocalDateTime loanedFrom,
        LocalDateTime loanedTo,
        LocalDateTime dueBefore,
        LocalDateTime returnedFrom,
        LocalDateTime returnedTo
) {
}
