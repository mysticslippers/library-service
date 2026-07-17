package me.ifmo.backend.notification.web.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import me.ifmo.backend.notification.domain.enums.NotificationStatus;

public record UpdateNotificationStatusRequest(
        @NotNull
        NotificationStatus status,

        @Size(max = 255)
        String externalMessageId,

        @Size(max = 2000)
        String errorMessage
) {
        public UpdateNotificationStatusRequest(NotificationStatus status) {
                this(status, null, null);
        }
}
