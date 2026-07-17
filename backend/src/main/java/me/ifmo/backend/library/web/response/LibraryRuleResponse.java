package me.ifmo.backend.library.web.response;

import me.ifmo.backend.library.domain.enums.LibraryRuleStatus;

import java.time.LocalDateTime;

public record LibraryRuleResponse(
        Long id,
        BranchShortResponse branch,
        Integer maxActiveReservations,
        Integer maxActiveLoans,
        Integer reservationTtlDays,
        Integer defaultLoanDays,
        Boolean renewalAllowed,
        Integer maxRenewalCount,
        Integer renewalPeriodDays,
        Boolean reservationAllowed,
        LibraryRuleStatus status,
        LocalDateTime validFrom,
        LocalDateTime validTo
) {
}
