package me.ifmo.backend.circulation.web.request;

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
