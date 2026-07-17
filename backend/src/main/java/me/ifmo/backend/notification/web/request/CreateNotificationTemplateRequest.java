package me.ifmo.backend.notification.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import me.ifmo.backend.notification.domain.enums.NotificationChannel;
import me.ifmo.backend.notification.domain.enums.NotificationType;

import java.util.List;

public record CreateNotificationTemplateRequest(
        @NotNull
        NotificationType type,

        @NotNull
        NotificationChannel channel,

        @Size(max = 255)
        String subjectTemplate,

        @NotBlank
        String bodyTemplate,

        List<@NotBlank @Size(max = 100) String> requiredParameters
) {
}
