package me.ifmo.backend.dto.notification.request;

import jakarta.validation.constraints.NotNull;
import me.ifmo.backend.entities.enums.NotificationChannel;
import me.ifmo.backend.entities.enums.NotificationType;

public record UpdateNotificationPreferenceRequest(
        @NotNull
        NotificationType type,

        @NotNull
        NotificationChannel channel,

        @NotNull
        Boolean enabled,

        @NotNull
        Boolean preferred
) {
}
