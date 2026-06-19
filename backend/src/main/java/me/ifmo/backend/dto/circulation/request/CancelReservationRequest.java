package me.ifmo.backend.dto.circulation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelReservationRequest(
        @NotBlank
        @Size(max = 1000)
        String reason
) {
}
