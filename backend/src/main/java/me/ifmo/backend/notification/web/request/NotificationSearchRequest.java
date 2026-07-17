package me.ifmo.backend.notification.web.request;

import jakarta.validation.constraints.Size;
import me.ifmo.backend.notification.domain.enums.NotificationChannel;
import me.ifmo.backend.notification.domain.enums.NotificationStatus;
import me.ifmo.backend.notification.domain.enums.NotificationType;

import java.time.LocalDateTime;

public record NotificationSearchRequest(
        Long userId,
        Long reservationId,
        Long loanId,
        Long fineId,
        NotificationType type,
        NotificationChannel channel,
        NotificationStatus status,

        LocalDateTime createdFrom,
        LocalDateTime createdTo,
        LocalDateTime sentFrom,
        LocalDateTime sentTo,

        @Size(max = 255)
        String query
) {
}
