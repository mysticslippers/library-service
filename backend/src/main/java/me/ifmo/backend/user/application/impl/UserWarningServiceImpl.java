package me.ifmo.backend.user.application.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.notification.web.request.CreateNotificationRequest;
import me.ifmo.backend.user.web.request.CancelUserWarningRequest;
import me.ifmo.backend.user.web.request.CreateUserWarningRequest;
import me.ifmo.backend.user.web.response.UserWarningResponse;
import me.ifmo.backend.user.domain.User;
import me.ifmo.backend.user.domain.UserWarning;
import me.ifmo.backend.audit.domain.enums.AuditAction;
import me.ifmo.backend.audit.domain.enums.AuditEntityType;
import me.ifmo.backend.notification.domain.enums.NotificationChannel;
import me.ifmo.backend.notification.domain.enums.NotificationType;
import me.ifmo.backend.user.domain.enums.RoleCode;
import me.ifmo.backend.user.domain.enums.UserStatus;
import me.ifmo.backend.user.domain.enums.UserWarningStatus;
import me.ifmo.backend.shared.error.BusinessRuleException;
import me.ifmo.backend.shared.error.ResourceNotFoundException;
import me.ifmo.backend.user.mapper.UserWarningMapper;
import me.ifmo.backend.user.persistence.UserRepository;
import me.ifmo.backend.user.persistence.UserRoleRepository;
import me.ifmo.backend.user.persistence.UserWarningRepository;
import me.ifmo.backend.audit.application.AuditLogService;
import me.ifmo.backend.notification.application.NotificationService;
import me.ifmo.backend.user.application.UserWarningService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserWarningServiceImpl implements UserWarningService {

    private final UserWarningRepository repository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserWarningMapper userWarningMapper;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    private String normalize(String value, String fieldName) {
        if(fieldName.equals("Comment")){
            if (value == null)
                return null;

            String normalized = value.strip();
            return normalized.isBlank() ? null : normalized;
        } else {
            if (value == null || value.strip().isBlank())
                throw new BusinessRuleException("%s must not be blank".formatted(fieldName));

            return value.strip();
        }
    }

    private String normalizeReason(String value) {
        if (value == null || value.strip().isBlank())
            throw new BusinessRuleException("%s must not be blank".formatted("Warning reason"));

        return value.strip();
    }

    private User findTargetUser(Long id) {
        User user = userRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("User with id '%s' not found".formatted(id)));

        if (user.getStatus() == UserStatus.ARCHIVED)
            throw new BusinessRuleException("Archived user cannot receive warnings");

        return user;
    }

    private User findActiveActor(Long id) {
        User user = userRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Created by user with id '%s' not found".formatted(id)));

        if (user.getStatus() != UserStatus.ACTIVE)
            throw new BusinessRuleException("Created by user must be active");

        if (maxRoleRank(user.getId()) < roleRank(RoleCode.LIBRARIAN))
            throw new BusinessRuleException("Created by user must be library staff");

        return user;
    }

    private int roleRank(RoleCode roleCode) {
        return switch (roleCode) {
            case READER -> 1;
            case LIBRARIAN -> 2;
            case ADMIN -> 3;
        };
    }

    private int maxRoleRank(Long userId) {
        return userRoleRepository.findRoleCodesByUser_Id(userId).stream()
                .mapToInt(this::roleRank)
                .max()
                .orElse(0);
    }

    private void validateActorCanManageTarget(User actor, User target) {
        if (actor.getId().equals(target.getId()))
            throw new BusinessRuleException("User cannot manage own account");

        if (maxRoleRank(actor.getId()) <= maxRoleRank(target.getId()))
            throw new BusinessRuleException("Insufficient access level for target user");
    }

    private void notifyUser(User user, String reason) {
        notificationService.create(new CreateNotificationRequest(user.getId(), null, null, null,
                NotificationType.USER_WARNING_CREATED, NotificationChannel.EMAIL,
                "Library account warning", "A warning has been added to your account. Reason: %s".formatted(reason)));
    }

    @Override
    @Transactional
    public UserWarningResponse create(Long createdByUserId, CreateUserWarningRequest request) {
        User user = findTargetUser(request.userId());
        User createdByUser = findActiveActor(createdByUserId);
        validateActorCanManageTarget(createdByUser, user);

        String reason = normalizeReason(request.reason());
        String comment = normalize(request.comment(), "Comment");

        if (request.expiresAt() != null && !request.expiresAt().isAfter(LocalDateTime.now()))
            throw new BusinessRuleException("Warning expiresAt must be in the future");

        CreateUserWarningRequest normalizedRequest = new CreateUserWarningRequest(user.getId(), reason, comment, request.expiresAt());

        UserWarning warning = userWarningMapper.toEntity(normalizedRequest, user, createdByUser);
        warning.setStatus(UserWarningStatus.ACTIVE);

        UserWarning saved = repository.save(warning);
        auditLogService.record(createdByUser.getId(), AuditEntityType.USER_WARNING, saved.getId(), AuditAction.WARNING_CREATED,
                Map.of("userId", user.getId(), "reason", reason));
        notifyUser(user, reason);
        return userWarningMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public UserWarningResponse getUserWarningById(Long id) {
        UserWarning warning = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("User warning with id '%s' not found".formatted(id)));

        return userWarningMapper.toResponse(warning);
    }

    @Override
    @Transactional
    public UserWarningResponse cancel(Long id, CancelUserWarningRequest request) {
        UserWarning warning = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("User warning with id '%s' not found".formatted(id)));

        if (warning.getStatus() != UserWarningStatus.ACTIVE)
            throw new BusinessRuleException("Only active user warning can be cancelled");

        if (request.reason() != null)
            normalize(request.reason(), "Cancellation reason");

        warning.setStatus(UserWarningStatus.CANCELLED);

        UserWarning saved = repository.save(warning);
        return userWarningMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public UserWarningResponse expire(Long id) {
        UserWarning warning = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("User warning with id '%s' not found".formatted(id)));

        if (warning.getStatus() != UserWarningStatus.ACTIVE)
            throw new BusinessRuleException("Only active user warning can be expired");

        if (warning.getExpiresAt() == null || warning.getExpiresAt().isAfter(LocalDateTime.now()))
            throw new BusinessRuleException("User warning has not expired yet");

        warning.setStatus(UserWarningStatus.EXPIRED);

        UserWarning saved = repository.save(warning);
        return userWarningMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserWarningResponse> search(Long userId, Long createdByUserId, UserWarningStatus status, Pageable pageable) {
        Specification<UserWarning> specification = (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();

        if (userId != null)
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("user").get("id"), userId));
        if (createdByUserId != null)
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("createdByUser").get("id"), createdByUserId));
        if (status != null)
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("status"), status));

        Page<UserWarningResponse> responses = repository.findAll(specification, pageable).map(userWarningMapper::toResponse);
        return PageResponse.from(responses);
    }
}
