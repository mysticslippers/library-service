package me.ifmo.backend.audit.web.response;

import me.ifmo.backend.user.web.response.UserShortResponse;
import me.ifmo.backend.audit.domain.enums.AuditAction;
import me.ifmo.backend.audit.domain.enums.AuditEntityType;

import java.time.LocalDateTime;
import java.util.Map;

public record AuditLogResponse(
        Long id,
        UserShortResponse actor,
        AuditEntityType type,
        Long entityId,
        AuditAction action,
        Map<String, Object> details,
        LocalDateTime createdAt
) {
}
