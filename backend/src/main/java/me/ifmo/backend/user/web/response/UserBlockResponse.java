package me.ifmo.backend.user.web.response;

import me.ifmo.backend.user.domain.enums.UserBlockStatus;

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
