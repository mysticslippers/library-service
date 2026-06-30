package me.ifmo.backend.services;

import me.ifmo.backend.dto.audit.response.AuditLogResponse;
import me.ifmo.backend.entities.enums.AuditAction;
import me.ifmo.backend.entities.enums.AuditEntityType;

import java.util.Map;

public interface AuditLogService {

    AuditLogResponse record(Long actorUserId, AuditEntityType type, Long entityId,
                            AuditAction action, Map<String, Object> details);

    AuditLogResponse getAuditLogById(Long id);
}
