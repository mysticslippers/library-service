package me.ifmo.backend.dto.fine.request;

import jakarta.validation.constraints.NotNull;
import me.ifmo.backend.entities.enums.PaymentStatus;

public record UpdatePaymentStatusRequest(
        @NotNull
        PaymentStatus status,

        String externalPayment
) {
}
