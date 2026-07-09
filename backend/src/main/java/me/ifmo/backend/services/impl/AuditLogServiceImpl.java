package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.audit.request.AuditLogSearchRequest;
import me.ifmo.backend.dto.audit.response.AuditLogResponse;
import me.ifmo.backend.dto.common.response.PageResponse;
import me.ifmo.backend.entities.AuditLog;
import me.ifmo.backend.entities.User;
import me.ifmo.backend.entities.enums.AuditAction;
import me.ifmo.backend.entities.enums.AuditEntityType;
import me.ifmo.backend.exceptions.domain.BusinessRuleException;
import me.ifmo.backend.exceptions.domain.ResourceNotFoundException;
import me.ifmo.backend.mappers.AuditLogMapper;
import me.ifmo.backend.repositories.AuditLogRepository;
import me.ifmo.backend.repositories.UserRepository;
import me.ifmo.backend.services.AuditLogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository repository;
    private final UserRepository userRepository;
    private final AuditLogMapper auditLogMapper;

    @Override
    @Transactional
    public AuditLogResponse record(Long actorUserId, AuditEntityType type, Long entityId, AuditAction action,
                                   Map<String, Object> details) {

        if (type == null)
            throw new BusinessRuleException("Audit entity type must not be null");

        if (action == null)
            throw new BusinessRuleException("Audit action must not be null");

        User actor = null;

        if (actorUserId != null)
            actor = userRepository.findById(actorUserId).orElseThrow(
                    () -> new ResourceNotFoundException("Actor user with id '%s' not found".formatted(actorUserId)));

        AuditLog auditLog = auditLogMapper.toEntity(actor, type, entityId, action, details != null ? details : Map.of());

        AuditLog saved = repository.save(auditLog);
        return auditLogMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public AuditLogResponse getAuditLogById(Long id) {
        AuditLog auditLog = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("AuditLog with id '%s' not found".formatted(id)));

        return auditLogMapper.toResponse(auditLog);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> search(AuditLogSearchRequest request, Pageable pageable) {
        Page<AuditLog> auditLogs = repository.search(request.actorUserId(), request.type(), request.entityId(),
                request.action(), request.createdFrom(), request.createdTo(), pageable);

        Page<AuditLogResponse> responses = auditLogs.map(auditLogMapper::toResponse);

        return PageResponse.from(responses);
    }
}
