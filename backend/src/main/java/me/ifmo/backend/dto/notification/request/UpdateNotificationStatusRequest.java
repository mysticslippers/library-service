package me.ifmo.backend.dto.notification.request;

import jakarta.validation.constraints.NotNull;
import me.ifmo.backend.entities.enums.NotificationStatus;

public record UpdateNotificationStatusRequest(
        @NotNull
        NotificationStatus status
) {
}
