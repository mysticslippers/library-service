package me.ifmo.backend.audit.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import me.ifmo.backend.audit.application.impl.AuditLogServiceImpl;
import me.ifmo.backend.audit.domain.enums.AuditAction;
import me.ifmo.backend.audit.domain.enums.AuditEntityType;
import me.ifmo.backend.audit.mapper.AuditDetailsJsonMapper;
import me.ifmo.backend.audit.mapper.AuditLogMapperImpl;
import me.ifmo.backend.user.mapper.RoleMapperImpl;
import me.ifmo.backend.user.mapper.UserMapperImpl;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false, properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({AuditLogServiceImpl.class, AuditDetailsSanitizer.class, AuditLogMapperImpl.class,
        AuditDetailsJsonMapper.class, UserMapperImpl.class, RoleMapperImpl.class})
@Testcontainers(disabledWithoutDocker = true)
class AuditSecurityIntegrationTest {

    private static final String SECRET = "audit-secret-marker";
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private AuditLogService service;
    @Autowired private AuditDetailsSanitizer sanitizer;

    @Test
    void userInsertPasswordChangeStatusChangeAndDeleteKeepEventsWithoutHashes() throws Exception {
        Long userId = user(jdbc);
        jdbc.update("UPDATE users SET password_hash = ? WHERE id = ?", SECRET + "-new", userId);
        assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, userId))
                .isEqualTo(SECRET + "-new");
        jdbc.update("UPDATE users SET status = 'BLOCKED' WHERE id = ?", userId);
        jdbc.update("DELETE FROM users WHERE id = ?", userId);

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT action::text AS action, details::text AS details FROM audit_logs
                WHERE entity_type = 'USER' AND entity_id = ? ORDER BY id
                """, userId);
        assertThat(rows).extracting(row -> row.get("action")).containsExactly("CREATE", "UPDATE", "UPDATE", "DELETE");
        assertThat(rows.toString()).doesNotContain(SECRET, "password_hash", "passwordHash", "@example.com", "+7903");
        JsonNode passwordChange = json.readTree((String) rows.get(1).get("details"));
        assertThat(passwordChange.path("password_changed").asBoolean()).isTrue();
        assertThat(passwordChange.path("old_data").path("id").asLong()).isEqualTo(userId);
        assertThat(passwordChange.path("new_data").path("status").asText()).isEqualTo("ACTIVE");
        assertThat(json.readTree((String) rows.get(2).get("details")).has("password_changed")).isFalse();
    }

    @ParameterizedTest
    @EnumSource(AuditEntityType.class)
    void databaseWritesAndJavaUseTheSameAllowlistForEveryEntity(AuditEntityType type) throws Exception {
        String table = jdbc.queryForObject("SELECT audit_entity_table(?)", String.class, type.name());
        Map<String, Object> unsafe = unsafeDetails(table);
        // Include every real column so policy differences outside the hand-written examples are detected too.
        Map<String, Object> fullSnapshot = new LinkedHashMap<>();
        jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = ?",
                String.class, table).forEach(column -> fullSnapshot.put(column, "example-" + column));
        fullSnapshot.putAll(snapshotExample(unsafe));
        unsafe.put("old_data", fullSnapshot);
        unsafe.put("new_data", fullSnapshot);
        JsonNode expected = json.readTree(json.writeValueAsString(sanitizer.sanitize(type, unsafe)));
        Long id = jdbc.queryForObject("""
                INSERT INTO audit_logs(entity_type, entity_id, action, details)
                VALUES (CAST(? AS audit_entity_type), 41, 'UPDATE', CAST(? AS jsonb)) RETURNING id
                """, Long.class, type.name(), json.writeValueAsString(unsafe));

        assertThat(details(jdbc, id)).isEqualTo(expected);
        assertThat(details(jdbc, id).toString()).doesNotContain(SECRET, "password_hash", "passwordHash", "future_credentials");
        assertThat(details(jdbc, id).path("new_data").path("id").asInt()).isEqualTo(41);
        jdbc.update("UPDATE audit_logs SET details = CAST(? AS jsonb) WHERE id = ?", json.writeValueAsString(unsafe), id);
        assertThat(details(jdbc, id)).isEqualTo(expected);

        // Invalid field names in the policy would silently erase useful audit data after a schema change.
        assertThat(jdbc.queryForList("""
                SELECT field FROM unnest(audit_snapshot_fields(?)) AS policy(field)
                WHERE NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = 'public' AND table_name = ? AND column_name = field)
                """, String.class, type.name(), table)).isEmpty();
    }

    @Test
    void manualRecordFiltersBeforePersistenceAndKeepsBusinessFields() throws Exception {
        Map<String, Object> unsafe = unsafeDetails("users");
        String original = json.writeValueAsString(unsafe);

        var response = service.record(null, AuditEntityType.USER, 41L, AuditAction.ROLE_CHANGED, unsafe);
        entityManager.flush();

        assertThat(response.details()).containsEntry("assignedRole", "LIBRARIAN");
        assertThat(response.details().get("roles")).isEqualTo(List.of("READER", "ADMIN"));
        assertThat(details(jdbc, response.id())).isEqualTo(json.readTree(json.writeValueAsString(response.details())));
        assertThat(details(jdbc, response.id()).toString()).doesNotContain(SECRET);
        assertThat(json.writeValueAsString(unsafe)).isEqualTo(original);
    }

    @Test
    void migrationFromV7ScrubsStoredHistoryWithoutDeletingEventsOrChangingMetadata() throws Exception {
        DriverManagerDataSource legacySource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(), POSTGRES.getPassword());
        Properties connectionProperties = new Properties();
        connectionProperties.setProperty("currentSchema", "legacy_audit");
        legacySource.setConnectionProperties(connectionProperties);
        var legacy = new JdbcTemplate(legacySource);
        migrate(legacySource, "7");
        Long userId = user(legacy);
        legacy.update("UPDATE users SET password_hash = ? WHERE id = ?", SECRET + "-changed", userId);
        Long manualId = legacy.queryForObject("""
                INSERT INTO audit_logs(actor_user_id, entity_type, entity_id, action, details)
                VALUES (?, 'USER', ?, 'ROLE_CHANGED', CAST(? AS jsonb)) RETURNING id
                """, Long.class, userId, userId, json.writeValueAsString(unsafeDetails("users")));
        var metadataBefore = legacy.queryForList("""
                SELECT id, actor_user_id, entity_type::text, entity_id, action::text, created_at FROM audit_logs ORDER BY id
                """);
        assertThat(legacy.queryForList("SELECT details::text FROM audit_logs").toString()).contains(SECRET, "password_hash");

        migrate(legacySource, "8");

        assertThat(legacy.queryForList("""
                SELECT id, actor_user_id, entity_type::text, entity_id, action::text, created_at FROM audit_logs ORDER BY id
                """)).isEqualTo(metadataBefore);
        assertThat(legacy.queryForList("SELECT details::text FROM audit_logs").toString())
                .doesNotContain(SECRET, "password_hash", "passwordHash", "future_credentials");
        assertThat(details(legacy, manualId).path("roles").toString()).isEqualTo("[\"READER\",\"ADMIN\"]");
        legacy.update("UPDATE users SET password_hash = ? WHERE id = ?", SECRET + "-after-migration", userId);
        assertThat(legacy.queryForList("SELECT details::text FROM audit_logs").toString()).doesNotContain(SECRET);
    }

    private void migrate(DriverManagerDataSource source, String target) {
        Flyway.configure().dataSource(source).locations("classpath:database/migration")
                .schemas("legacy_audit").defaultSchema("legacy_audit").target(target).load().migrate();
    }

    private Long user(JdbcTemplate database) {
        long sequence = SEQUENCE.incrementAndGet();
        return database.queryForObject("""
                INSERT INTO users(email, phone, password_hash, first_name, last_name, status)
                VALUES (?, ?, ?, 'Audit', 'User', 'ACTIVE') RETURNING id
                """, Long.class, "audit-" + sequence + "@example.com", "+7903%07d".formatted(sequence), SECRET);
    }

    private JsonNode details(JdbcTemplate database, Long id) throws Exception {
        return json.readTree(database.queryForObject("SELECT details::text FROM audit_logs WHERE id = ?", String.class, id));
    }

    private Map<String, Object> unsafeDetails(String table) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", 41);
        snapshot.put("status", "ACTIVE");
        snapshot.put("amount", 120);
        snapshot.put("material_type", "BOOK");
        snapshot.put("password_hash", SECRET);
        snapshot.put("passwordHash", SECRET);
        snapshot.put("token", SECRET);
        snapshot.put("body", SECRET);
        snapshot.put("subject", SECRET);
        snapshot.put("error_message", SECRET);
        snapshot.put("external_payment_id", SECRET);
        snapshot.put("email", SECRET);
        snapshot.put("future_credentials", Map.of("secret", SECRET));
        snapshot.put("address", Map.of("city", "Moscow", "street", "Main", "building", "1", "token", SECRET));
        snapshot.put("description", List.of(Map.of("token", SECRET)));
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("table", table);
        details.put("operation", "UPDATE");
        details.put("old_data", snapshot);
        details.put("new_data", snapshot);
        details.put("roles", List.of("READER", Map.of("password", SECRET), SECRET, "ADMIN"));
        details.put("assignedRole", "LIBRARIAN");
        details.put("revokedRole", "READER");
        details.put("status", "ACTIVE");
        details.put("updated", true);
        details.put("userId", 41);
        details.put("reason", "Routine account maintenance");
        details.put("action", "RESEND");
        details.put("processed", 2);
        details.put("createdAt", "2026-10-23T12:00:00");
        details.put("type", "ACCOUNT_STATUS_CHANGED");
        details.put("channel", "EMAIL");
        details.put("enabled", true);
        details.put("preferred", false);
        details.put("password_changed", true);
        details.put("password", SECRET);
        details.put("metadata", Map.of("token", SECRET));
        return details;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> snapshotExample(Map<String, Object> details) {
        return (Map<String, Object>) details.get("new_data");
    }
}
