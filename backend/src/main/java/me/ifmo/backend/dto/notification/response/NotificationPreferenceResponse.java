package me.ifmo.backend.dto.notification.response;

import me.ifmo.backend.entities.enums.NotificationChannel;
import me.ifmo.backend.entities.enums.NotificationType;

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
