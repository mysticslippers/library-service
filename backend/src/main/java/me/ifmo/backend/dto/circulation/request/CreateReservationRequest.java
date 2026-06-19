package me.ifmo.backend.dto.circulation.request;

import jakarta.validation.constraints.NotNull;

public record CreateReservationRequest(
        @NotNull
        Long userId,

        @NotNull
        Long materialId,

        Long copyId,

        @NotNull
        Long branchId
) {
}
