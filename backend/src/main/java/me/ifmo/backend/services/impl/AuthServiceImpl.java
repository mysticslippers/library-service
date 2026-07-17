package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.auth.request.ActivateAccountRequest;
import me.ifmo.backend.dto.auth.request.LoginRequest;
import me.ifmo.backend.dto.auth.request.PasswordRecoveryRequest;
import me.ifmo.backend.dto.auth.request.PasswordResetRequest;
import me.ifmo.backend.dto.auth.request.RegisterRequest;
import me.ifmo.backend.dto.auth.request.ResendActivationRequest;
import me.ifmo.backend.dto.auth.response.AuthMessageResponse;
import me.ifmo.backend.dto.auth.response.AuthResponse;
import me.ifmo.backend.dto.notification.request.CreateNotificationRequest;
import me.ifmo.backend.dto.user.response.UserProfileResponse;
import me.ifmo.backend.entities.AuthToken;
import me.ifmo.backend.entities.Role;
import me.ifmo.backend.entities.User;
import me.ifmo.backend.entities.UserRole;
import me.ifmo.backend.entities.enums.AuthTokenType;
import me.ifmo.backend.entities.enums.NotificationChannel;
import me.ifmo.backend.entities.enums.NotificationType;
import me.ifmo.backend.entities.enums.RoleCode;
import me.ifmo.backend.entities.enums.UserStatus;
import me.ifmo.backend.entities.id.UserRoleId;
import me.ifmo.backend.shared.error.BusinessRuleException;
import me.ifmo.backend.shared.error.DuplicateResourceException;
import me.ifmo.backend.shared.error.ResourceNotFoundException;
import me.ifmo.backend.mappers.UserMapper;
import me.ifmo.backend.repositories.AuthTokenRepository;
import me.ifmo.backend.repositories.RoleRepository;
import me.ifmo.backend.repositories.UserRepository;
import me.ifmo.backend.repositories.UserRoleRepository;
import me.ifmo.backend.services.AuthService;
import me.ifmo.backend.services.JwtService;
import me.ifmo.backend.services.NotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final short MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCK_MINUTES = 15;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuthTokenRepository authTokenRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final NotificationService notificationService;

    @Value("${security.auth.activation-token-expiration-minutes:1440}")
    private long activationTokenExpirationMinutes;

    @Value("${security.auth.password-reset-token-expiration-minutes:60}")
    private long passwordResetTokenExpirationMinutes;

    private String normalize(String value, String fieldName) {
        if (fieldName.equals("Middle name")) {
            if (value == null || value.strip().isBlank())
                return null;

        } else {
            if (value == null || value.strip().isBlank())
                throw new BusinessRuleException("%s must not be blank".formatted(fieldName));

            if (fieldName.equals("Email"))
                value = value.toLowerCase(Locale.ROOT);
        }
        return value.strip();
    }

    private AuthResponse toAuthResponse(User user) {
        List<RoleCode> roles = userRoleRepository.findRoleCodesByUser_Id(user.getId());
        if (roles.isEmpty())
            throw new BusinessRuleException("User has no assigned roles");

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

    private AuthToken createToken(User user, AuthTokenType type, long expirationMinutes) {
        LocalDateTime now = LocalDateTime.now();
        authTokenRepository.findByUser_IdAndTypeAndUsedAtIsNull(user.getId(), type)
                .forEach(token -> token.setUsedAt(now));

        AuthToken authToken = AuthToken.builder()
                .user(user)
                .type(type)
                .token(UUID.randomUUID().toString())
                .expiresAt(now.plusMinutes(expirationMinutes))
                .build();

        return authTokenRepository.save(authToken);
    }

    private AuthToken findValidToken(String tokenValue, AuthTokenType type) {
        AuthToken token = authTokenRepository.findByTokenAndType(tokenValue, type).orElseThrow(
                () -> new BusinessRuleException("Invalid or expired token"));

        LocalDateTime now = LocalDateTime.now();
        if (token.isUsed() || token.isExpired(now))
            throw new BusinessRuleException("Invalid or expired token");

        return token;
    }

    private void sendActivationEmail(User user, String token) {
        notificationService.create(new CreateNotificationRequest(
                user.getId(),
                null,
                null,
                null,
                NotificationType.ACCOUNT_ACTIVATION,
                NotificationChannel.EMAIL,
                "Library account activation",
                "Use this activation code to confirm your account: %s".formatted(token)
        ));
    }

    private void sendPasswordRecoveryEmail(User user, String token) {
        notificationService.create(new CreateNotificationRequest(
                user.getId(),
                null,
                null,
                null,
                NotificationType.PASSWORD_RECOVERY,
                NotificationChannel.EMAIL,
                "Library password recovery",
                "Use this password recovery code to reset your password: %s".formatted(token)
        ));
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
    public AuthMessageResponse register(RegisterRequest request) {
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
        user.setStatus(UserStatus.PENDING_ACTIVATION);
        user.setActivatedAt(null);
        user.setFailedLoginAttempts((short) 0);
        user.setLockedUntil(null);

        User saved = userRepository.save(user);
        assignDefaultReaderRole(saved);
        AuthToken token = createToken(saved, AuthTokenType.ACCOUNT_ACTIVATION, activationTokenExpirationMinutes);
        sendActivationEmail(saved, token.getToken());

        return new AuthMessageResponse("Registration created. Check email for account activation code.");
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
    @Transactional
    public AuthMessageResponse activate(ActivateAccountRequest request) {
        AuthToken token = findValidToken(request.token(), AuthTokenType.ACCOUNT_ACTIVATION);
        User user = token.getUser();

        if (user.getStatus() != UserStatus.PENDING_ACTIVATION)
            throw new BusinessRuleException("User account cannot be activated");

        LocalDateTime now = LocalDateTime.now();
        user.setStatus(UserStatus.ACTIVE);
        user.setActivatedAt(now);
        user.setFailedLoginAttempts((short) 0);
        user.setLockedUntil(null);
        token.setUsedAt(now);

        userRepository.save(user);
        authTokenRepository.save(token);

        return new AuthMessageResponse("User account has been activated.");
    }

    @Override
    @Transactional
    public AuthMessageResponse resendActivation(ResendActivationRequest request) {
        String email = normalize(request.email(), "Email");
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User with email '%s' not found".formatted(email)));

        if (user.getStatus() != UserStatus.PENDING_ACTIVATION)
            throw new BusinessRuleException("Activation can be resent only for pending accounts");

        AuthToken token = createToken(user, AuthTokenType.ACCOUNT_ACTIVATION, activationTokenExpirationMinutes);
        sendActivationEmail(user, token.getToken());

        return new AuthMessageResponse("Activation email has been sent.");
    }

    @Override
    @Transactional
    public AuthMessageResponse requestPasswordRecovery(PasswordRecoveryRequest request) {
        String email = normalize(request.email(), "Email");
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User with email '%s' not found".formatted(email)));

        if (user.getStatus() == UserStatus.ARCHIVED)
            throw new BusinessRuleException("Password recovery is not available for archived accounts");

        AuthToken token = createToken(user, AuthTokenType.PASSWORD_RESET, passwordResetTokenExpirationMinutes);
        sendPasswordRecoveryEmail(user, token.getToken());

        return new AuthMessageResponse("Password recovery email has been sent.");
    }

    @Override
    @Transactional
    public AuthMessageResponse resetPassword(PasswordResetRequest request) {
        if (!request.newPassword().equals(request.newPasswordConfirmation()))
            throw new BusinessRuleException("Password confirmation does not match");

        AuthToken token = findValidToken(request.token(), AuthTokenType.PASSWORD_RESET);
        User user = token.getUser();

        if (user.getStatus() == UserStatus.ARCHIVED)
            throw new BusinessRuleException("Password reset is not available for archived accounts");

        LocalDateTime now = LocalDateTime.now();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setFailedLoginAttempts((short) 0);
        user.setLockedUntil(null);
        token.setUsedAt(now);

        userRepository.save(user);
        authTokenRepository.save(token);

        return new AuthMessageResponse("Password has been changed.");
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse me(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(
                () -> new ResourceNotFoundException("User with id '%s' not found".formatted(userId)));

        return userMapper.toProfileResponse(user, new LinkedHashSet<>(userRoleRepository.findRoleCodesByUser_Id(userId)));
    }
}
