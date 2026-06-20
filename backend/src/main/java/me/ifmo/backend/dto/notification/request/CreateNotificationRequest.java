package me.ifmo.backend.dto.notification.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import me.ifmo.backend.entities.enums.NotificationChannel;
import me.ifmo.backend.entities.enums.NotificationType;

public record CreateNotificationRequest(
        @NotNull
        Long userId,

        Long reservationId,
        Long loanId,
        Long fineId,

        @NotNull
        NotificationType type,

        @NotNull
        NotificationChannel channel,

        @Size(max = 255)
        String subject,

        String body
) {
}
