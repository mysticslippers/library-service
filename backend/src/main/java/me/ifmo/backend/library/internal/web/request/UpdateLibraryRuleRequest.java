package me.ifmo.backend.library.internal.web.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

public record UpdateLibraryRuleRequest(
        @Min(0)
        Integer maxActiveReservations,

        @Min(0)
        Integer maxActiveLoans,

        @Positive
        Integer reservationTtlDays,

        @Positive
        Integer defaultLoanDays,

        Boolean renewalAllowed,

        @Min(0)
        Integer maxRenewalCount,

        @Positive
        Integer renewalPeriodDays,

        Boolean reservationAllowed,
        LocalDateTime validTo
) {
}
