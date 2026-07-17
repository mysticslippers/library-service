package me.ifmo.backend.notification.web.response;

import me.ifmo.backend.notification.domain.enums.NotificationChannel;
import me.ifmo.backend.notification.domain.enums.NotificationType;

import java.time.LocalDateTime;

public record NotificationPreferenceResponse(
        Long id,
        Long userId,
        NotificationType type,
        NotificationChannel channel,
        Boolean enabled,
        Boolean preferred,
        Boolean mandatory,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
