package me.ifmo.backend.authentication.application.impl;

import me.ifmo.backend.authentication.application.AuthService;
import me.ifmo.backend.authentication.security.JwtService;
import me.ifmo.backend.authentication.security.JwtServiceImpl;
import me.ifmo.backend.authentication.web.request.LoginRequest;
import me.ifmo.backend.authentication.web.request.PasswordResetRequest;
import me.ifmo.backend.authentication.web.response.AuthResponse;
import me.ifmo.backend.notification.application.NotificationService;
import me.ifmo.backend.shared.exceptions.BusinessRuleException;
import me.ifmo.backend.shared.exceptions.InvalidCredentialsException;
import me.ifmo.backend.user.mapper.RoleMapperImpl;
import me.ifmo.backend.user.mapper.UserMapperImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

@DataJpaTest(showSql = false, properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({AuthServiceImpl.class, JwtServiceImpl.class, UserMapperImpl.class, RoleMapperImpl.class,
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
                .isInstanceOf(BusinessRuleException.class).hasMessage("User is temporarily locked");
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
                .hasMessage("User is temporarily locked");
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
                .hasMessage("User account is not active");
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

    private String resetCode(Account account) {
        String code = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO auth_tokens (user_id, token, type, created_at, expires_at)
                VALUES (?, ?, 'PASSWORD_RESET', CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP + INTERVAL '1 day')
                """, account.id(), code);
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
