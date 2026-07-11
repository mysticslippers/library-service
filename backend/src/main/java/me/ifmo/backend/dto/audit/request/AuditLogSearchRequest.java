package me.ifmo.backend.dto.audit.request;

import jakarta.validation.constraints.Positive;
import me.ifmo.backend.entities.enums.AuditAction;
import me.ifmo.backend.entities.enums.AuditEntityType;

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
