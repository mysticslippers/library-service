package me.ifmo.backend.user.application.impl;

import me.ifmo.backend.user.domain.enums.RoleCode;
import me.ifmo.backend.user.domain.enums.UserBlockStatus;
import me.ifmo.backend.user.domain.enums.UserStatus;
import me.ifmo.backend.user.domain.enums.UserWarningStatus;
import me.ifmo.backend.user.domain.Role;
import me.ifmo.backend.user.domain.User;
import me.ifmo.backend.user.domain.UserRole;
import me.ifmo.backend.user.mapper.UserBlockMapper;
import me.ifmo.backend.user.mapper.UserMapper;
import me.ifmo.backend.user.mapper.UserWarningMapper;
import me.ifmo.backend.user.persistence.RoleRepository;
import me.ifmo.backend.user.persistence.UserBlockRepository;
import me.ifmo.backend.user.persistence.UserRepository;
import me.ifmo.backend.user.persistence.UserRoleRepository;
import me.ifmo.backend.user.persistence.UserWarningRepository;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.catalog.response.MaterialShortResponse;
import me.ifmo.backend.dto.circulation.response.ReservationResponse;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.dto.notification.request.CreateNotificationRequest;
import me.ifmo.backend.user.web.request.*;
import me.ifmo.backend.user.web.response.UserAdminResponse;
import me.ifmo.backend.user.web.response.UserProfileResponse;
import me.ifmo.backend.entities.*;
import me.ifmo.backend.library.domain.Branch;
import me.ifmo.backend.library.domain.Library;
import me.ifmo.backend.entities.enums.*;
import me.ifmo.backend.library.domain.enums.BranchStatus;
import me.ifmo.backend.user.domain.id.UserRoleId;
import me.ifmo.backend.shared.error.BusinessRuleException;
import me.ifmo.backend.shared.error.DuplicateResourceException;
import me.ifmo.backend.shared.error.ResourceNotFoundException;
import me.ifmo.backend.mappers.*;
import me.ifmo.backend.repositories.*;
import me.ifmo.backend.library.persistence.BranchRepository;
import me.ifmo.backend.services.AuditLogService;
import me.ifmo.backend.services.NotificationService;
import me.ifmo.backend.user.application.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final Set<LoanStatus> ACTIVE_LOAN_STATUSES =
            Set.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE);
    private static final Set<ReservationStatus> ACTIVE_RESERVATION_STATUSES =
            Set.of(ReservationStatus.ACTIVE, ReservationStatus.READY_FOR_PICKUP);
    private static final Pageable CARD_RELATED_PAGEABLE = PageRequest.of(0, 20);

    private final UserRepository repository;
    private final BranchRepository branchRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserBlockRepository userBlockRepository;
    private final UserWarningRepository userWarningRepository;
    private final LoanRepository loanRepository;
    private final ReservationRepository reservationRepository;
    private final FineRepository fineRepository;
    private final MaterialAuthorRepository materialAuthorRepository;
    private final MaterialGenreRepository materialGenreRepository;
    private final UserMapper userMapper;
    private final UserBlockMapper userBlockMapper;
    private final UserWarningMapper userWarningMapper;
    private final LoanMapper loanMapper;
    private final ReservationMapper reservationMapper;
    private final FineMapper fineMapper;
    private final MaterialMapper materialMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    private String normalize(String value, String fieldName) {
        if(fieldName.equals("Middle name")){
            if (value == null || value.strip().isBlank())
                return null;

        } else {
            if(fieldName.equals("Email"))
                value = value.toLowerCase(Locale.ROOT);

            if (value == null || value.strip().isBlank())
                throw new BusinessRuleException("%s must not be blank".formatted(fieldName));
        }
        return value.strip();
    }

    private Branch findActiveBranch(Long branchId) {
        Branch branch = branchRepository.findById(branchId).orElseThrow(
                () -> new ResourceNotFoundException("Branch with id '%s' not found".formatted(branchId)));

        if (branch.getStatus() != BranchStatus.ACTIVE)
            throw new BusinessRuleException("Home branch must be active");

        return branch;
    }

    private UserAdminResponse toAdminResponse(User user) {
        List<UserRole> userRoles = userRoleRepository.findByUser_Id(user.getId());
        UserAdminResponse base = userMapper.toAdminResponse(user, userRoles);

        return new UserAdminResponse(base.id(), base.email(), base.phone(), base.firstName(), base.lastName(),
                base.middleName(), base.status(), base.homeBranchId(), base.homeBranchName(), base.registeredAt(),
                base.activatedAt(), base.lastLoginAt(), base.lockedUntil(), base.roles(),
                userBlockRepository.findByUser_IdAndStatus(user.getId(), UserBlockStatus.ACTIVE)
                        .map(userBlockMapper::toResponse).orElse(null),
                userWarningRepository.findByUser_IdAndStatus(user.getId(), UserWarningStatus.ACTIVE, CARD_RELATED_PAGEABLE)
                        .map(userWarningMapper::toResponse).toList(),
                loanRepository.findByUser_IdAndStatusIn(user.getId(), ACTIVE_LOAN_STATUSES, CARD_RELATED_PAGEABLE)
                        .map(loanMapper::toResponse).toList(),
                reservationRepository.findByUser_IdAndStatusIn(user.getId(), ACTIVE_RESERVATION_STATUSES, CARD_RELATED_PAGEABLE)
                        .map(this::toReservationResponse).toList(),
                fineRepository.findByUser_IdAndStatus(user.getId(), FineStatus.ACTIVE, CARD_RELATED_PAGEABLE)
                        .map(fineMapper::toResponse).toList());
    }

    private UserProfileResponse toProfileResponse(User user) {
        List<RoleCode> roles = userRoleRepository.findRoleCodesByUser_Id(user.getId());
        return userMapper.toProfileResponse(user, new LinkedHashSet<>(roles));
    }

    private boolean isTransitionAllowed(UserStatus current, UserStatus target) {
        return switch (current) {
            case PENDING_ACTIVATION -> target == UserStatus.ACTIVE || target == UserStatus.BLOCKED
                            || target == UserStatus.ARCHIVED;

            case ACTIVE -> target == UserStatus.BLOCKED || target == UserStatus.INACTIVE
                            || target == UserStatus.ARCHIVED;

            case BLOCKED -> target == UserStatus.ACTIVE || target == UserStatus.INACTIVE
                            || target == UserStatus.ARCHIVED;

            case INACTIVE -> target == UserStatus.ACTIVE || target == UserStatus.ARCHIVED;
            case ARCHIVED -> false;
        };
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

    private User findActiveActor(Long actorUserId) {
        User actor = repository.findById(actorUserId).orElseThrow(
                () -> new ResourceNotFoundException("Actor user with id '%s' not found".formatted(actorUserId)));

        if (actor.getStatus() != UserStatus.ACTIVE)
            throw new BusinessRuleException("Actor user must be active");

        if (maxRoleRank(actor.getId()) < roleRank(RoleCode.LIBRARIAN))
            throw new BusinessRuleException("Actor user must be library staff");

        return actor;
    }

    private void validateActorCanManageTarget(User actor, User target) {
        if (actor.getId().equals(target.getId()))
            throw new BusinessRuleException("User cannot manage own account");

        if (maxRoleRank(actor.getId()) <= maxRoleRank(target.getId()))
            throw new BusinessRuleException("Insufficient access level for target user");
    }

    private void validateActorCanAssignRole(User actor, RoleCode roleCode) {
        if (maxRoleRank(actor.getId()) <= roleRank(roleCode))
            throw new BusinessRuleException("Insufficient access level for target role");
    }

    private MaterialShortResponse toMaterialShortResponse(Material material) {
        List<MaterialAuthor> authors = materialAuthorRepository.findByMaterial_IdOrderByAuthorOrderAsc(material.getId());
        List<MaterialGenre> genres = materialGenreRepository.findByMaterial_Id(material.getId());
        return materialMapper.toShortResponse(material, authors, genres);
    }

    private ReservationResponse toReservationResponse(Reservation reservation) {
        return reservationMapper.toResponse(reservation, toMaterialShortResponse(reservation.getMaterial()));
    }

    private void recordUserAudit(Long actorUserId, Long userId, AuditAction action, Map<String, Object> details) {
        auditLogService.record(actorUserId, AuditEntityType.USER, userId, action, details);
    }

    private void notifyUser(User user, String subject, String body) {
        notificationService.create(new CreateNotificationRequest(
                user.getId(), null, null, null, NotificationType.ACCOUNT_STATUS_CHANGED, NotificationChannel.EMAIL, subject, body));
    }

    private void assignRoleIfAbsent(User user, RoleCode roleCode) {
        Role role = roleRepository.findByCode(roleCode).orElseThrow(
                () -> new ResourceNotFoundException("Role with code '%s' not found".formatted(roleCode)));

        if (userRoleRepository.existsByUser_IdAndRole_Code(user.getId(), roleCode))
            return;

        UserRole userRole = UserRole.builder().id(new UserRoleId(user.getId(), role.getId())).user(user)
                .role(role).build();

        userRoleRepository.save(userRole);
    }

    @Override
    @Transactional
    public UserAdminResponse create(Long actorUserId, CreateUserRequest request) {
        User actor = findActiveActor(actorUserId);

        String email = normalize(request.email(), "Email");
        String phone = normalize(request.phone(), "Phone");
        String firstName = normalize(request.firstName(), "First name");
        String lastName = normalize(request.lastName(), "Last name");
        String middleName = normalize(request.middleName(), "Middle name");

        if (repository.existsByEmail(email))
            throw new DuplicateResourceException("User with email '%s' already exists".formatted(email));

        if (repository.existsByPhone(phone))
            throw new DuplicateResourceException("User with phone '%s' already exists".formatted(phone));

        Set<RoleCode> roles = request.roles() == null || request.roles().isEmpty() ? Set.of(RoleCode.READER) : request.roles();
        roles.forEach(role -> validateActorCanAssignRole(actor, role));

        User user = userMapper.toEntity(new CreateUserRequest(email, phone, request.password(), firstName, lastName,
                middleName, request.homeBranchId(), roles));

        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(UserStatus.ACTIVE);
        user.setActivatedAt(LocalDateTime.now());

        if (request.homeBranchId() != null)
            user.setBranch(findActiveBranch(request.homeBranchId()));

        User saved = repository.save(user);

        for (RoleCode roleCode : roles)
            assignRoleIfAbsent(saved, roleCode);

        recordUserAudit(actor.getId(), saved.getId(), AuditAction.CREATE,
                Map.of("roles", roles.stream().map(RoleCode::name).toList()));

        return toAdminResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public UserAdminResponse getUserById(Long id) {
        User user = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("User with id '%s' not found".formatted(id)));

        return toAdminResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long id) {
        User user = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("User with id '%s' not found".formatted(id)));

        return toProfileResponse(user);
    }

    @Override
    @Transactional
    public UserAdminResponse update(Long actorUserId, Long id, UpdateUserRequest request) {
        User actor = findActiveActor(actorUserId);
        User user = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("User with id '%s' not found".formatted(id)));
        validateActorCanManageTarget(actor, user);

        if (user.getStatus() == UserStatus.ARCHIVED)
            throw new BusinessRuleException("Archived user cannot be updated");

        String email = request.email() != null ? normalize(request.email(), "Email") : null;
        String phone = request.phone() != null ? normalize(request.phone(), "Phone") : null;
        String firstName = request.firstName() != null ? normalize(request.firstName(), "First name") : null;
        String lastName = request.lastName() != null ? normalize(request.lastName(), "Last name") : null;
        String middleName = request.middleName() != null ? normalize(request.middleName(), "Middle name") : null;

        if (email != null && !email.equals(user.getEmail()) && repository.existsByEmail(email))
            throw new DuplicateResourceException("User with email '%s' already exists".formatted(email));

        if (phone != null && !phone.equals(user.getPhone()) && repository.existsByPhone(phone))
            throw new DuplicateResourceException("User with phone '%s' already exists".formatted(phone));

        userMapper.updateEntity(new UpdateUserRequest(email, phone, firstName, lastName, middleName, request.homeBranchId()), user);

        if (request.middleName() != null)
            user.setMiddleName(middleName);

        if (request.homeBranchId() != null)
            user.setBranch(findActiveBranch(request.homeBranchId()));

        User saved = repository.save(user);
        recordUserAudit(actor.getId(), saved.getId(), AuditAction.UPDATE, Map.of("updated", true));
        return toAdminResponse(saved);
    }

    @Override
    @Transactional
    public UserAdminResponse changeStatus(Long actorUserId, Long id, ChangeUserStatusRequest request) {
        User actor = findActiveActor(actorUserId);
        User user = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("User with id '%s' not found".formatted(id)));
        validateActorCanManageTarget(actor, user);

        UserStatus targetStatus = request.status();

        if (user.getStatus() == targetStatus)
            return toAdminResponse(user);

        if (!isTransitionAllowed(user.getStatus(), targetStatus))
            throw new BusinessRuleException("User status transition from '%s' to '%s' is not allowed".formatted(user.getStatus(), targetStatus));

        if (targetStatus == UserStatus.BLOCKED || (user.getStatus() == UserStatus.BLOCKED && targetStatus == UserStatus.ACTIVE))
            throw new BusinessRuleException("Use user block operations to block or unblock users");

        if (targetStatus == UserStatus.ACTIVE && user.getActivatedAt() == null)
            user.setActivatedAt(LocalDateTime.now());

        if (targetStatus == UserStatus.ACTIVE) {
            user.setLockedUntil(null);
            user.setFailedLoginAttempts((short) 0);
        }

        user.setStatus(targetStatus);

        User saved = repository.save(user);
        recordUserAudit(actor.getId(), saved.getId(), AuditAction.STATUS_CHANGED,
                Map.of("status", targetStatus.name(), "reason", request.reason() != null ? request.reason() : ""));
        notifyUser(saved, "Library account status changed",
                "Your account status has been changed to %s.".formatted(targetStatus));
        return toAdminResponse(saved);
    }

    @Override
    @Transactional
    public UserAdminResponse assignRole(Long actorUserId, Long id, AssignUserRoleRequest request) {
        User actor = findActiveActor(actorUserId);
        User user = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("User with id '%s' not found".formatted(id)));
        validateActorCanManageTarget(actor, user);
        validateActorCanAssignRole(actor, request.roleCode());

        if (user.getStatus() == UserStatus.ARCHIVED)
            throw new BusinessRuleException("Cannot assign role to archived user");

        assignRoleIfAbsent(user, request.roleCode());

        recordUserAudit(actor.getId(), user.getId(), AuditAction.ROLE_CHANGED,
                Map.of("assignedRole", request.roleCode().name()));
        notifyUser(user, "Library account role changed",
                "Role %s has been assigned to your account.".formatted(request.roleCode()));

        return toAdminResponse(user);
    }

    @Override
    @Transactional
    public UserAdminResponse revokeRole(Long actorUserId, Long id, AssignUserRoleRequest request) {
        User actor = findActiveActor(actorUserId);
        User user = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("User with id '%s' not found".formatted(id)));
        validateActorCanManageTarget(actor, user);
        validateActorCanAssignRole(actor, request.roleCode());

        UserRole userRole = userRoleRepository.findByUser_IdAndRole_Code(user.getId(), request.roleCode()).orElseThrow(
                () -> new ResourceNotFoundException("User role '%s' not found for user with id '%s'".formatted(request.roleCode(), user.getId())));

        List<UserRole> currentRoles = userRoleRepository.findByUser_Id(user.getId());

        if (currentRoles.size() <= 1)
            throw new BusinessRuleException("User must have at least one role");

        userRoleRepository.delete(userRole);

        recordUserAudit(actor.getId(), user.getId(), AuditAction.ROLE_CHANGED,
                Map.of("revokedRole", request.roleCode().name()));
        notifyUser(user, "Library account role changed",
                "Role %s has been revoked from your account.".formatted(request.roleCode()));

        return toAdminResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserAdminResponse> search(UserSearchRequest request, Pageable pageable) {
        String normalizedQuery = request.query() != null ? request.query().strip().toLowerCase(Locale.ROOT) : "";
        Specification<User> specification = (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();

        if (!normalizedQuery.isBlank()) {
            String pattern = "%" + normalizedQuery + "%";
            specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), pattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("phone")), pattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("firstName")), pattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("lastName")), pattern),
                    criteriaBuilder.like(criteriaBuilder.lower(
                            criteriaBuilder.coalesce(root.get("middleName"), "")), pattern)
            ));
        }
        if (request.status() != null)
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("status"), request.status()));
        if (request.homeBranchId() != null)
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("branch").get("id"), request.homeBranchId()));

        Page<User> users = repository.findAll(specification, pageable);

        Page<UserAdminResponse> responses = users.map(this::toAdminResponse);

        return PageResponse.from(responses);
    }
}
