package me.ifmo.backend.notification.web.request;

import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateNotificationTemplateRequest(
        @Size(max = 255)
        String subjectTemplate,

        String bodyTemplate,

        List<@Size(max = 100) String> requiredParameters
) {
}
