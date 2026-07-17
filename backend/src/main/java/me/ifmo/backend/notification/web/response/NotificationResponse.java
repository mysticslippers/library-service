package me.ifmo.backend.notification.web.response;

import me.ifmo.backend.user.web.response.UserShortResponse;
import me.ifmo.backend.notification.domain.enums.NotificationChannel;
import me.ifmo.backend.notification.domain.enums.NotificationStatus;
import me.ifmo.backend.notification.domain.enums.NotificationType;

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
