package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.notification.request.CreateNotificationRequest;
import me.ifmo.backend.dto.common.response.PageResponse;
import me.ifmo.backend.dto.user.request.CancelUserBlockRequest;
import me.ifmo.backend.dto.user.request.CreateUserBlockRequest;
import me.ifmo.backend.dto.user.response.UserBlockResponse;
import me.ifmo.backend.entities.User;
import me.ifmo.backend.entities.UserBlock;
import me.ifmo.backend.entities.enums.AuditAction;
import me.ifmo.backend.entities.enums.AuditEntityType;
import me.ifmo.backend.entities.enums.NotificationChannel;
import me.ifmo.backend.entities.enums.NotificationType;
import me.ifmo.backend.entities.enums.RoleCode;
import me.ifmo.backend.entities.enums.UserBlockStatus;
import me.ifmo.backend.entities.enums.UserStatus;
import me.ifmo.backend.exceptions.domain.BusinessRuleException;
import me.ifmo.backend.exceptions.domain.DuplicateResourceException;
import me.ifmo.backend.exceptions.domain.ResourceNotFoundException;
import me.ifmo.backend.mappers.UserBlockMapper;
import me.ifmo.backend.repositories.UserBlockRepository;
import me.ifmo.backend.repositories.UserRepository;
import me.ifmo.backend.repositories.UserRoleRepository;
import me.ifmo.backend.services.AuditLogService;
import me.ifmo.backend.services.NotificationService;
import me.ifmo.backend.services.UserBlockService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserBlockServiceImpl implements UserBlockService {

    private final UserBlockRepository repository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserBlockMapper userBlockMapper;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    private String normalize(String value, String fieldName) {
        if (value == null || value.strip().isBlank())
            throw new BusinessRuleException("%s must not be blank".formatted(fieldName));

        return value.strip();
    }

    private User findActiveUser(Long id, String fieldName) {
        User user = userRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("%s with id '%s' not found".formatted(fieldName, id)));

        if (user.getStatus() != UserStatus.ACTIVE)
            throw new BusinessRuleException("%s must be active".formatted(fieldName));

        return user;
    }

    private User findActiveStaff(Long id, String fieldName) {
        User user = findActiveUser(id, fieldName);

        if (maxRoleRank(user.getId()) < roleRank(RoleCode.LIBRARIAN))
            throw new BusinessRuleException("%s must be library staff".formatted(fieldName));

        return user;
    }

    private void activateUserIfBlocked(User user) {
        if (user.getStatus() == UserStatus.BLOCKED)
            user.setStatus(UserStatus.ACTIVE);
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

    private void notifyUser(User user, NotificationType type, String subject, String body) {
        notificationService.create(new CreateNotificationRequest(user.getId(), null, null, null,
                type, NotificationChannel.EMAIL, subject, body));
    }

    @Override
    @Transactional
    public UserBlockResponse create(Long createdByUserId, CreateUserBlockRequest request) {
        User user = findActiveUser(request.userId(), "User");
        User createdByUser = findActiveStaff(createdByUserId, "Created by user");
        validateActorCanManageTarget(createdByUser, user);

        if (repository.existsByUser_IdAndStatus(user.getId(), UserBlockStatus.ACTIVE))
            throw new DuplicateResourceException("User with id '%s' already has active block".formatted(user.getId()));

        String reason = normalize(request.reason(), "Block reason");

        if (request.expiresAt() != null && !request.expiresAt().isAfter(LocalDateTime.now()))
            throw new BusinessRuleException("Block expiresAt must be in the future");

        CreateUserBlockRequest normalizedRequest = new CreateUserBlockRequest(user.getId(), reason, request.expiresAt());

        UserBlock block = userBlockMapper.toEntity(normalizedRequest, user, createdByUser);
        block.setStatus(UserBlockStatus.ACTIVE);
        user.setStatus(UserStatus.BLOCKED);

        UserBlock saved = repository.save(block);
        auditLogService.record(createdByUser.getId(), AuditEntityType.USER_BLOCK, saved.getId(), AuditAction.BLOCK,
                Map.of("userId", user.getId(), "reason", reason));

        notifyUser(user, NotificationType.USER_BLOCKED, "Library account blocked",
                "Your account has been blocked. Reason: %s".formatted(reason));
        return userBlockMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public UserBlockResponse getUserBlockById(Long id) {
        UserBlock block = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("User block with id '%s' not found".formatted(id)));

        return userBlockMapper.toResponse(block);
    }

    @Override
    @Transactional
    public UserBlockResponse cancel(Long id, Long unblockedByUserId, CancelUserBlockRequest request) {
        UserBlock block = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("User block with id '%s' not found".formatted(id)));

        if (block.getStatus() != UserBlockStatus.ACTIVE)
            throw new BusinessRuleException("Only active user block can be cancelled");

        User unblockedByUser = findActiveStaff(unblockedByUserId, "Unblocked by user");
        validateActorCanManageTarget(unblockedByUser, block.getUser());

        String reason = normalize(request.reason(), "Cancellation reason");

        block.setStatus(UserBlockStatus.CANCELLED);
        block.setUnblockedByUser(unblockedByUser);
        block.setUnblockReason(reason);
        block.setUnblockedAt(LocalDateTime.now());
        activateUserIfBlocked(block.getUser());

        UserBlock saved = repository.save(block);
        auditLogService.record(unblockedByUser.getId(), AuditEntityType.USER_BLOCK, saved.getId(), AuditAction.UNBLOCK,
                Map.of("userId", block.getUser().getId(), "reason", reason));
        notifyUser(block.getUser(), NotificationType.USER_UNBLOCKED, "Library account unblocked",
                "Your account has been unblocked. Reason: %s".formatted(reason));
        return userBlockMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public UserBlockResponse expire(Long id) {
        UserBlock block = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("User block with id '%s' not found".formatted(id)));

        if (block.getStatus() != UserBlockStatus.ACTIVE)
            throw new BusinessRuleException("Only active user block can be expired");

        if (block.getExpiresAt() == null || block.getExpiresAt().isAfter(LocalDateTime.now()))
            throw new BusinessRuleException("User block has not expired yet");

        block.setStatus(UserBlockStatus.EXPIRED);
        block.setUnblockReason("Block expired");
        block.setUnblockedAt(LocalDateTime.now());
        activateUserIfBlocked(block.getUser());

        UserBlock saved = repository.save(block);
        auditLogService.record(null, AuditEntityType.USER_BLOCK, saved.getId(), AuditAction.UNBLOCK,
                Map.of("userId", block.getUser().getId(), "reason", "Block expired"));
        notifyUser(block.getUser(), NotificationType.USER_UNBLOCKED, "Library account unblocked",
                "Your account has been unblocked because the block expired.");
        return userBlockMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserBlockResponse> search(Long userId, Long createdByUserId, UserBlockStatus status, Pageable pageable) {
        Specification<UserBlock> specification = (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();

        if (userId != null)
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("user").get("id"), userId));
        if (createdByUserId != null)
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("createdByUser").get("id"), createdByUserId));
        if (status != null)
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("status"), status));

        Page<UserBlockResponse> responses = repository.findAll(specification, pageable).map(userBlockMapper::toResponse);
        return PageResponse.from(responses);
    }
}
