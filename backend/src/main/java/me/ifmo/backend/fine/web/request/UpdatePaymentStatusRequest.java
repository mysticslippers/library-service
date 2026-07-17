package me.ifmo.backend.fine.web.request;

import jakarta.validation.constraints.NotNull;
import me.ifmo.backend.fine.domain.enums.PaymentStatus;

public record UpdatePaymentStatusRequest(
        @NotNull
        PaymentStatus status,

        String externalPayment
) {
}
