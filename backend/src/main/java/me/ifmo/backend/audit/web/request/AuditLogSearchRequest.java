package me.ifmo.backend.audit.web.request;

import jakarta.validation.constraints.Positive;
import me.ifmo.backend.audit.domain.enums.AuditAction;
import me.ifmo.backend.audit.domain.enums.AuditEntityType;

import java.time.LocalDateTime;

public record AuditLogSearchRequest(
        @Positive
        Long actorUserId,

        AuditEntityType type,

        @Positive
        Long entityId,

        AuditAction action,

        LocalDateTime createdFrom,

        LocalDateTime createdTo
) {
}
