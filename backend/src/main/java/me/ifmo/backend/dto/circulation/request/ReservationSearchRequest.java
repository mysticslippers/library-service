package me.ifmo.backend.dto.circulation.request;

import jakarta.validation.constraints.Size;
import me.ifmo.backend.entities.enums.ReservationStatus;

import java.time.LocalDateTime;

public record ReservationSearchRequest(
        Long userId,
        Long materialId,
        Long copyId,
        Long branchId,
        ReservationStatus status,
        LocalDateTime createdFrom,
        LocalDateTime createdTo,
        LocalDateTime expiresBefore,

        @Size(max = 255)
        String query
) {
}
