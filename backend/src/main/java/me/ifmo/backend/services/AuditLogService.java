package me.ifmo.backend.services;

import me.ifmo.backend.dto.audit.request.AuditLogSearchRequest;
import me.ifmo.backend.dto.audit.response.AuditLogResponse;
import me.ifmo.backend.dto.common.response.PageResponse;
import me.ifmo.backend.entities.enums.AuditAction;
import me.ifmo.backend.entities.enums.AuditEntityType;
import org.springframework.data.domain.Pageable;

import java.util.Map;

public interface AuditLogService {

    AuditLogResponse record(Long actorUserId, AuditEntityType type, Long entityId,
                            AuditAction action, Map<String, Object> details);

    AuditLogResponse getAuditLogById(Long id);

    PageResponse<AuditLogResponse> search(AuditLogSearchRequest request, Pageable pageable);
}
