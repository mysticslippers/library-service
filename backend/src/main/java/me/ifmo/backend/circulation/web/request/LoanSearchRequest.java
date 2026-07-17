package me.ifmo.backend.circulation.web.request;

import me.ifmo.backend.circulation.domain.enums.LoanStatus;

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
