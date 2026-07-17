package me.ifmo.backend.audit.application;

import me.ifmo.backend.audit.web.request.AuditLogSearchRequest;
import me.ifmo.backend.audit.web.response.AuditLogResponse;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.audit.domain.enums.AuditAction;
import me.ifmo.backend.audit.domain.enums.AuditEntityType;
import org.springframework.data.domain.Pageable;

import java.util.Map;

public interface AuditLogService {

    AuditLogResponse record(Long actorUserId, AuditEntityType type, Long entityId,
                            AuditAction action, Map<String, Object> details);

    AuditLogResponse getAuditLogById(Long id);

    PageResponse<AuditLogResponse> search(AuditLogSearchRequest request, Pageable pageable);
}
