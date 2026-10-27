package me.ifmo.backend.authentication.application.impl;

import jakarta.persistence.EntityManager;
import me.ifmo.backend.authentication.application.AuthService;
import me.ifmo.backend.authentication.protection.AuthProtectionConfiguration;
import me.ifmo.backend.authentication.protection.AuthRequestLimiter;
import me.ifmo.backend.authentication.protection.AuthRateLimitStore;
import me.ifmo.backend.authentication.protection.AuthPasswordVerifier;
import me.ifmo.backend.authentication.protection.RegistrationStore;
import me.ifmo.backend.authentication.domain.enums.AuthTokenType;
import me.ifmo.backend.authentication.security.JwtService;
import me.ifmo.backend.authentication.security.JwtServiceImpl;
import me.ifmo.backend.authentication.web.request.ActivateAccountRequest;
import me.ifmo.backend.authentication.web.request.LoginRequest;
import me.ifmo.backend.authentication.web.request.PasswordRecoveryRequest;
import me.ifmo.backend.authentication.web.request.PasswordResetRequest;
import me.ifmo.backend.authentication.web.request.ResendActivationRequest;
import me.ifmo.backend.authentication.web.response.AuthResponse;
import me.ifmo.backend.notification.application.NotificationService;
import me.ifmo.backend.notification.web.request.CreateNotificationRequest;
import me.ifmo.backend.shared.exceptions.BusinessRuleException;
import me.ifmo.backend.shared.exceptions.InvalidCredentialsException;
import me.ifmo.backend.user.mapper.RoleMapperImpl;
import me.ifmo.backend.user.mapper.UserMapperImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

