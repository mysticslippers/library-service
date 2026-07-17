package me.ifmo.backend.notification.web.response;

import me.ifmo.backend.notification.domain.enums.NotificationChannel;
import me.ifmo.backend.notification.domain.enums.NotificationTemplateStatus;
import me.ifmo.backend.notification.domain.enums.NotificationType;

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
