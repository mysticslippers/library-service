package me.ifmo.backend.DTO.user.response;

import me.ifmo.backend.entities.enums.UserWarningStatus;

import java.time.LocalDateTime;

public record UserWarningResponse(
        Long id,
        UserShortResponse user,
        UserShortResponse createdByUser,
        String reason,
        String comment,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        UserWarningStatus status
) {
}
