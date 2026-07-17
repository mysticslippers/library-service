package me.ifmo.backend.circulation.web.response;

import me.ifmo.backend.catalog.web.response.MaterialCopyResponse;
import me.ifmo.backend.library.web.response.BranchShortResponse;
import me.ifmo.backend.user.web.response.UserShortResponse;
import me.ifmo.backend.circulation.domain.enums.LoanStatus;

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
