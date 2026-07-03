package me.ifmo.backend.dto.user.response;

import me.ifmo.backend.entities.enums.UserBlockStatus;

import java.time.LocalDateTime;

public record UserBlockResponse(
        Long id,
        UserShortResponse user,
        UserShortResponse createdByUser,
        String reason,
        LocalDateTime blockedAt,
        LocalDateTime expiresAt,
        UserShortResponse unblockedByUser,
        String unblockReason,
        LocalDateTime unblockedAt,
        UserBlockStatus status
) {
}
