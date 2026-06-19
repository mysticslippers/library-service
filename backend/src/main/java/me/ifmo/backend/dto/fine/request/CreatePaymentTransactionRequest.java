package me.ifmo.backend.dto.fine.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreatePaymentTransactionRequest(
        @NotNull
        Long fineId,

        String externalPayment,

        @NotNull
        @DecimalMin(value = "0.00", inclusive = false)
        BigDecimal amount
) {
}
