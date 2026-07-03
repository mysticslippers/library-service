package me.ifmo.backend.dto.notification.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import me.ifmo.backend.entities.enums.NotificationStatus;

public record UpdateNotificationStatusRequest(
        @NotNull
        NotificationStatus status,

        @Size(max = 255)
        String externalMessageId,

        @Size(max = 2000)
        String errorMessage
) {
}
