package me.ifmo.backend.services.impl;

import me.ifmo.backend.dto.auth.request.ActivateAccountRequest;
import me.ifmo.backend.dto.auth.request.LoginRequest;
import me.ifmo.backend.dto.auth.request.PasswordRecoveryRequest;
import me.ifmo.backend.dto.auth.request.PasswordResetRequest;
import me.ifmo.backend.dto.auth.request.RegisterRequest;
import me.ifmo.backend.dto.auth.request.ResendActivationRequest;
import me.ifmo.backend.dto.auth.response.AuthResponse;
import me.ifmo.backend.dto.notification.request.CreateNotificationRequest;
import me.ifmo.backend.dto.user.response.UserProfileResponse;
import me.ifmo.backend.entities.AuthToken;
import me.ifmo.backend.entities.Role;
import me.ifmo.backend.entities.User;
import me.ifmo.backend.entities.UserRole;
import me.ifmo.backend.entities.enums.AuthTokenType;
import me.ifmo.backend.entities.enums.NotificationType;
import me.ifmo.backend.entities.enums.RoleCode;
import me.ifmo.backend.entities.enums.UserStatus;
import me.ifmo.backend.shared.error.BusinessRuleException;
import me.ifmo.backend.mappers.UserMapper;
import me.ifmo.backend.repositories.AuthTokenRepository;
import me.ifmo.backend.repositories.RoleRepository;
import me.ifmo.backend.repositories.UserRepository;
import me.ifmo.backend.repositories.UserRoleRepository;
import me.ifmo.backend.services.JwtService;
import me.ifmo.backend.services.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final long USER_ID = 1L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private UserRoleRepository userRoleRepository;
    @Mock
    private AuthTokenRepository authTokenRepository;
    @Mock
    private UserMapper userMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private AuthServiceImpl service;

    private User activeUser() {
        return User.builder().id(USER_ID)
                .email("reader@example.com")
                .passwordHash("encoded-password")
                .status(UserStatus.ACTIVE)
                .failedLoginAttempts((short) 0)
                .build();
    }

    @BeforeEach
    void configureTokenExpirations() {
        ReflectionTestUtils.setField(service, "activationTokenExpirationMinutes", 1440L);
        ReflectionTestUtils.setField(service, "passwordResetTokenExpirationMinutes", 60L);
    }

    @Test
    void registerNormalizesDataAssignsReaderRoleAndSendsActivationNotification() {
        RegisterRequest request = new RegisterRequest(
                "  READER@Example.COM  ",
                "  +79991234567  ",
                "password1",
                "password1",
                "  Ivan  ",
                "  Petrov  ",
                "   "
        );

        User user = User.builder().id(USER_ID).build();
        Role readerRole = Role.builder().id(10L).code(RoleCode.READER).build();

        when(userRepository.existsByEmail("reader@example.com")).thenReturn(false);
        when(userRepository.existsByPhone("+79991234567")).thenReturn(false);
        when(userMapper.toEntity(any(RegisterRequest.class))).thenReturn(user);
        when(passwordEncoder.encode("password1")).thenReturn("encoded-password");
        when(userRepository.save(user)).thenReturn(user);
        when(roleRepository.findByCode(RoleCode.READER)).thenReturn(Optional.of(readerRole));
        when(authTokenRepository.findByUser_IdAndTypeAndUsedAtIsNull(USER_ID, AuthTokenType.ACCOUNT_ACTIVATION)).thenReturn(List.of());
        when(authTokenRepository.save(any(AuthToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.register(request);

        assertThat(result.message()).contains("Registration created");
        assertThat(user.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_ACTIVATION);
        assertThat(user.getFailedLoginAttempts()).isZero();

        ArgumentCaptor<RegisterRequest> normalized = ArgumentCaptor.forClass(RegisterRequest.class);
        verify(userMapper).toEntity(normalized.capture());
        assertThat(normalized.getValue().email()).isEqualTo("reader@example.com");
        assertThat(normalized.getValue().phone()).isEqualTo("+79991234567");
        assertThat(normalized.getValue().firstName()).isEqualTo("Ivan");
        assertThat(normalized.getValue().lastName()).isEqualTo("Petrov");
        assertThat(normalized.getValue().middleName()).isNull();

        ArgumentCaptor<UserRole> assignedRole = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleRepository).save(assignedRole.capture());
        assertThat(assignedRole.getValue().getUser()).isSameAs(user);
        assertThat(assignedRole.getValue().getRole()).isSameAs(readerRole);

        ArgumentCaptor<CreateNotificationRequest> notification =
                ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationService).create(notification.capture());
        assertThat(notification.getValue().userId()).isEqualTo(USER_ID);
        assertThat(notification.getValue().type()).isEqualTo(NotificationType.ACCOUNT_ACTIVATION);
        assertThat(notification.getValue().body()).contains("activation code");
    }

    @Test
    void registerRejectsMismatchedPasswordConfirmationBeforeRepositoryAccess() {
        RegisterRequest request = new RegisterRequest(
                "reader@example.com",
                "+79991234567",
                "password1",
                "password2",
                "Ivan",
                "Petrov",
                null
        );

        assertThatThrownBy(() -> service.register(request)).isInstanceOf(BusinessRuleException.class).hasMessage("Password confirmation does not match");

        verify(userRepository, never()).existsByEmail(anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginReturnsJwtAndResetsFailedAttempts() {
        User user = activeUser();
        user.setFailedLoginAttempts((short) 2);
        user.setLockedUntil(LocalDateTime.now().minusMinutes(1));
        UserProfileResponse profile = org.mockito.Mockito.mock(UserProfileResponse.class);

        when(userRepository.findByEmail("reader@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password1", "encoded-password")).thenReturn(true);
        when(userRepository.save(user)).thenReturn(user);
        when(userRoleRepository.findRoleCodesByUser_Id(USER_ID)).thenReturn(List.of(RoleCode.READER));
        when(jwtService.generate(eq(user), anyList())).thenReturn("jwt-token");
        when(jwtService.getAccessTokenExpiresIn()).thenReturn(3600L);
        when(userMapper.toProfileResponse(eq(user), anyCollection())).thenReturn(profile);

        LocalDateTime before = LocalDateTime.now();
        AuthResponse result = service.login(new LoginRequest("  READER@EXAMPLE.COM  ", "password1"));

        assertThat(result.accessToken()).isEqualTo("jwt-token");
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.expiresIn()).isEqualTo(3600L);
        assertThat(result.user()).isSameAs(profile);
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.getLastLoginAt()).isAfterOrEqualTo(before);
    }

    @Test
    void loginLocksUserOnFifthInvalidPasswordAttempt() {
        User user = activeUser();
        user.setFailedLoginAttempts((short) 4);

        when(userRepository.findByEmail("reader@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);
        when(userRepository.save(user)).thenReturn(user);

        LocalDateTime before = LocalDateTime.now();
        assertThatThrownBy(() -> service.login(new LoginRequest("reader@example.com", "wrong-password")))
                .isInstanceOf(BusinessRuleException.class).hasMessage("Invalid email or password");

        assertThat(user.getFailedLoginAttempts()).isEqualTo((short) 5);
        assertThat(user.getLockedUntil()).isAfterOrEqualTo(before.plusMinutes(15));
        verify(jwtService, never()).generate(any(), anyList());
    }

    @Test
    void loginRejectsTemporarilyLockedUserBeforePasswordCheck() {
        User user = activeUser();
        user.setLockedUntil(LocalDateTime.now().plusMinutes(5));
        when(userRepository.findByEmail("reader@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login(new LoginRequest("reader@example.com", "password1")))
                .isInstanceOf(BusinessRuleException.class).hasMessage("User is temporarily locked");

        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    void activateChangesPendingUserAndConsumesToken() {
        User user = User.builder()
                .id(USER_ID)
                .status(UserStatus.PENDING_ACTIVATION)
                .failedLoginAttempts((short) 2)
                .lockedUntil(LocalDateTime.now().plusMinutes(1))
                .build();

        AuthToken token = AuthToken.builder()
                .id(10L)
                .user(user)
                .token("activation-token")
                .type(AuthTokenType.ACCOUNT_ACTIVATION)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();

        when(authTokenRepository.findByTokenAndType(
                "activation-token", AuthTokenType.ACCOUNT_ACTIVATION)).thenReturn(Optional.of(token));

        LocalDateTime before = LocalDateTime.now();
        var result = service.activate(new ActivateAccountRequest("activation-token"));

        assertThat(result.message()).contains("activated");
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getActivatedAt()).isAfterOrEqualTo(before);
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(token.getUsedAt()).isAfterOrEqualTo(before);
        verify(userRepository).save(user);
        verify(authTokenRepository).save(token);
    }

    @Test
    void activateRejectsExpiredToken() {
        AuthToken token = AuthToken.builder()
                .token("expired-token")
                .type(AuthTokenType.ACCOUNT_ACTIVATION)
                .expiresAt(LocalDateTime.now().minusSeconds(1))
                .build();

        when(authTokenRepository.findByTokenAndType(
                "expired-token", AuthTokenType.ACCOUNT_ACTIVATION)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.activate(new ActivateAccountRequest("expired-token")))
                .isInstanceOf(BusinessRuleException.class).hasMessage("Invalid or expired token");

        verify(userRepository, never()).save(any());
        verify(authTokenRepository, never()).save(any());
    }

    @Test
    void resetPasswordEncodesPasswordUnlocksUserAndConsumesToken() {
        User user = activeUser();
        user.setFailedLoginAttempts((short) 5);
        user.setLockedUntil(LocalDateTime.now().plusMinutes(10));

        AuthToken token = AuthToken.builder()
                .id(10L)
                .user(user)
                .token("reset-token")
                .type(AuthTokenType.PASSWORD_RESET)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();

        when(authTokenRepository.findByTokenAndType("reset-token", AuthTokenType.PASSWORD_RESET)).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("new-password1")).thenReturn("new-encoded-password");

        var result = service.resetPassword(new PasswordResetRequest("reset-token", "new-password1", "new-password1"));

        assertThat(result.message()).contains("changed");
        assertThat(user.getPasswordHash()).isEqualTo("new-encoded-password");
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(token.getUsedAt()).isNotNull();
        verify(userRepository).save(user);
        verify(authTokenRepository).save(token);
    }

    @Test
    void resendActivationInvalidatesPreviousTokenAndSendsNewNotification() {
        User user = User.builder()
                .id(USER_ID)
                .email("reader@example.com")
                .status(UserStatus.PENDING_ACTIVATION)
                .build();

        AuthToken previousToken = AuthToken.builder()
                .user(user)
                .token("old-token")
                .type(AuthTokenType.ACCOUNT_ACTIVATION)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();

        when(userRepository.findByEmail("reader@example.com")).thenReturn(Optional.of(user));
        when(authTokenRepository.findByUser_IdAndTypeAndUsedAtIsNull(USER_ID, AuthTokenType.ACCOUNT_ACTIVATION)).thenReturn(List.of(previousToken));
        when(authTokenRepository.save(any(AuthToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LocalDateTime before = LocalDateTime.now();
        var result = service.resendActivation(new ResendActivationRequest(" READER@EXAMPLE.COM "));

        assertThat(result.message()).contains("sent");
        assertThat(previousToken.getUsedAt()).isAfterOrEqualTo(before);
        ArgumentCaptor<CreateNotificationRequest> notification = ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationService).create(notification.capture());
        assertThat(notification.getValue().type()).isEqualTo(NotificationType.ACCOUNT_ACTIVATION);
    }

    @Test
    void requestPasswordRecoveryCreatesTokenAndSendsNotification() {
        User user = activeUser();
        when(userRepository.findByEmail("reader@example.com")).thenReturn(Optional.of(user));
        when(authTokenRepository.findByUser_IdAndTypeAndUsedAtIsNull(USER_ID, AuthTokenType.PASSWORD_RESET)).thenReturn(List.of());
        when(authTokenRepository.save(any(AuthToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.requestPasswordRecovery(new PasswordRecoveryRequest("reader@example.com"));

        assertThat(result.message()).contains("sent");
        ArgumentCaptor<CreateNotificationRequest> notification = ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationService).create(notification.capture());
        assertThat(notification.getValue().type()).isEqualTo(NotificationType.PASSWORD_RECOVERY);
    }

    @Test
    void resetPasswordRejectsMismatchedConfirmationBeforeTokenLookup() {
        assertThatThrownBy(() -> service.resetPassword(new PasswordResetRequest("reset-token", "new-password1", "different-password1")))
                .isInstanceOf(BusinessRuleException.class).hasMessage("Password confirmation does not match");

        verify(authTokenRepository, never()).findByTokenAndType(anyString(), any());
    }

    @Test
    void meMapsUserAndAssignedRoles() {
        User user = activeUser();
        UserProfileResponse profile = org.mockito.Mockito.mock(UserProfileResponse.class);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRoleRepository.findRoleCodesByUser_Id(USER_ID)).thenReturn(List.of(RoleCode.READER, RoleCode.LIBRARIAN));
        when(userMapper.toProfileResponse(eq(user), anyCollection())).thenReturn(profile);

        assertThat(service.me(USER_ID)).isSameAs(profile);
    }
}
