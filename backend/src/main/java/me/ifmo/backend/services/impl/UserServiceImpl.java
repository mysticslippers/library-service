package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.user.request.CreateUserRequest;
import me.ifmo.backend.dto.user.response.UserAdminResponse;
import me.ifmo.backend.dto.user.response.UserProfileResponse;
import me.ifmo.backend.entities.Branch;
import me.ifmo.backend.entities.Role;
import me.ifmo.backend.entities.User;
import me.ifmo.backend.entities.UserRole;
import me.ifmo.backend.entities.enums.BranchStatus;
import me.ifmo.backend.entities.enums.RoleCode;
import me.ifmo.backend.entities.enums.UserStatus;
import me.ifmo.backend.entities.id.UserRoleId;
import me.ifmo.backend.exceptions.domain.BusinessRuleException;
import me.ifmo.backend.exceptions.domain.DuplicateResourceException;
import me.ifmo.backend.exceptions.domain.ResourceNotFoundException;
import me.ifmo.backend.mappers.UserMapper;
import me.ifmo.backend.repositories.BranchRepository;
import me.ifmo.backend.repositories.RoleRepository;
import me.ifmo.backend.repositories.UserRepository;
import me.ifmo.backend.repositories.UserRoleRepository;
import me.ifmo.backend.services.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository repository;
    private final BranchRepository branchRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserMapper mapper;
    private final PasswordEncoder passwordEncoder;

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
        return mapper.toAdminResponse(user, userRoles);
    }

    private UserProfileResponse toProfileResponse(User user) {
        List<RoleCode> roles = userRoleRepository.findRoleCodesByUser_Id(user.getId());
        return mapper.toProfileResponse(user, new LinkedHashSet<>(roles));
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
    public UserAdminResponse create(CreateUserRequest request) {
        String email = normalize(request.email(), "Email");
        String phone = normalize(request.phone(), "Phone");
        String firstName = normalize(request.firstName(), "First name");
        String lastName = normalize(request.lastName(), "Last name");
        String middleName = normalize(request.middleName(), "Middle name");

        if (repository.existsByEmail(email))
            throw new DuplicateResourceException("User with email '%s' already exists".formatted(email));

        if (repository.existsByPhone(phone))
            throw new DuplicateResourceException("User with phone '%s' already exists".formatted(phone));

        User user = mapper.toEntity(new CreateUserRequest(email, phone, request.password(), firstName, lastName,
                middleName, request.homeBranchId(), request.roles()));

        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(UserStatus.ACTIVE);
        user.setActivatedAt(LocalDateTime.now());

        if (request.homeBranchId() != null)
            user.setBranch(findActiveBranch(request.homeBranchId()));

        User saved = repository.save(user);

        Set<RoleCode> roles = request.roles() == null || request.roles().isEmpty() ? Set.of(RoleCode.READER) : request.roles();

        for (RoleCode roleCode : roles)
            assignRoleIfAbsent(saved, roleCode);

        return toAdminResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public UserAdminResponse getUserById(Long id) {
        User user = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("User with id '%s' not found".formatted(id)));

        return toAdminResponse(user);
    }
}
