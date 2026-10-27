package me.ifmo.backend.authentication.application.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.authentication.application.AuthService;
import me.ifmo.backend.authentication.domain.AuthToken;
import me.ifmo.backend.authentication.domain.enums.AuthTokenType;
import me.ifmo.backend.authentication.persistence.AuthTokenRepository;
import me.ifmo.backend.authentication.persistence.RegistrationStore;
import me.ifmo.backend.authentication.security.AuthPasswordVerifier;
import me.ifmo.backend.authentication.security.JwtService;
import me.ifmo.backend.authentication.security.ratelimit.AuthAction;
import me.ifmo.backend.authentication.security.ratelimit.AuthRequestLimiter;
import me.ifmo.backend.authentication.web.request.ActivateAccountRequest;
import me.ifmo.backend.authentication.web.request.LoginRequest;
import me.ifmo.backend.authentication.web.request.PasswordRecoveryRequest;
import me.ifmo.backend.authentication.web.request.PasswordResetRequest;
import me.ifmo.backend.authentication.web.request.RegisterRequest;
import me.ifmo.backend.authentication.web.request.ResendActivationRequest;
import me.ifmo.backend.authentication.web.response.AuthMessageResponse;
import me.ifmo.backend.authentication.web.response.AuthResponse;
import me.ifmo.backend.notification.application.NotificationService;
import me.ifmo.backend.notification.domain.enums.NotificationChannel;
import me.ifmo.backend.notification.domain.enums.NotificationType;
import me.ifmo.backend.notification.web.request.CreateNotificationRequest;
import me.ifmo.backend.shared.exceptions.BusinessRuleException;
import me.ifmo.backend.shared.exceptions.InvalidCredentialsException;
import me.ifmo.backend.shared.exceptions.ResourceNotFoundException;
import me.ifmo.backend.user.domain.Role;
import me.ifmo.backend.user.domain.User;
import me.ifmo.backend.user.domain.UserRole;
import me.ifmo.backend.user.domain.enums.RoleCode;
import me.ifmo.backend.user.domain.enums.UserStatus;
import me.ifmo.backend.user.domain.id.UserRoleId;
import me.ifmo.backend.user.mapper.UserMapper;
import me.ifmo.backend.user.persistence.RoleRepository;
import me.ifmo.backend.user.persistence.UserRepository;
import me.ifmo.backend.user.persistence.UserRoleRepository;
import me.ifmo.backend.user.web.response.UserProfileResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
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
    public static final String AUTH_INSTRUCTIONS_MESSAGE = "If the account is eligible, instructions will be sent.";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuthTokenRepository authTokenRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final NotificationService notificationService;
    private final AuthRequestLimiter requestLimiter;
    private final AuthPasswordVerifier passwordVerifier;
    private final RegistrationStore registrationStore;

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

    private LockedAuthToken findValidTokenForUpdate(String tokenValue, AuthTokenType type) {
        Long userId = authTokenRepository.findUserIdByTokenAndType(tokenValue, type).orElseThrow(
                () -> new BusinessRuleException("Invalid or expired token"));
        User user = userRepository.findByIdForUpdate(userId).orElseThrow(
                () -> new BusinessRuleException("Invalid or expired token"));

        AuthToken token = authTokenRepository.findByTokenAndType(tokenValue, type).orElseThrow(
                () -> new BusinessRuleException("Invalid or expired token"));

        LocalDateTime now = LocalDateTime.now();
        if (token.isUsed() || token.isExpired(now))
            throw new BusinessRuleException("Invalid or expired token");

        return new LockedAuthToken(user, token);
    }

    private record LockedAuthToken(User user, AuthToken token) {}

    private void sendActivationEmail(User user, AuthToken token) {
        notificationService.createAuth(new CreateNotificationRequest(
                user.getId(),
                null,
                null,
                null,
                NotificationType.ACCOUNT_ACTIVATION,
                NotificationChannel.EMAIL,
                "Library account activation",
                "Use this activation code to confirm your account: %s".formatted(token.getToken())
        ), token.getId(), token.getExpiresAt());
    }

    private void sendPasswordRecoveryEmail(User user, AuthToken token) {
        notificationService.createAuth(new CreateNotificationRequest(
                user.getId(),
                null,
                null,
                null,
                NotificationType.PASSWORD_RECOVERY,
                NotificationChannel.EMAIL,
                "Library password recovery",
                "Use this password recovery code to reset your password: %s".formatted(token.getToken())
        ), token.getId(), token.getExpiresAt());
    }

    private void rejectInvalidCredentials(User user) {
        short attempts = (short) (user.getFailedLoginAttempts() + 1);
        user.setFailedLoginAttempts(attempts);

        if (attempts >= MAX_FAILED_ATTEMPTS)
            user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));

        userRepository.save(user);

        throw new InvalidCredentialsException();
    }

    private void validate(User user) {
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now()))
            throw new InvalidCredentialsException();

        if (user.getStatus() != UserStatus.ACTIVE)
            throw new InvalidCredentialsException();
    }

    private void validatePasswordLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new BusinessRuleException("Password must not exceed 72 UTF-8 bytes");
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
        requestLimiter.account(AuthAction.REGISTER, email);
        requestLimiter.registrationPhone(phone);
        validatePasswordLength(request.password());

        User user = userMapper.toEntity(new RegisterRequest(email, phone, request.password(), request.passwordConfirmation(),
                firstName, lastName, middleName));

        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(UserStatus.PENDING_ACTIVATION);
        user.setActivatedAt(null);
        user.setFailedLoginAttempts((short) 0);
        user.setLockedUntil(null);

        User saved = registrationStore.insertIfAbsent(user).orElse(null);
        if (saved == null) return new AuthMessageResponse(AUTH_INSTRUCTIONS_MESSAGE);
        assignDefaultReaderRole(saved);
        AuthToken token = createToken(saved, AuthTokenType.ACCOUNT_ACTIVATION, activationTokenExpirationMinutes);
        sendActivationEmail(saved, token);

        return new AuthMessageResponse(AUTH_INSTRUCTIONS_MESSAGE);
    }

    @Override
    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public AuthResponse login(LoginRequest request) {
        String email = normalize(request.email(), "Email");
        requestLimiter.account(AuthAction.LOGIN, email);
        User user = userRepository.findByEmailForUpdate(email).orElse(null);
        boolean passwordMatches = passwordVerifier.matches(request.password(), user == null ? null : user.getPasswordHash());
        if (user == null) throw new InvalidCredentialsException();
        validate(user);

        if (!passwordMatches)
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
        requestLimiter.account(AuthAction.ACTIVATE, request.token());
        LockedAuthToken locked = findValidTokenForUpdate(request.token(), AuthTokenType.ACCOUNT_ACTIVATION);
        User user = locked.user();
        AuthToken token = locked.token();

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
        requestLimiter.account(AuthAction.RESEND, email);
        User user = userRepository.findByEmailForUpdate(email).orElse(null);
        if (user == null || user.getStatus() != UserStatus.PENDING_ACTIVATION)
            return new AuthMessageResponse(AUTH_INSTRUCTIONS_MESSAGE);

        AuthToken token = createToken(user, AuthTokenType.ACCOUNT_ACTIVATION, activationTokenExpirationMinutes);
        sendActivationEmail(user, token);

        return new AuthMessageResponse(AUTH_INSTRUCTIONS_MESSAGE);
    }

    @Override
    @Transactional
    public AuthMessageResponse requestPasswordRecovery(PasswordRecoveryRequest request) {
        String email = normalize(request.email(), "Email");
        requestLimiter.account(AuthAction.RECOVERY, email);
        User user = userRepository.findByEmailForUpdate(email).orElse(null);
        if (user == null || user.getStatus() == UserStatus.ARCHIVED)
            return new AuthMessageResponse(AUTH_INSTRUCTIONS_MESSAGE);

        AuthToken token = createToken(user, AuthTokenType.PASSWORD_RESET, passwordResetTokenExpirationMinutes);
        sendPasswordRecoveryEmail(user, token);

        return new AuthMessageResponse(AUTH_INSTRUCTIONS_MESSAGE);
    }

    @Override
    @Transactional
    public AuthMessageResponse resetPassword(PasswordResetRequest request) {
        requestLimiter.account(AuthAction.RESET, request.token());
        if (!request.newPassword().equals(request.newPasswordConfirmation()))
            throw new BusinessRuleException("Password confirmation does not match");

        validatePasswordLength(request.newPassword());

        LockedAuthToken locked = findValidTokenForUpdate(request.token(), AuthTokenType.PASSWORD_RESET);
        User user = locked.user();
        AuthToken token = locked.token();

        if (user.getStatus() == UserStatus.ARCHIVED)
            throw new BusinessRuleException("Password reset is not available for archived accounts");

        LocalDateTime now = LocalDateTime.now();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setFailedLoginAttempts((short) 0);
        user.setLockedUntil(null);
        token.setUsedAt(now);

        userRepository.save(user);
        authTokenRepository.save(token);
        userRepository.incrementTokenVersion(user.getId());

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