@DataJpaTest(showSql = false, properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({AuthServiceImpl.class, JwtServiceImpl.class, UserMapperImpl.class, RoleMapperImpl.class,
        AuthProtectionConfiguration.class, AuthRequestLimiter.class, AuthRateLimitStore.class,
        AuthPasswordVerifier.class, RegistrationStore.class,
        AuthConcurrencyIntegrationTest.PasswordConfiguration.class})
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuthConcurrencyIntegrationTest {

    private static final String OLD_PASSWORD = "old-password1";
    private static final String NEW_PASSWORD = "new-password2";
    private static final String WRONG_PASSWORD = "wrong-password3";
    private static final AtomicLong SEQUENCE = new AtomicLong();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("security.jwt.secret", () -> Base64.getEncoder().encodeToString(
                "test-only-auth-concurrency-signing-key-64-bytes-long-for-HS512-0000"
                        .getBytes(StandardCharsets.UTF_8)));
        registry.add("security.jwt.access-token-expiration-ms", () -> "60000");
    }

    @Autowired private AuthService auth;
    @Autowired private JwtService jwt;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EntityManager entityManager;
    // Credential hashing, JWTs, repositories and transactions are real; no messages are sent.
    @MockitoBean private NotificationService notifications;
    @MockitoSpyBean private PasswordEncoder encoder;

    @Test
    void invalidAttemptsCommitAndTheFifthFailureBlocksEvenTheCorrectPassword() {
        Account account = account(0);
        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThatThrownBy(() -> login(account, WRONG_PASSWORD))
                    .isInstanceOf(InvalidCredentialsException.class);
            assertThat(attempts(account)).isEqualTo(attempt);
        }

        assertThat(jdbc.queryForObject("SELECT locked_until IS NOT NULL FROM users WHERE id = ?",
                Boolean.class, account.id())).isTrue();
        assertThatThrownBy(() -> login(account, OLD_PASSWORD))
                .isInstanceOf(BusinessRuleException.class).hasMessage("Invalid email or password");
        assertThat(attempts(account)).isEqualTo(5);
        assertThat(lastLogin(account)).isNull();
    }

    @Test
    void overlappingInvalidLoginsBothCount() throws Exception {
        Account account = account(0);
        Pause pause = pauseFirstPasswordCheck(WRONG_PASSWORD);

        Outcomes outcomes = overlap(() -> login(account, WRONG_PASSWORD),
                () -> login(account, WRONG_PASSWORD), pause, true);

        assertInvalid(outcomes.first());
        assertInvalid(outcomes.second());
        assertThat(attempts(account)).isEqualTo(2);
        assertThat(lastLogin(account)).isNull();
    }

    @Test
    void fifthInvalidLoginBlocksACorrectLoginAlreadyWaitingForTheAccount() throws Exception {
        Account account = account(4);
        Pause pause = pauseFirstPasswordCheck(WRONG_PASSWORD);

        Outcomes outcomes = overlap(() -> login(account, WRONG_PASSWORD),
                () -> login(account, OLD_PASSWORD), pause, true);

        assertInvalid(outcomes.first());
        assertThat(outcomes.second().error()).isInstanceOf(BusinessRuleException.class)
                .hasMessage("Invalid email or password");
        assertThat(outcomes.second().result()).isNull();
        assertThat(attempts(account)).isEqualTo(5);
        assertThat(lastLogin(account)).isNull();
    }

    @Test
    void passwordResetWaitsForFailedLoginAndPreservesTheNewPassword() throws Exception {
        Account account = account(0);
        String oldJwt = login(account, OLD_PASSWORD).accessToken();
        String resetCode = resetCode(account);
        Pause pause = pauseFirstPasswordCheck(WRONG_PASSWORD);

        Outcomes outcomes = overlap(() -> login(account, WRONG_PASSWORD),
                () -> reset(resetCode), pause, true);

        assertInvalid(outcomes.first());
        assertThat(outcomes.second().error()).isNull();
        assertThat(outcomes.second().result()).isNotNull();
        assertThat(attempts(account)).isZero();
        assertNewPasswordAndRevokedOldJwt(account, oldJwt);
    }

    @Test
    void loginWaitingForResetChecksTheNewPasswordAndDoesNotRestoreTheOldOne() throws Exception {
        Account account = account(0);
        String oldJwt = login(account, OLD_PASSWORD).accessToken();
        String resetCode = resetCode(account);
        Pause pause = new Pause();
        doAnswer(invocation -> {
            pause.hold();
            return invocation.callRealMethod();
        }).when(encoder).encode(NEW_PASSWORD);

        Outcomes outcomes = overlap(() -> reset(resetCode),
                () -> login(account, OLD_PASSWORD), pause, true);

        assertThat(outcomes.first().error()).isNull();
        assertInvalid(outcomes.second());
        assertThat(attempts(account)).isEqualTo(1);
        assertNewPasswordAndRevokedOldJwt(account, oldJwt);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void successfulAndInvalidLoginApplyInTheirTransactionOrder(boolean successfulFirst) throws Exception {
        Account account = account(2);
        String firstPassword = successfulFirst ? OLD_PASSWORD : WRONG_PASSWORD;
        String secondPassword = successfulFirst ? WRONG_PASSWORD : OLD_PASSWORD;
        Pause pause = pauseFirstPasswordCheck(firstPassword);

        Outcomes outcomes = overlap(() -> login(account, firstPassword),
                () -> login(account, secondPassword), pause, true);

        Outcome success = successfulFirst ? outcomes.first() : outcomes.second();
        assertThat(success.error()).isNull();
        assertThat(jwt.isTokenValid(((AuthResponse) success.result()).accessToken())).isTrue();
        assertInvalid(successfulFirst ? outcomes.second() : outcomes.first());
        assertThat(attempts(account)).isEqualTo(successfulFirst ? 1 : 0);
        assertThat(lastLogin(account)).isNotNull();
    }

    @Test
    void loginSeesAnAccountBlockCommittedWhileItWaitsForTheUserRow() throws Exception {
        Account account = account(0);
        Pause pause = new Pause();

        Outcomes outcomes = overlap(() -> {
            jdbc.update("UPDATE users SET status = 'BLOCKED' WHERE id = ?", account.id());
            pause.hold();
            return null;
        }, () -> login(account, OLD_PASSWORD), pause, true);

        assertThat(outcomes.first().error()).isNull();
        assertThat(outcomes.second().error()).isInstanceOf(BusinessRuleException.class)
                .hasMessage("Invalid email or password");
        assertThat(jdbc.queryForObject("SELECT status::text FROM users WHERE id = ?", String.class,
                account.id())).isEqualTo("BLOCKED");
        assertThat(attempts(account)).isZero();
        assertThat(lastLogin(account)).isNull();
    }

    @Test
    void differentAccountsCanLogInIndependently() throws Exception {
        Account first = account(0);
        Account second = account(0);
        Pause pause = pauseFirstPasswordCheck(WRONG_PASSWORD);

        Outcomes outcomes = overlap(() -> login(first, WRONG_PASSWORD),
                () -> login(second, OLD_PASSWORD), pause, false);

        assertInvalid(outcomes.first());
        assertThat(outcomes.second().error()).isNull();
        assertThat(jwt.isTokenValid(((AuthResponse) outcomes.second().result()).accessToken())).isTrue();
        assertThat(attempts(first)).isEqualTo(1);
        assertThat(attempts(second)).isZero();
    }

    @Test
    void samePasswordResetCodeCannotBeConsumedByTwoOverlappingRequests() throws Exception {
        Account account = account(0);
        String oldJwt = login(account, OLD_PASSWORD).accessToken();
        String code = resetCode(account);
        Pause pause = pausePasswordEncoding();

        Outcomes outcomes = overlap(() -> reset(code),
                () -> auth.resetPassword(new PasswordResetRequest(code, WRONG_PASSWORD, WRONG_PASSWORD)),
                pause, true);

        assertThat(outcomes.first().error()).isNull();
        assertInvalidToken(outcomes.second());
        assertThat(codeUsed(code)).isTrue();
        assertNewPasswordAndRevokedOldJwt(account, oldJwt);
    }

    @ParameterizedTest
    @EnumSource(AuthTokenType.class)
    void parallelReissuesLeaveOnlyTheLatestCodeUnused(AuthTokenType type) throws Exception {
        Account account = account(type);
        Pause pause = pauseFirstNotification();

        Outcomes outcomes = overlap(() -> issueCode(account, type),
                () -> issueCode(account, type), pause, true);

        assertThat(outcomes.first().error()).isNull();
        assertThat(outcomes.second().error()).isNull();
        assertThat(unusedCodeCount(account, type)).isEqualTo(1);
        assertThat(codeUsed((String) outcomes.first().result())).isTrue();
        assertThat(codeUsed((String) outcomes.second().result())).isFalse();
    }

    @Test
    void sameActivationCodeCannotBeConsumedByTwoOverlappingRequests() throws Exception {
        Account account = account(AuthTokenType.ACCOUNT_ACTIVATION);
        String code = code(account, AuthTokenType.ACCOUNT_ACTIVATION);
        Pause pause = new Pause();

        Outcomes outcomes = overlap(() -> {
            Object result = consumeCode(code, AuthTokenType.ACCOUNT_ACTIVATION);
            entityManager.flush();
            pause.hold();
            return result;
        }, () -> consumeCode(code, AuthTokenType.ACCOUNT_ACTIVATION), pause, true);

        assertThat(outcomes.first().error()).isNull();
        assertInvalidToken(outcomes.second());
        assertThat(codeUsed(code)).isTrue();
        assertThat(jdbc.queryForObject("SELECT status::text FROM users WHERE id = ?", String.class, account.id()))
                .isEqualTo("ACTIVE");
        assertTokenRejected(code, AuthTokenType.ACCOUNT_ACTIVATION);
    }

    @ParameterizedTest
    @EnumSource(AuthTokenType.class)
    void reissueInvalidatesAnOldCodeWhileItsConsumerWaits(AuthTokenType type) throws Exception {
        Account account = account(type);
        String oldCode = code(account, type);
        Pause pause = pauseFirstNotification();

        Outcomes outcomes = overlap(() -> issueCode(account, type),
                () -> consumeCode(oldCode, type), pause, true);

        assertThat(outcomes.first().error()).isNull();
        assertInvalidToken(outcomes.second());
        assertThat(codeUsed(oldCode)).isTrue();
        assertThat(unusedCodeCount(account, type)).isEqualTo(1);
        String newCode = (String) outcomes.first().result();
        assertThat(consumeCode(newCode, type)).isNotNull();
        assertTokenRejected(newCode, type);
    }

    @ParameterizedTest
    @EnumSource(AuthTokenType.class)
    void reissueSeesTheAccountAfterAWaitingConsumptionCommits(AuthTokenType type) throws Exception {
        Account account = account(type);
        String oldCode = code(account, type);
        Pause pause = new Pause();

        Outcomes outcomes = overlap(() -> {
            Object result = consumeCode(oldCode, type);
            entityManager.flush();
            pause.hold();
            return result;
        }, () -> type == AuthTokenType.ACCOUNT_ACTIVATION
                ? auth.resendActivation(new ResendActivationRequest(account.email())) : issueCode(account, type), pause, true);

        assertThat(outcomes.first().error()).isNull();
        assertThat(codeUsed(oldCode)).isTrue();
        if (type == AuthTokenType.ACCOUNT_ACTIVATION) {
            assertThat(outcomes.second().error()).isNull();
            assertThat(outcomes.second().result()).isEqualTo(
                    new me.ifmo.backend.authentication.web.response.AuthMessageResponse(AuthServiceImpl.AUTH_INSTRUCTIONS_MESSAGE));
            assertThat(unusedCodeCount(account, type)).isZero();
        } else {
            assertThat(outcomes.second().error()).isNull();
            assertThat(unusedCodeCount(account, type)).isEqualTo(1);
            assertThat(codeUsed((String) outcomes.second().result())).isFalse();
        }
        assertTokenRejected(oldCode, type);
    }

    @ParameterizedTest
    @EnumSource(AuthTokenType.class)
    void waitingConsumerReadsTheLatestTokenExpiration(AuthTokenType type) throws Exception {
        Account account = account(type);
        String code = code(account, type);
        Pause pause = new Pause();

        Outcomes outcomes = overlap(() -> {
            jdbc.queryForObject("SELECT id FROM users WHERE id = ? FOR UPDATE", Long.class, account.id());
            jdbc.update("UPDATE auth_tokens SET expires_at = CURRENT_TIMESTAMP - INTERVAL '12 hours' WHERE token = ?", code);
            pause.hold();
            return null;
        }, () -> consumeCode(code, type), pause, true);

        assertThat(outcomes.first().error()).isNull();
        assertInvalidToken(outcomes.second());
        assertThat(codeUsed(code)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(AuthTokenType.class)
    void consumptionRollsBackBothTheAccountChangeAndTheUsedCode(AuthTokenType type) {
        Account account = account(type);
        String code = code(account, type);

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            consumeCode(code, type);
            entityManager.flush();
            assertThat(codeUsed(code)).isTrue();
            throw new IllegalStateException("Failure after credential changes were flushed");
        })).isInstanceOf(IllegalStateException.class).hasMessage("Failure after credential changes were flushed");

        assertThat(codeUsed(code)).isFalse();
        assertThat(jdbc.queryForObject("SELECT token_version FROM users WHERE id = ?", Long.class, account.id())).isZero();
        assertThat(encoder.matches(OLD_PASSWORD,
                jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, account.id()))).isTrue();
        if (type == AuthTokenType.ACCOUNT_ACTIVATION)
            assertThat(jdbc.queryForObject("SELECT status::text FROM users WHERE id = ?", String.class, account.id()))
                    .isEqualTo("PENDING_ACTIVATION");
        assertThat(consumeCode(code, type)).isNotNull();
        assertTokenRejected(code, type);
    }

    @ParameterizedTest
    @EnumSource(AuthTokenType.class)
    void failedNotificationCreationRollsBackReissueAndKeepsThePreviousCode(AuthTokenType type) {
        Account account = account(type);
        String oldCode = code(account, type);
        doThrow(new IllegalStateException("Notification creation failed"))
                .when(notifications).createAuth(any(CreateNotificationRequest.class), any(), any());

        assertThatThrownBy(() -> issueCode(account, type)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Notification creation failed");

        assertThat(codeUsed(oldCode)).isFalse();
        assertThat(unusedCodeCount(account, type)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_tokens WHERE user_id = ?", Integer.class, account.id()))
                .isEqualTo(1);
        doReturn(null).when(notifications).createAuth(any(CreateNotificationRequest.class), any(), any());
        assertThat(issueCode(account, type)).isNotEqualTo(oldCode);
        assertThat(codeUsed(oldCode)).isTrue();
        assertThat(unusedCodeCount(account, type)).isEqualTo(1);
    }

    @Test
    void reissueOnlyRevokesCodesOfTheSameType() {
        Account account = account(AuthTokenType.ACCOUNT_ACTIVATION);
        String activation = code(account, AuthTokenType.ACCOUNT_ACTIVATION);
        String recovery = code(account, AuthTokenType.PASSWORD_RESET);

        String newRecovery = issueCode(account, AuthTokenType.PASSWORD_RESET);
        assertThat(codeUsed(activation)).isFalse();
        assertThat(codeUsed(recovery)).isTrue();

        String newActivation = issueCode(account, AuthTokenType.ACCOUNT_ACTIVATION);
        assertThat(codeUsed(activation)).isTrue();
        assertThat(codeUsed(newRecovery)).isFalse();
        assertThat(codeUsed(newActivation)).isFalse();
        assertThat(unusedCodeCount(account, AuthTokenType.ACCOUNT_ACTIVATION)).isEqualTo(1);
        assertThat(unusedCodeCount(account, AuthTokenType.PASSWORD_RESET)).isEqualTo(1);
    }

    private Pause pausePasswordEncoding() {
        Pause pause = new Pause();
        doAnswer(invocation -> {
            pause.hold();
            return invocation.callRealMethod();
        }).doCallRealMethod().when(encoder).encode(NEW_PASSWORD);
        return pause;
    }

    private Pause pauseFirstNotification() {
        Pause pause = new Pause();
        doAnswer(invocation -> {
            pause.hold();
            return null;
        }).doReturn(null).when(notifications).createAuth(any(CreateNotificationRequest.class), any(), any());
        return pause;
    }

    private String issueCode(Account account, AuthTokenType type) {
        if (type == AuthTokenType.ACCOUNT_ACTIVATION)
            auth.resendActivation(new ResendActivationRequest(account.email()));
        else
            auth.requestPasswordRecovery(new PasswordRecoveryRequest(account.email()));
        return jdbc.queryForObject("""
                SELECT token FROM auth_tokens WHERE user_id = ? AND type::text = ? AND used_at IS NULL
                ORDER BY id DESC LIMIT 1
                """, String.class, account.id(), type.name());
    }

    private Object consumeCode(String code, AuthTokenType type) {
        if (type == AuthTokenType.ACCOUNT_ACTIVATION)
            return auth.activate(new ActivateAccountRequest(code));
        return reset(code);
    }

    private void assertTokenRejected(String code, AuthTokenType type) {
        assertThatThrownBy(() -> consumeCode(code, type)).isInstanceOf(BusinessRuleException.class)
                .hasMessage("Invalid or expired token");
    }

    private int unusedCodeCount(Account account, AuthTokenType type) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM auth_tokens WHERE user_id = ? AND type::text = ? AND used_at IS NULL
                """, Integer.class, account.id(), type.name());
    }

    private boolean codeUsed(String code) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT used_at IS NOT NULL FROM auth_tokens WHERE token = ?",
                Boolean.class, code));
    }

    private void assertInvalidToken(Outcome outcome) {
        assertThat(outcome.error()).isInstanceOf(BusinessRuleException.class).hasMessage("Invalid or expired token");
        assertThat(outcome.result()).isNull();
    }

    private Pause pauseFirstPasswordCheck(String password) {
        Pause pause = new Pause();
        doAnswer(invocation -> {
            pause.hold();
            return invocation.callRealMethod();
        }).doCallRealMethod().when(encoder).matches(eq(password), anyString());
        return pause;
    }

    private Outcomes overlap(Supplier<?> firstRequest, Supplier<?> secondRequest,
                             Pause pause, boolean expectBlocking) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        CompletableFuture<Integer> firstPid = new CompletableFuture<>();
        CompletableFuture<Integer> secondPid = new CompletableFuture<>();
        try {
            Future<Outcome> first = executor.submit(() -> invoke(firstRequest, firstPid));
            assertThat(pause.entered.await(15, TimeUnit.SECONDS)).as("First request reached the protected operation").isTrue();
            Future<Outcome> second = executor.submit(() -> invoke(secondRequest, secondPid));
            if (expectBlocking)
                awaitBlocked(secondPid.get(15, TimeUnit.SECONDS), firstPid.get(15, TimeUnit.SECONDS), second);
            else
                assertThat(second.get(10, TimeUnit.SECONDS).error()).isNull();
            pause.release.countDown();
            return new Outcomes(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        } finally {
            pause.release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private Outcome invoke(Supplier<?> request, CompletableFuture<Integer> pid) {
        try {
            return new TransactionTemplate(transactionManager).execute(status -> {
                pid.complete(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                try {
                    return new Outcome(request.get(), null);
                } catch (InvalidCredentialsException exception) {
                    // The service interceptor must allow this transaction to commit its failed-attempt update.
                    return new Outcome(null, exception);
                }
            });
        } catch (RuntimeException exception) {
            pid.completeExceptionally(exception);
            return new Outcome(null, exception);
        }
    }

    private void awaitBlocked(int waitingPid, int holdingPid, Future<?> waitingRequest) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        boolean blocked = false;
        while (!waitingRequest.isDone() && System.nanoTime() < deadline) {
            blocked = Boolean.TRUE.equals(jdbc.queryForObject("SELECT ? = ANY(pg_blocking_pids(?))",
                    Boolean.class, holdingPid, waitingPid));
            if (blocked)
                break;
            Thread.sleep(20);
        }
        assertThat(blocked).as("Second transaction waits for the account row lock in PostgreSQL").isTrue();
    }

    private void assertNewPasswordAndRevokedOldJwt(Account account, String oldJwt) {
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, account.id());
        assertThat(encoder.matches(NEW_PASSWORD, hash)).isTrue();
        assertThat(encoder.matches(OLD_PASSWORD, hash)).isFalse();
        assertThat(jdbc.queryForObject("SELECT token_version FROM users WHERE id = ?", Long.class, account.id()))
                .isEqualTo(1L);
        assertThat(jwt.isTokenValid(oldJwt)).isFalse();
        assertThat(jwt.isTokenValid(login(account, NEW_PASSWORD).accessToken())).isTrue();
        assertThatThrownBy(() -> login(account, OLD_PASSWORD)).isInstanceOf(InvalidCredentialsException.class);
    }

    private AuthResponse login(Account account, String password) {
        return auth.login(new LoginRequest(account.email(), password));
    }

    private Object reset(String code) {
        return auth.resetPassword(new PasswordResetRequest(code, NEW_PASSWORD, NEW_PASSWORD));
    }

    private void assertInvalid(Outcome outcome) {
        assertThat(outcome.error()).isInstanceOf(InvalidCredentialsException.class);
        assertThat(outcome.result()).isNull();
    }

    private int attempts(Account account) {
        return jdbc.queryForObject("SELECT failed_login_attempts FROM users WHERE id = ?", Integer.class, account.id());
    }

    private LocalDateTime lastLogin(Account account) {
        return jdbc.queryForObject("SELECT last_login_at FROM users WHERE id = ?", LocalDateTime.class, account.id());
    }

    private Account account(int failedAttempts) {
        long sequence = SEQUENCE.incrementAndGet();
        String email = "auth-concurrency-" + sequence + "@example.com";
        Long id = jdbc.queryForObject("""
                INSERT INTO users (email, phone, password_hash, first_name, last_name, status, failed_login_attempts)
                VALUES (?, ?, ?, 'Auth', 'Reader', 'ACTIVE', ?) RETURNING id
                """, Long.class, email, "+7902%07d".formatted(sequence), encoder.encode(OLD_PASSWORD), failedAttempts);
        jdbc.update("INSERT INTO user_roles (user_id, role_id) SELECT ?, id FROM roles WHERE code = 'READER'", id);
        return new Account(id, email);
    }

    private Account account(AuthTokenType type) {
        Account account = account(0);
        if (type == AuthTokenType.ACCOUNT_ACTIVATION)
            jdbc.update("UPDATE users SET status = 'PENDING_ACTIVATION' WHERE id = ?", account.id());
        return account;
    }

    private String resetCode(Account account) {
        return code(account, AuthTokenType.PASSWORD_RESET);
    }

    private String code(Account account, AuthTokenType type) {
        String code = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO auth_tokens (user_id, token, type, created_at, expires_at)
                VALUES (?, ?, CAST(? AS auth_token_type), CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP + INTERVAL '1 day')
                """, account.id(), code, type.name());
        return code;
    }

    private record Account(Long id, String email) {}
    private record Outcome(Object result, RuntimeException error) {}
    private record Outcomes(Outcome first, Outcome second) {}

    private static class Pause {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        private void hold() {
            entered.countDown();
            try {
                if (!release.await(20, TimeUnit.SECONDS))
                    throw new IllegalStateException("Timed out waiting to release the first auth request");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PasswordConfiguration {
        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder(4);
        }
    }
}
