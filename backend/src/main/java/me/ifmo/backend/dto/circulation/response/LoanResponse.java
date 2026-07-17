package me.ifmo.backend.dto.circulation.response;

import me.ifmo.backend.dto.catalog.response.MaterialCopyResponse;
import me.ifmo.backend.library.internal.web.response.BranchShortResponse;
import me.ifmo.backend.dto.user.response.UserShortResponse;
import me.ifmo.backend.entities.enums.LoanStatus;

import java.time.LocalDateTime;

public record LoanResponse(
        Long id,
        UserShortResponse user,
        MaterialCopyResponse copy,
        Long reservationId,
        BranchShortResponse branch,
        UserShortResponse issuedByUser,
        LocalDateTime loanedAt,
        LocalDateTime dueAt,
        LocalDateTime returnedAt,
        Integer renewalCount,
        LoanStatus status
) {
}
