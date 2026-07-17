package me.ifmo.backend.user.web.response;

import me.ifmo.backend.user.domain.enums.UserWarningStatus;

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
