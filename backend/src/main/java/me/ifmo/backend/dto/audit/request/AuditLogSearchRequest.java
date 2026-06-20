package me.ifmo.backend.dto.audit.request;

import me.ifmo.backend.entities.enums.AuditAction;
import me.ifmo.backend.entities.enums.AuditEntityType;

import java.time.LocalDateTime;

public record AuditLogSearchRequest(
        Long actorUserId,
        AuditEntityType type,
        Long entityId,
        AuditAction action,
        LocalDateTime createdFrom,
        LocalDateTime createdTo
) {
}
