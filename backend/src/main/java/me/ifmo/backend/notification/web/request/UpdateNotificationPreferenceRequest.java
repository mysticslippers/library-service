package me.ifmo.backend.notification.web.request;

import jakarta.validation.constraints.NotNull;
import me.ifmo.backend.notification.domain.enums.NotificationChannel;
import me.ifmo.backend.notification.domain.enums.NotificationType;

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
