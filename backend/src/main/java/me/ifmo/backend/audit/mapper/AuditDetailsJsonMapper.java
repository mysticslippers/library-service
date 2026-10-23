package me.ifmo.backend.audit.mapper;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.audit.application.AuditDetailsSanitizer;
import me.ifmo.backend.audit.domain.AuditLog;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class AuditDetailsJsonMapper {

    private final AuditDetailsSanitizer sanitizer;

    @Named("safeAuditDetails")
    public Map<String, Object> toMap(AuditLog auditLog) {
        return sanitizer.sanitize(auditLog.getType(), auditLog.getDetails());
    }
}
