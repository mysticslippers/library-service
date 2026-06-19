package me.ifmo.backend.dto.fine.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import me.ifmo.backend.entities.enums.ViolationType;

import java.math.BigDecimal;

public record CreateFineRequest(
        @NotNull
        Long userId,

        Long loanId,
        Long copyId,
        Long tariffId,

        @NotNull
        ViolationType reason,

        @NotNull
        @DecimalMin("0.00")
        BigDecimal amount
) {
}
