package me.ifmo.backend.dto.notification.response;

import me.ifmo.backend.dto.user.response.UserShortResponse;
import me.ifmo.backend.entities.enums.NotificationChannel;
import me.ifmo.backend.entities.enums.NotificationStatus;
import me.ifmo.backend.entities.enums.NotificationType;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        UserShortResponse user,
        Long reservationId,
        Long loanId,
        Long fineId,
        NotificationType type,
        NotificationChannel channel,
        String subject,
        String body,
        NotificationStatus status,
        LocalDateTime createdAt,
        LocalDateTime sentAt,
        LocalDateTime readAt,
        String externalMessageId,
        String errorMessage,
        Integer attemptCount
) {
}
