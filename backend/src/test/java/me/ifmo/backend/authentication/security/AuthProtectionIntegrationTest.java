package me.ifmo.backend.authentication.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import me.ifmo.backend.audit.application.AuditLogService;
import me.ifmo.backend.authentication.application.AuthService;
import me.ifmo.backend.authentication.application.impl.AuthServiceImpl;
import me.ifmo.backend.authentication.application.retention.AuthDataRetention;
import me.ifmo.backend.authentication.configuration.AuthProtectionConfiguration;
import me.ifmo.backend.authentication.configuration.AuthProtectionProperties;
import me.ifmo.backend.authentication.exception.AuthRateLimitExceededException;
import me.ifmo.backend.authentication.persistence.AuthRateLimitStore;
import me.ifmo.backend.authentication.persistence.RegistrationStore;
import me.ifmo.backend.authentication.security.ratelimit.AuthAction;
import me.ifmo.backend.authentication.security.ratelimit.AuthRequestLimiter;
import me.ifmo.backend.authentication.web.AuthController;
import me.ifmo.backend.authentication.web.request.*;
import me.ifmo.backend.notification.application.NotificationService;
import me.ifmo.backend.notification.application.impl.*;
import me.ifmo.backend.notification.domain.Notification;
import me.ifmo.backend.notification.mapper.NotificationMapperImpl;
import me.ifmo.backend.notification.mapper.NotificationPreferenceMapperImpl;
import me.ifmo.backend.notification.persistence.NotificationRepository;
import me.ifmo.backend.shared.web.GlobalExceptionHandler;
import me.ifmo.backend.user.mapper.RoleMapperImpl;
import me.ifmo.backend.user.mapper.UserMapperImpl;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = AuthProtectionIntegrationTest.TestApplication.class, properties = {
        "security.auth.protection.cleanup-enabled=false", "security.auth.protection.minimum-response-time=20ms",
        "security.auth.protection.login.ip=3", "security.auth.protection.login.account=3",
        "security.auth.protection.register.ip=3", "security.auth.protection.register.account=3",
        "security.auth.protection.recovery.ip=3", "security.auth.protection.recovery.account=3",
        "security.auth.protection.resend.ip=3", "security.auth.protection.resend.account=3",
        "security.auth.protection.activate.ip=3", "security.auth.protection.activate.account=3",
        "security.auth.protection.reset.ip=3", "security.auth.protection.reset.account=3",
        "security.auth.protection.trusted-proxies=10.20.0.0/24", "spring.cache.type=none"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class AuthProtectionIntegrationTest {
    private static final String KEY = "test-only-shared-quota-key-with-at-least-32-bytes";
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final String PASSWORD = "password123";
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan("me.ifmo.backend")
    @EnableJpaRepositories("me.ifmo.backend")
    @Import({AuthServiceImpl.class, AuthController.class, SecurityConfig.class, JwtAuthenticationFilter.class,
            CustomUserDetailsService.class, RestAuthenticationEntryPoint.class, JwtServiceImpl.class,
            AuthProtectionConfiguration.class, AuthRequestLimiter.class, AuthRateLimitStore.class,
            AuthPasswordVerifier.class, RegistrationStore.class, AuthClientAddress.class, AuthDataRetention.class,
            UserMapperImpl.class, RoleMapperImpl.class, NotificationServiceImpl.class,
            NotificationMapperImpl.class, NotificationPreferenceServiceImpl.class,
            NotificationPreferenceMapperImpl.class, NotificationChannelPolicy.class, GlobalExceptionHandler.class})
    static class TestApplication {}

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        properties.add("security.auth.protection.key", () -> KEY);
        properties.add("security.jwt.secret", () -> Base64.getEncoder().encodeToString(
                "test-only-auth-protection-signing-key-64-bytes-long-for-HS512-0000".getBytes(StandardCharsets.UTF_8)));
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthService auth;
    @Autowired AuthRequestLimiter limiter;
    @MockitoSpyBean AuthRateLimitStore store;
    @Autowired AuthProtectionProperties properties;
    @Autowired AuthDataRetention retention;
    @Autowired NotificationRepository notifications;
    @Autowired NotificationService notificationService;
    @Autowired PasswordEncoder encoder;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean AuditLogService audit;

    @BeforeEach
    void resetQuotasAndPendingMessages() {
        jdbc.update("DELETE FROM auth_rate_limits");
        jdbc.update("DELETE FROM notifications");
        jdbc.update("DELETE FROM auth_tokens");
    }

    @ParameterizedTest
    @ValueSource(strings = {"login", "register", "password-recovery", "resend-activation", "activate", "password-reset"})
    void malformedRequestsStillConsumeIpQuotaBeforeJsonParsing(String route) throws Exception {
        for (int i = 0; i < 3; i++) mvc.perform(command(route, "{"))
                .andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(command(route, "{"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("AUTH_RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void forgedForwardedHeadersDoNotRotateAnUntrustedPeer() throws Exception {
        for (int i = 0; i < 3; i++) mvc.perform(command("login", "{")
                .header("X-Real-IP", "203.0.113." + (10 + i)).header("X-Forwarded-For", "198.51.100." + i))
                .andExpect(status().isBadRequest());
        mvc.perform(command("login", "{").header("X-Real-IP", "203.0.113.50"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void explicitlyTrustedProxyUsesCanonicalRealIp() throws Exception {
        for (int i = 0; i < 3; i++) mvc.perform(command("login", "{").with(request -> {
            request.setRemoteAddr("10.20.0.10"); return request;
        }).header("X-Real-IP", "203.000.113.001")).andExpect(status().isBadRequest());
        mvc.perform(command("login", "{").with(request -> {
            request.setRemoteAddr("10.20.0.11"); return request;
        }).header("X-Real-IP", "203.0.113.1")).andExpect(status().isTooManyRequests());
        mvc.perform(command("login", "{").with(request -> {
            request.setRemoteAddr("10.20.0.10"); return request;
        }).header("X-Real-IP", "203.0.113.2")).andExpect(status().isBadRequest());
    }

    @Test
    void accountQuotaSurvivesLoginFailureAndIpRotation() throws Exception {
        String email = "missing@example.com";
        for (int i = 0; i < 3; i++) mvc.perform(command("login", json.writeValueAsString(
                Map.of("email", i == 0 ? email.toUpperCase() : email, "password", PASSWORD))).with(request -> {
            request.setRemoteAddr("203.0.113." + SEQUENCE.incrementAndGet() % 200); return request;
        })).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.message").value("Invalid email or password"));
        mvc.perform(command("login", json.writeValueAsString(Map.of("email", email, "password", PASSWORD)))
                .with(request -> { request.setRemoteAddr("198.51.100.250"); return request; }))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
        assertThat(jdbc.queryForList("SELECT bucket_key FROM auth_rate_limits", String.class))
                .allSatisfy(key -> assertThat(key).matches("[a-f0-9]{64}"));
    }

    @Test
    void rejectedRequestsDoNotExtendWindowAndExpiredWindowStartsAgain() {
        String key = "a".repeat(64);
        assertThat(store.consume(key, 1, Duration.ofMinutes(1)).allowed()).isTrue();
        Object expiry = jdbc.queryForObject("SELECT expires_at FROM auth_rate_limits WHERE bucket_key = ?", Object.class, key);
        for (int i = 0; i < 4; i++) assertThat(store.consume(key, 1, Duration.ofMinutes(1)).allowed()).isFalse();
        assertThat(jdbc.queryForObject("SELECT expires_at FROM auth_rate_limits WHERE bucket_key = ?", Object.class, key)).isEqualTo(expiry);
        assertThat(jdbc.queryForObject("SELECT attempts FROM auth_rate_limits WHERE bucket_key = ?", Integer.class, key)).isEqualTo(2);
        jdbc.update("UPDATE auth_rate_limits SET expires_at = clock_timestamp() - INTERVAL '1 second'");
        assertThat(store.consume(key, 1, Duration.ofMinutes(1)).allowed()).isTrue();
    }

    @Test
    void separateBackendLimitersShareOneAtomicBudget() throws Exception {
        var replica = new AuthRequestLimiter(store, properties, KEY);
        var executor = Executors.newFixedThreadPool(8);
        var start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int i = 0; i < 16; i++) {
                AuthRequestLimiter selected = i % 2 == 0 ? limiter : replica;
                results.add(executor.submit(() -> {
                    start.await();
                    try { selected.account(AuthAction.LOGIN, " USER@EXAMPLE.COM "); return true; }
                    catch (AuthRateLimitExceededException denied) { return false; }
                }));
            }
            start.countDown();
            int allowed = 0;
            for (var result : results) if (result.get(15, TimeUnit.SECONDS)) allowed++;
            assertThat(allowed).isEqualTo(3);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_rate_limits", Long.class)).isEqualTo(1);
        } finally { start.countDown(); executor.shutdownNow(); }
    }

    @Test
    void quotaCommitsWorkEvenWhenAuthTransactionsHoldTheEntireMainPool() throws Exception {
        int size = jdbc.getDataSource().unwrap(HikariDataSource.class).getMaximumPoolSize();
        var executor = Executors.newFixedThreadPool(size);
        var allConnectionsHeld = new CountDownLatch(size);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int i = 0; i < size; i++) results.add(executor.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                jdbc.queryForObject("SELECT 1", Integer.class);
                allConnectionsHeld.countDown();
                try { if (!allConnectionsHeld.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("main pool was not filled"); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
                try { limiter.account(AuthAction.LOGIN, "full-pool@example.com"); return true; }
                catch (AuthRateLimitExceededException denied) { return false; }
            })));
            int allowed = 0;
            for (var result : results) if (result.get(15, TimeUnit.SECONDS)) allowed++;
            assertThat(allowed).isEqualTo(3);
        } finally { executor.shutdownNow(); }
    }

    @Test
    void quotaCommitsEvenWhenOuterBusinessTransactionRollsBack() {
        assertThatThrownBy(() -> new TransactionTemplate(transactions).execute(status -> {
            limiter.account(AuthAction.LOGIN, "rollback@example.com");
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT attempts FROM auth_rate_limits", Integer.class)).isEqualTo(1);
    }

    @Test
    void quotaStorageFailureReturns503WithoutLeakingDatabaseDetails() throws Exception {
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("private-database-details"))
                .when(store).consume(anyString(), anyInt(), any());
        var response = mvc.perform(command("login", json.writeValueAsString(Map.of("email", "private@example.com", "password", PASSWORD))))
                .andExpect(status().isServiceUnavailable()).andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.code").value("AUTH_TEMPORARILY_UNAVAILABLE")).andReturn().getResponse();
        assertThat(response.getContentAsString()).doesNotContain("private", PASSWORD);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING_ACTIVATION", "ARCHIVED", "BLOCKED", "LOCKED", "MISSING"})
    void loginDoesNotRevealWhetherAnAccountExistsOrIsLocked(String state) throws Exception {
        String email = email();
        if (!state.equals("MISSING")) {
            long id = account(email, state.equals("LOCKED") ? "ACTIVE" : state);
            if (state.equals("LOCKED")) jdbc.update("UPDATE users SET locked_until=? WHERE id=?", LocalDateTime.now().plusMinutes(15), id);
        }
        var response = mvc.perform(command("login", json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.message").value("Invalid email or password"))
                .andReturn().getResponse();
        assertThat(response.getContentAsString()).doesNotContain(email, PASSWORD);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "PENDING_ACTIVATION", "BLOCKED", "ARCHIVED", "MISSING"})
    void recoveryAndResendHaveNeutralResponsesAcrossAccountStates(String state) throws Exception {
        String email = email();
        if (!state.equals("MISSING")) account(email, state);
        for (String route : List.of("password-recovery", "resend-activation")) {
            mvc.perform(command(route, json.writeValueAsString(Map.of("email", email))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value(AuthServiceImpl.AUTH_INSTRUCTIONS_MESSAGE));
        }
        long expected = state.equals("MISSING") || state.equals("ARCHIVED") ? 0 : state.equals("PENDING_ACTIVATION") ? 2 : 1;
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications", Long.class)).isEqualTo(expected);
        if (expected > 0) assertThat(jdbc.queryForList("SELECT auth_token_id IS NOT NULL AND secret_expires_at IS NOT NULL AS linked FROM notifications", Boolean.class))
                .containsOnly(true);
    }

    @Test
    void allMailCommandsShareOneNormalizedAccountBudget() {
        String email = email();
        account(email, "PENDING_ACTIVATION");
        auth.requestPasswordRecovery(new PasswordRecoveryRequest(email));
        auth.resendActivation(new ResendActivationRequest(email.toUpperCase()));
        auth.register(registration(email, phone()));
        assertThatThrownBy(() -> auth.requestPasswordRecovery(new PasswordRecoveryRequest(" " + email + " ")))
                .isInstanceOf(AuthRateLimitExceededException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications", Long.class)).isEqualTo(2);
    }

    @Test
    void newAndDuplicateEmailAndPhoneRegistrationsReturnTheSameResponse() throws Exception {
        String email = email(), phone = phone();
        for (RegisterRequest request : List.of(registration(email, phone), registration(email, phone()), registration(email(), phone))) {
            mvc.perform(command("register", json.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.message").value(AuthServiceImpl.AUTH_INSTRUCTIONS_MESSAGE));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ? OR phone = ?", Long.class, email, phone)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_roles ur JOIN users u ON u.id=ur.user_id WHERE u.email=?", Long.class, email)).isEqualTo(1);
    }

    @Test
    void concurrentDuplicateRegistrationCreatesOneAccountCodeAndMessage() throws Exception {
        String email = email(), phone = phone();
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var one = executor.submit(() -> { start.await(); return auth.register(registration(email, phone)); });
            var two = executor.submit(() -> { start.await(); return auth.register(registration(email, phone)); });
            start.countDown();
            assertThat(one.get(15, TimeUnit.SECONDS)).isEqualTo(two.get(15, TimeUnit.SECONDS));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email=?", Long.class, email)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications", Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_tokens t JOIN users u ON u.id=t.user_id WHERE u.email=?", Long.class, email)).isEqualTo(1);
        } finally { start.countDown(); executor.shutdownNow(); }
    }

    @Test
    void equivalentPhonePrefixesShareRegistrationQuota() {
        for (int i = 0; i < 3; i++) limiter.registrationPhone("89991234567");
        assertThatThrownBy(() -> limiter.registrationPhone("+79991234567")).isInstanceOf(AuthRateLimitExceededException.class);
    }

    @ParameterizedTest
    @EnumSource(value = AuthAction.class, names = {"ACTIVATE", "RESET"})
    void invalidCodeAttemptsAreLimitedAndCommitAfterRollback(AuthAction action) {
        for (int i = 0; i < 3; i++) assertThatThrownBy(() -> invalidCode(action)).hasMessage("Invalid or expired token");
        assertThatThrownBy(() -> invalidCode(action)).isInstanceOf(AuthRateLimitExceededException.class);
    }

    @Test
    void oversizedPasswordsProduceControlledErrorsBeforeBcryptEncoding() throws Exception {
        String password = "a1" + "я".repeat(36);
        var request = new RegisterRequest(email(), phone(), password, password, "Test", "User", null);
        mvc.perform(command("register", json.writeValueAsString(request))).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Password must not exceed 72 UTF-8 bytes"));
        mvc.perform(command("password-reset", json.writeValueAsString(new PasswordResetRequest("invalid", password, password))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void oversizedBodiesAreRejectedAndPrivateEndpointStaysProtected() throws Exception {
        mvc.perform(command("login", "x".repeat(properties.getMaxRequestBytes() + 1)))
                .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("AUTH_REQUEST_TOO_LARGE"));
        mvc.perform(get("/api/auth/me").servletPath("/api").pathInfo("/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bodyLimitAlsoAppliesWhenContentLengthIsUnknown() throws Exception {
        byte[] body = json.writeValueAsBytes(Map.of("email", email(), "password", "x".repeat(properties.getMaxRequestBytes() * 2)));
        mvc.perform(context -> {
            var request = new MockHttpServletRequest(context) {
                @Override public int getContentLength() { return -1; }
                @Override public long getContentLengthLong() { return -1; }
            };
            request.setMethod("POST");
            request.setRequestURI("/api/auth/login");
            request.setServletPath("/api");
            request.setPathInfo("/auth/login");
            request.setRemoteAddr("203.0.113.250");
            request.setContentType("application/json");
            request.setContent(body);
            return request;
        }).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_rate_limits", Long.class)).isEqualTo(1);
    }

    @Test
    void neutralResponsesArePaddedAfterProcessing() throws Exception {
        long start = System.nanoTime();
        mvc.perform(command("password-recovery", json.writeValueAsString(Map.of("email", email())))).andExpect(status().isOk());
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(properties.getMinimumResponseTime());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "FAILED", "SENT", "DELIVERED", "UNKNOWN", "SENDING"})
    void retentionScrubsExpiredSecretsAndPreservesDeliveryHistory(String state) {
        long user = account(email(), "ACTIVE");
        long id = secretMessage(user, state, true);
        if (state.equals("SENDING")) jdbc.update("UPDATE notifications SET sending_started_at=? WHERE id=?", utcNow().minusHours(1), id);
        var result = retention.cleanupBatch();
        assertThat(result.secretNotifications()).isEqualTo(1);
        var row = jdbc.queryForMap("SELECT * FROM notifications WHERE id=?", id);
        assertThat(row.get("body")).isEqualTo("[redacted authentication message]");
        assertThat(row.get("subject")).isEqualTo("Authentication message expired");
        assertThat(row.get("auth_token_id")).isNull();
        assertThat(row.get("secret_purged_at")).isNotNull();
        assertThat(row.get("version")).isEqualTo(1L);
        assertThat(row.get("status").toString()).isEqualTo(switch(state) {
            case "PENDING", "FAILED" -> "CANCELLED";
            case "SENDING" -> "UNKNOWN";
            default -> state;
        });
        assertThat(retention.cleanupBatch().secretNotifications()).isZero();
    }

    @Test
    void retentionKeepsLiveSecretsAndFreshSendingButScrubsUsedAndOldBodies() {
        long user = account(email(), "ACTIVE");
        long live = secretMessage(user, "PENDING", false);
        long sending = secretMessage(user, "SENDING", true);
        jdbc.update("UPDATE notifications SET sending_started_at=? WHERE id=?", utcNow(), sending);
        jdbc.update("UPDATE auth_tokens SET created_at=?, expires_at=? WHERE id=(SELECT auth_token_id FROM notifications WHERE id=?)",
                utcNow().minusHours(26), utcNow().minusHours(25), sending);
        long used = secretMessage(user, "SENT", false);
        jdbc.update("UPDATE auth_tokens SET used_at=? WHERE id=(SELECT auth_token_id FROM notifications WHERE id=?)", utcNow(), used);
        long old = secretMessage(user, "SENT", false);
        jdbc.update("UPDATE notifications SET created_at=? WHERE id=?", utcNow().minusHours(25), old);
        assertThat(retention.cleanupBatch().secretNotifications()).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT id FROM notifications WHERE secret_purged_at IS NULL ORDER BY id", Long.class)).containsExactly(live, sending);
        assertThat(jdbc.queryForObject("SELECT auth_token_id IS NOT NULL FROM notifications WHERE id=?", Boolean.class, sending)).isTrue();
    }

    @Test
    void cleanupSkipsAnAccountLockedByReissueAndKeepsTheNewCode() throws Exception {
        long user = account(email(), "ACTIVE");
        long old = secretMessage(user, "PENDING", true);
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var held = executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE", Long.class, user);
                locked.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("lock timed out"); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            retention.cleanupBatch();
            assertThat(jdbc.queryForObject("SELECT secret_purged_at IS NULL FROM notifications WHERE id=?", Boolean.class, old)).isTrue();
            release.countDown(); held.get(10, TimeUnit.SECONDS);
            String email = jdbc.queryForObject("SELECT email FROM users WHERE id=?", String.class, user);
            auth.requestPasswordRecovery(new PasswordRecoveryRequest(email));
            retention.cleanupBatch();
            assertThat(jdbc.queryForList("SELECT secret_purged_at IS NULL FROM notifications WHERE user_id=? ORDER BY id", Boolean.class, user))
                    .containsExactly(false, true);
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    @Test
    void cleanupProcessesSecretBodiesInBoundedBatches() {
        long user = account(email(), "ACTIVE");
        for (int i = 0; i < 3; i++) secretMessage(user, "PENDING", true);
        int original = properties.getCleanupBatchSize();
        try {
            properties.setCleanupBatchSize(1);
            for (int i = 0; i < 3; i++) assertThat(retention.cleanupBatch().secretNotifications()).isEqualTo(1);
            assertThat(retention.cleanupBatch().secretNotifications()).isZero();
        } finally { properties.setCleanupBatchSize(original); }
    }

    @Test
    void onlyOldInvalidTokensAndExpiredQuotaRowsAreDeleted() {
        long user = account(email(), "ACTIVE");
        long live = token(user, false), recentExpired = token(user, true), oldExpired = token(user, true), oldUsed = token(user, false);
        jdbc.update("UPDATE auth_tokens SET expires_at=?, created_at=? WHERE id=?", utcNow().minusHours(25), utcNow().minusHours(26), oldExpired);
        jdbc.update("UPDATE auth_tokens SET used_at=?, created_at=? WHERE id=?", utcNow().minusHours(25), utcNow().minusHours(26), oldUsed);
        limiter.ip(AuthAction.LOGIN, "203.0.113.1");
        jdbc.update("UPDATE auth_rate_limits SET expires_at=clock_timestamp()-INTERVAL '1 second'");
        assertThat(retention.cleanupBatch()).satisfies(result -> {
            assertThat(result.quotaBuckets()).isEqualTo(1);
            assertThat(result.tokens()).isGreaterThanOrEqualTo(2);
        });
        assertThat(jdbc.queryForList("SELECT id FROM auth_tokens WHERE user_id=? ORDER BY id", Long.class, user)).containsExactly(live, recentExpired);
    }

    @Test
    void deliveryNeverClaimsAnExpiredUsedOrScrubbedAuthenticationMessage() {
        long user = account(email(), "ACTIVE");
        secretMessage(user, "PENDING", true);
        long used = secretMessage(user, "PENDING", false);
        jdbc.update("UPDATE auth_tokens SET used_at=? WHERE id=(SELECT auth_token_id FROM notifications WHERE id=?)", utcNow(), used);
        long live = secretMessage(user, "PENDING", false);
        new TransactionTemplate(transactions).executeWithoutResult(status ->
                assertThat(notifications.findNextPendingForUpdate(3)).get().extracting(Notification::getId).isEqualTo(live));
        retention.cleanupBatch();
        new TransactionTemplate(transactions).executeWithoutResult(status ->
                assertThat(notifications.findNextPendingForUpdate(3)).get().extracting(Notification::getId).isEqualTo(live));
    }

    @Test
    void issuedMessageUsesTheSameUtcDeadlineAsItsTokenAndIsDeliverableUntilExpiry() {
        String email = email();
        long user = account(email, "ACTIVE");
        auth.requestPasswordRecovery(new PasswordRecoveryRequest(email));
        long message = jdbc.queryForObject("SELECT id FROM notifications WHERE user_id=?", Long.class, user);
        LocalDateTime deadline = jdbc.queryForObject("SELECT expires_at FROM auth_tokens WHERE user_id=?", LocalDateTime.class, user);
        assertThat(Duration.between(utcNow(), deadline)).isBetween(Duration.ofMinutes(59), Duration.ofMinutes(61));
        retention.cleanupBatch();
        assertThat(jdbc.queryForObject("SELECT secret_purged_at IS NULL FROM notifications WHERE id=?", Boolean.class, message)).isTrue();
        new TransactionTemplate(transactions).executeWithoutResult(status ->
                assertThat(notifications.findNextPendingForUpdate(3)).get().extracting(Notification::getId).isEqualTo(message));
        jdbc.update("UPDATE auth_tokens SET created_at=?, expires_at=? WHERE user_id=?", utcNow().minusHours(2), utcNow().minusHours(1), user);
        new TransactionTemplate(transactions).executeWithoutResult(status -> assertThat(notifications.findNextPendingForUpdate(3)).isEmpty());
    }

    @Test
    void authMessagesCannotBeManuallyRequeuedByStaff() {
        long staff = account(email(), "ACTIVE");
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE code='ADMIN'", staff);
        long id = secretMessage(staff, "FAILED", false);
        assertThatThrownBy(() -> notificationService.resend(staff, id)).hasMessageContaining("authentication");
        assertThat(jdbc.queryForObject("SELECT status::text FROM notifications WHERE id=?", String.class, id)).isEqualTo("FAILED");
    }

    @Test
    void migrationLinksLegacyAuthMessagesToExistingCodes() {
        String schema = "auth_b08_legacy";
        var flyway = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema).locations("classpath:database/migration").target("11").load();
        flyway.migrate();
        var legacy = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl() + "&currentSchema=" + schema, POSTGRES.getUsername(), POSTGRES.getPassword()));
        long user = legacy.queryForObject("INSERT INTO users(email,phone,password_hash,first_name,last_name,status) VALUES ('legacy@example.com','+79999999990','hash','Test','User','ACTIVE') RETURNING id", Long.class);
        long token = legacy.queryForObject("INSERT INTO auth_tokens(user_id,type,token,created_at,expires_at) VALUES (?,'PASSWORD_RESET','legacy-secret',timezone('UTC',clock_timestamp()),timezone('UTC',clock_timestamp())+INTERVAL '1 hour') RETURNING id", Long.class, user);
        legacy.update("INSERT INTO notifications(user_id,type,channel,status,body) VALUES (?,'PASSWORD_RECOVERY','EMAIL','PENDING','Code: legacy-secret')", user);
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema).locations("classpath:database/migration").load().migrate();
        assertThat(legacy.queryForObject("SELECT auth_token_id FROM notifications", Long.class)).isEqualTo(token);
        assertThat(legacy.queryForObject("SELECT secret_expires_at > timezone('UTC',clock_timestamp()) FROM notifications", Boolean.class)).isTrue();
    }

    private MockHttpServletRequestBuilder command(String route, String body) {
        return post("/api/auth/" + route).servletPath("/api").pathInfo("/auth/" + route)
                .contentType("application/json").content(body).with(request -> { request.setRemoteAddr("203.0.113.250"); return request; });
    }
    private String email() { return "auth-b08-" + SEQUENCE.incrementAndGet() + "@example.com"; }
    private String phone() { return "+7" + String.format("%010d", SEQUENCE.incrementAndGet()); }
    private RegisterRequest registration(String email, String phone) { return new RegisterRequest(email, phone, PASSWORD, PASSWORD, "Test", "User", null); }
    private long account(String email, String state) {
        return jdbc.queryForObject("INSERT INTO users(email,phone,password_hash,first_name,last_name,status) VALUES (?,?,?,?,?,?::user_status) RETURNING id",
                Long.class, email, phone(), encoder.encode(PASSWORD), "Test", "User", state);
    }
    private long token(long user, boolean expired) {
        return jdbc.queryForObject("INSERT INTO auth_tokens(user_id,type,token,expires_at,created_at) VALUES (?,'PASSWORD_RESET',?,?,?) RETURNING id",
                Long.class, user, UUID.randomUUID().toString(), utcNow().plusHours(expired ? -1 : 1), utcNow().minusHours(2));
    }
    private long secretMessage(long user, String state, boolean expired) {
        long token = token(user, expired);
        return jdbc.queryForObject("INSERT INTO notifications(user_id,type,channel,status,subject,body,error_message,auth_token_id,secret_expires_at,created_at,sent_at) SELECT ?,'PASSWORD_RECOVERY','EMAIL',?::notification_status,'secret-subject','secret-body','secret-error',id,expires_at,?,? FROM auth_tokens WHERE id=? RETURNING notifications.id",
                Long.class, user, state, utcNow().minusMinutes(1), state.equals("SENT") || state.equals("DELIVERED") ? utcNow() : null, token);
    }
    private void invalidCode(AuthAction action) {
        if (action == AuthAction.ACTIVATE) auth.activate(new ActivateAccountRequest("invalid-code"));
        else auth.resetPassword(new PasswordResetRequest("invalid-code", PASSWORD, PASSWORD));
    }
    private LocalDateTime utcNow() { return LocalDateTime.now(ZoneOffset.UTC); }
}
