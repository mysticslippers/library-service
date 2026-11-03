package me.ifmo.backend.security;

import me.ifmo.backend.audit.application.AuditLogService;
import me.ifmo.backend.audit.domain.enums.AuditAction;
import me.ifmo.backend.audit.domain.enums.AuditEntityType;
import me.ifmo.backend.authentication.security.JwtService;
import me.ifmo.backend.catalog.storage.CoverObjectStorage;
import me.ifmo.backend.library.application.LibraryService;
import me.ifmo.backend.library.web.request.CreateLibraryRequest;
import me.ifmo.backend.library.web.request.UpdateLibraryRequest;
import me.ifmo.backend.notification.application.NotificationDeliveryClient;
import me.ifmo.backend.user.domain.enums.RoleCode;
import me.ifmo.backend.user.persistence.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = AccessTestApplication.class, properties = {
        "security.auth.protection.cleanup-enabled=false",
        "security.auth.protection.minimum-response-time=0ms",
        "security.auth.protection.key=test-only-audit-quota-key-at-least-32-characters",
        "spring.cache.type=none",
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false",
        "spring.datasource.hikari.maximum-pool-size=2",
        "spring.datasource.hikari.minimum-idle=0",
        "storage.covers.endpoint=http://127.0.0.1:1",
        "storage.covers.access-key=test-access-key",
        "storage.covers.secret-key=test-secret-key"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class AuditActorIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        properties.add("security.jwt.secret", () -> Base64.getEncoder().encodeToString(
                "audit-tests-only-signing-key-with-64-bytes-00000000000000000000000"
                        .getBytes(StandardCharsets.UTF_8)));
    }

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private LibraryService libraries;
    @Autowired private AuditLogService audit;
    @Autowired private UserRepository users;
    @Autowired private JwtService jwt;
    @Autowired private AccessTestData data;
    @MockitoBean private CoverObjectStorage storage;
    @MockitoBean private NotificationDeliveryClient delivery;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        data.reset();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void jwtRequestsAttributeChangesToEachAuthenticatedActorAndRejectUnauthorizedWrites() throws Exception {
        mvc.perform(post("/api/libraries")
                        .servletPath("/api")
                        .header("Authorization", "Bearer " + token(data.admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"AUDIT-HTTP\",\"name\":\"Created\"}"))
                .andExpect(status().isCreated());
        long library = libraryId("AUDIT-HTTP");
        long otherAdmin = data.user(RoleCode.ADMIN);

        mvc.perform(patch("/api/libraries/" + library)
                        .servletPath("/api")
                        .header("Authorization", "Bearer " + token(otherAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated\"}"))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/libraries/" + library)
                        .servletPath("/api")
                        .header("Authorization", "Bearer " + token(data.reader))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Forbidden\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/libraries/" + library)
                        .servletPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Anonymous\"}"))
                .andExpect(status().isUnauthorized());

        assertThat(jdbc.queryForList("""
                SELECT actor_user_id FROM audit_logs
                WHERE entity_type = 'LIBRARY' AND entity_id = ? ORDER BY id
                """, Long.class, library)).containsExactly(data.admin, otherAdmin);
        assertThat(actorSetting()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void commitRollbackAndAnonymousWorkDoNotLeakActorThroughTheSamePooledConnection() throws Exception {
        try (var spare = dataSource.getConnection()) {
            authenticate(data.admin);
            int connectionId = transactions().execute(status -> {
                createLibrary("COMMITTED");
                return backendPid();
            });
            assertThat(actorSetting()).isNull();
            assertThat(backendPid()).isEqualTo(connectionId);

            authenticate(data.librarian);
            transactions().executeWithoutResult(status -> {
                assertThat(backendPid()).isEqualTo(connectionId);
                createLibrary("ROLLED-BACK");
                status.setRollbackOnly();
            });
            assertThat(actorSetting()).isNull();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM libraries WHERE code = 'ROLLED-BACK'", Integer.class))
                    .isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE actor_user_id = ?",
                    Integer.class, data.librarian)).isZero();

            SecurityContextHolder.clearContext();
            long systemLibrary = transactions().execute(status -> {
                assertThat(backendPid()).isEqualTo(connectionId);
                return createLibrary("SYSTEM");
            });
            var principal = User.withUsername(Long.toString(data.admin)).password("unused")
                    .authorities("ROLE_ANONYMOUS").build();
            SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                    "anonymous", principal, List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
            long anonymousLibrary = transactions().execute(status -> createLibrary("ANONYMOUS"));

            assertThat(actorOf(libraryId("COMMITTED"))).isEqualTo(data.admin);
            assertThat(actorOf(systemLibrary)).isNull();
            assertThat(actorOf(anonymousLibrary)).isNull();
            assertThat(actorSetting()).isNull();
        }
    }

    @Test
    void requiresNewHasItsOwnActorAndCommitFlushRetainsTheOuterActor() {
        long existing = createLibrary("OUTER");
        authenticate(data.admin);
        var inner = transactions();
        inner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        long innerLibrary = transactions().execute(status -> {
            int outerConnection = backendPid();
            libraries.update(existing, new UpdateLibraryRequest(null, "Flushed at commit"));
            authenticate(data.librarian);
            long created = inner.execute(innerStatus -> {
                assertThat(backendPid()).isNotEqualTo(outerConnection);
                return createLibrary("INNER");
            });
            assertThat(actorSetting()).isEqualTo(Long.toString(data.admin));
            SecurityContextHolder.clearContext();
            return created;
        });

        assertThat(actorOf(existing)).isEqualTo(data.admin);
        assertThat(actorOf(innerLibrary)).isEqualTo(data.librarian);
        assertThat(actorSetting()).isNull();
    }

    @Test
    void concurrentTransactionsKeepIndependentActors() throws Exception {
        var barrier = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> createConcurrently(data.admin, "CONCURRENT-A", barrier));
            var second = executor.submit(() -> createConcurrently(data.librarian, "CONCURRENT-B", barrier));

            assertThat(actorOf(first.get(15, TimeUnit.SECONDS))).isEqualTo(data.admin);
            assertThat(actorOf(second.get(15, TimeUnit.SECONDS))).isEqualTo(data.librarian);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void readOnlyTransactionsAndExplicitManualActorsRemainSupported() {
        authenticate(data.admin);
        var readOnly = transactions();
        readOnly.setReadOnly(true);
        String actor = readOnly.execute(status -> actorSetting());
        assertThat(actor).isEqualTo(Long.toString(data.admin));

        var manual = audit.record(data.librarian, AuditEntityType.USER, data.reader,
                AuditAction.ROLE_CHANGED, Map.of("assignedRole", "READER", "password", "secret-marker"));

        assertThat(jdbc.queryForObject("SELECT actor_user_id FROM audit_logs WHERE id = ?", Long.class, manual.id()))
                .isEqualTo(data.librarian);
        assertThat(jdbc.queryForObject("SELECT details::text FROM audit_logs WHERE id = ?", String.class, manual.id()))
                .doesNotContain("password", "secret-marker");
        assertThat(actorSetting()).isNull();
    }

    private long createConcurrently(long actor, String code, CyclicBarrier barrier) {
        authenticate(actor);
        try {
            return transactions().execute(status -> {
                try {
                    barrier.await(10, TimeUnit.SECONDS);
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
                return createLibrary(code);
            });
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private TransactionTemplate transactions() {
        return new TransactionTemplate(transactionManager);
    }

    private long createLibrary(String code) {
        return libraries.create(new CreateLibraryRequest(code, "Audit " + code)).id();
    }

    private long libraryId(String code) {
        return jdbc.queryForObject("SELECT id FROM libraries WHERE code = ?", Long.class, code);
    }

    private Long actorOf(long libraryId) {
        return jdbc.queryForObject("""
                SELECT actor_user_id FROM audit_logs
                WHERE entity_type = 'LIBRARY' AND entity_id = ? ORDER BY id DESC LIMIT 1
                """, Long.class, libraryId);
    }

    private String actorSetting() {
        return jdbc.queryForObject("SELECT NULLIF(current_setting('app.current_user_id', true), '')", String.class);
    }

    private int backendPid() {
        return jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
    }

    private void authenticate(long actor) {
        var principal = User.withUsername(Long.toString(actor)).password("unused").authorities("ROLE_ADMIN").build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()));
    }

    private String token(long actor) {
        return jwt.generate(users.findById(actor).orElseThrow(), List.of(RoleCode.ADMIN));
    }
}
