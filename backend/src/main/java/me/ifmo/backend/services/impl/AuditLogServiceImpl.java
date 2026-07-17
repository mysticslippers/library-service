package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.audit.request.AuditLogSearchRequest;
import me.ifmo.backend.dto.audit.response.AuditLogResponse;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.entities.AuditLog;
import me.ifmo.backend.entities.User;
import me.ifmo.backend.entities.enums.AuditAction;
import me.ifmo.backend.entities.enums.AuditEntityType;
import me.ifmo.backend.shared.error.BusinessRuleException;
import me.ifmo.backend.shared.error.ResourceNotFoundException;
import me.ifmo.backend.mappers.AuditLogMapper;
import me.ifmo.backend.repositories.AuditLogRepository;
import me.ifmo.backend.repositories.UserRepository;
import me.ifmo.backend.services.AuditLogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository repository;
    private final UserRepository userRepository;
    private final AuditLogMapper auditLogMapper;

    private void validate(AuditLogSearchRequest request) {
        if (request.createdFrom() != null && request.createdTo() != null && request.createdFrom().isAfter(request.createdTo()))
            throw new BusinessRuleException("Audit log createdFrom must not be after createdTo");
    }

    @Override
    @Transactional
    public AuditLogResponse record(Long actorUserId, AuditEntityType type, Long entityId, AuditAction action,
                                   Map<String, Object> details) {

        if (type == null)
            throw new BusinessRuleException("Audit entity type must not be null");

        if (action == null)
            throw new BusinessRuleException("Audit action must not be null");

        if (actorUserId != null && actorUserId <= 0)
            throw new BusinessRuleException("Actor user id must be positive");

        if (entityId != null && entityId <= 0)
            throw new BusinessRuleException("Audit entity id must be positive");

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
        validate(request);

        Specification<AuditLog> specification = (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();

        if (request.actorUserId() != null)
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("user").get("id"), request.actorUserId()));
        if (request.type() != null)
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("type"), request.type()));
        if (request.entityId() != null)
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("entityId"), request.entityId()));
        if (request.action() != null)
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("action"), request.action()));
        if (request.createdFrom() != null)
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), request.createdFrom()));
        if (request.createdTo() != null)
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), request.createdTo()));

        Pageable effectivePageable = pageable.getSort().isSorted() ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<AuditLog> auditLogs = repository.findAll(specification, effectivePageable);

        Page<AuditLogResponse> responses = auditLogs.map(auditLogMapper::toResponse);

        return PageResponse.from(responses);
    }
}
