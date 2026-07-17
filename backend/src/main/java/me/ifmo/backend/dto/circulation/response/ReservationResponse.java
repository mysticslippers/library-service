package me.ifmo.backend.dto.circulation.response;

import me.ifmo.backend.catalog.web.response.MaterialCopyResponse;
import me.ifmo.backend.catalog.web.response.MaterialShortResponse;
import me.ifmo.backend.library.web.response.BranchShortResponse;
import me.ifmo.backend.user.web.response.UserShortResponse;
import me.ifmo.backend.entities.enums.ReservationStatus;

import java.time.LocalDateTime;

public record ReservationResponse(
        Long id,
        UserShortResponse user,
        MaterialShortResponse material,
        MaterialCopyResponse copy,
        BranchShortResponse branch,
        ReservationStatus status,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        LocalDateTime readyAt,
        LocalDateTime cancelledAt,
        String cancellationReason
) {
}
