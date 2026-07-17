package me.ifmo.backend.notification.web.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import me.ifmo.backend.notification.domain.enums.NotificationChannel;
import me.ifmo.backend.notification.domain.enums.NotificationType;

import java.util.Map;

public record CreateNotificationRequest(
        @NotNull
        Long userId,

        Long reservationId,
        Long loanId,
        Long fineId,

        @NotNull
        NotificationType type,

        NotificationChannel channel,

        @Size(max = 255)
        String subject,

        String body,

        Map<String, Object> parameters
) {
        public CreateNotificationRequest(Long userId, Long reservationId, Long loanId, Long fineId,
                                         NotificationType type, NotificationChannel channel,
                                         String subject, String body) {
                this(userId, reservationId, loanId, fineId, type, channel, subject, body, Map.of());
        }
}
