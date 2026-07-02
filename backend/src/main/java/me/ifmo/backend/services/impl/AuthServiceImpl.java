package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.auth.request.LoginRequest;
import me.ifmo.backend.dto.auth.request.RegisterRequest;
import me.ifmo.backend.dto.auth.response.AuthResponse;
import me.ifmo.backend.dto.user.response.UserProfileResponse;
import me.ifmo.backend.entities.Role;
import me.ifmo.backend.entities.User;
import me.ifmo.backend.entities.UserRole;
import me.ifmo.backend.entities.enums.RoleCode;
import me.ifmo.backend.entities.enums.UserStatus;
import me.ifmo.backend.entities.id.UserRoleId;
import me.ifmo.backend.exceptions.domain.BusinessRuleException;
import me.ifmo.backend.exceptions.domain.DuplicateResourceException;
import me.ifmo.backend.exceptions.domain.ResourceNotFoundException;
import me.ifmo.backend.mappers.UserMapper;
import me.ifmo.backend.repositories.RoleRepository;
import me.ifmo.backend.repositories.UserRepository;
import me.ifmo.backend.repositories.UserRoleRepository;
import me.ifmo.backend.services.AuthService;
import me.ifmo.backend.services.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final short MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCK_MINUTES = 15;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

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

    private AuthResponse toAuthResponse(User user) {
        List<RoleCode> roles = userRoleRepository.findRoleCodesByUser_Id(user.getId());
        String token = jwtService.generate(user, roles);

        return new AuthResponse(token, "Bearer", jwtService.getAccessTokenExpiresIn(),
                userMapper.toProfileResponse(user, new LinkedHashSet<>(roles)));
    }

    private void assignDefaultReaderRole(User user) {
        Role role = roleRepository.findByCode(RoleCode.READER).orElseThrow(
                () -> new ResourceNotFoundException("Role with code '%s' not found".formatted(RoleCode.READER)));

        UserRole userRole = UserRole.builder().id(new UserRoleId(user.getId(), role.getId())).user(user).role(role).build();

        userRoleRepository.save(userRole);
    }

    private void rejectInvalidCredentials(User user) {
        short attempts = (short) (user.getFailedLoginAttempts() + 1);
        user.setFailedLoginAttempts(attempts);

        if (attempts >= MAX_FAILED_ATTEMPTS)
            user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));

        userRepository.save(user);

        throw new BusinessRuleException("Invalid email or password");
    }

    private void validate(User user) {
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now()))
            throw new BusinessRuleException("User is temporarily locked");

        if (user.getStatus() != UserStatus.ACTIVE)
            throw new BusinessRuleException("User account is not active");
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (!request.password().equals(request.passwordConfirmation()))
            throw new BusinessRuleException("Password confirmation does not match");

        String email = normalize(request.email(), "Email");
        String phone = normalize(request.phone(), "Phone");
        String firstName = normalize(request.firstName(), "First name");
        String lastName = normalize(request.lastName(), "Last name");
        String middleName = normalize(request.middleName(), "Middle name");

        if (userRepository.existsByEmail(email))
            throw new DuplicateResourceException("User with email '%s' already exists".formatted(email));

        if (userRepository.existsByPhone(phone))
            throw new DuplicateResourceException("User with phone '%s' already exists".formatted(phone));

        User user = userMapper.toEntity(new RegisterRequest(email, phone, request.password(), request.passwordConfirmation(),
                firstName, lastName, middleName));

        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(UserStatus.ACTIVE);
        user.setActivatedAt(LocalDateTime.now());
        user.setFailedLoginAttempts((short) 0);
        user.setLockedUntil(null);

        User saved = userRepository.save(user);
        assignDefaultReaderRole(saved);

        return toAuthResponse(saved);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = normalize(request.email(), "Email");

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessRuleException("Invalid email or password"));

        validate(user);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash()))
            rejectInvalidCredentials(user);

        user.setFailedLoginAttempts((short) 0);
        user.setLockedUntil(null);
        user.setLastLoginAt(LocalDateTime.now());

        User saved = userRepository.save(user);

        return toAuthResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse me(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(
                () -> new ResourceNotFoundException("User with id '%s' not found".formatted(userId)));

        return userMapper.toProfileResponse(user, new LinkedHashSet<>(userRoleRepository.findRoleCodesByUser_Id(userId)));
    }
}
