package me.ifmo.backend.dto.notification.response;

import me.ifmo.backend.entities.enums.NotificationChannel;
import me.ifmo.backend.entities.enums.NotificationTemplateStatus;
import me.ifmo.backend.entities.enums.NotificationType;

import java.time.LocalDateTime;
import java.util.List;

public record NotificationTemplateResponse(
        Long id,
        NotificationType type,
        NotificationChannel channel,
        String subjectTemplate,
        String bodyTemplate,
        List<String> requiredParameters,
        NotificationTemplateStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
