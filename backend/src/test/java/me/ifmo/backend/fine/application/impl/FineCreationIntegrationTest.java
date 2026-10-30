package me.ifmo.backend.fine.application.impl;

import me.ifmo.backend.catalog.mapper.MaterialCopyMapperImpl;
import me.ifmo.backend.fine.application.FineService;
import me.ifmo.backend.fine.application.PaymentLocks;
import me.ifmo.backend.fine.domain.Fine;
import me.ifmo.backend.fine.domain.enums.ViolationType;
import me.ifmo.backend.fine.mapper.FineMapper;
import me.ifmo.backend.fine.mapper.FineMapperImpl;
import me.ifmo.backend.fine.web.request.CancelFineRequest;
import me.ifmo.backend.fine.web.request.CreateFineRequest;
import me.ifmo.backend.fine.web.response.FineResponse;
import me.ifmo.backend.library.mapper.BranchAddressJsonMapper;
import me.ifmo.backend.library.mapper.BranchMapperImpl;
import me.ifmo.backend.library.mapper.LibraryMapperImpl;
import me.ifmo.backend.shared.exceptions.DuplicateResourceException;
import me.ifmo.backend.user.mapper.RoleMapperImpl;
import me.ifmo.backend.user.mapper.UserMapperImpl;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@DataJpaTest(showSql = false, properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import({FineServiceImpl.class, PaymentLocks.class, FineMapperImpl.class, MaterialCopyMapperImpl.class,
        UserMapperImpl.class, RoleMapperImpl.class, BranchMapperImpl.class, LibraryMapperImpl.class,
        BranchAddressJsonMapper.class})
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FineCreationIntegrationTest {

    private static final AtomicLong SEQUENCE = new AtomicLong();
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private FineService fines;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    @MockitoSpyBean private FineMapper mapper;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void concurrentCreatesAfterBothPrechecksLeaveOneFineAndOneAuditEntry() throws Exception {
        FineCreationFixture fixture = fixture(jdbc);
        long auditBefore = audits();
        CountDownLatch checked = new CountDownLatch(2);
        doAnswer(invocation -> {
            Fine fine = (Fine) invocation.callRealMethod();
            checked.countDown();
            if (!checked.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Both requests must pass the duplicate precheck");
            }
            return fine;
        }).when(mapper).toEntity(any(), any(), any(), any(), any(), any());

        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<FineResponse> first = executor.submit(() -> create(fixture, ViolationType.OTHER));
            Future<FineResponse> second = executor.submit(() -> create(fixture, ViolationType.OTHER));
            List<Object> results = List.of(result(first), result(second));
            assertThat(results.stream().filter(FineResponse.class::isInstance).count()).isEqualTo(1);
            assertThat(results.stream().filter(DuplicateResourceException.class::isInstance).count()).isEqualTo(1);
            DuplicateResourceException duplicate = results.stream().filter(DuplicateResourceException.class::isInstance)
                    .map(DuplicateResourceException.class::cast).findFirst().orElseThrow();
            assertThat(duplicate.getStatus()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(duplicate.getCode()).isEqualTo("DUPLICATE_RESOURCE");
            assertThat(count(jdbc, fixture.loan())).isEqualTo(1);
            assertThat(audits()).isEqualTo(auditBefore + 1);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void rollbackReleasesTheUniqueKeyAndDoesNotLeaveAnAuditEntry() {
        FineCreationFixture fixture = fixture(jdbc);
        long auditBefore = audits();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            create(fixture, ViolationType.OTHER);
            status.setRollbackOnly();
        });
        assertThat(count(jdbc, fixture.loan())).isZero();
        assertThat(audits()).isEqualTo(auditBefore);
        create(fixture, ViolationType.OTHER);
        assertThat(count(jdbc, fixture.loan())).isEqualTo(1);
    }

    @Test
    void differentReasonsAndDifferentLoansHaveIndependentKeys() {
        FineCreationFixture fixture = fixture(jdbc);
        create(fixture, ViolationType.OTHER);
        create(fixture, ViolationType.DAMAGE);
        FineCreationFixture other = fixture(jdbc);
        create(other, ViolationType.OTHER);
        assertThat(count(jdbc, fixture.loan())).isEqualTo(2);
        assertThat(count(jdbc, other.loan())).isEqualTo(1);
    }

    @Test
    void finesWithoutALoanAreNotGroupedIntoOneUniqueKey() {
        FineCreationFixture fixture = fixture(jdbc);
        CreateFineRequest request = new CreateFineRequest(fixture.reader(), null, null, null,
                ViolationType.OTHER, BigDecimal.TEN);
        long first = fines.create(fixture.staff(), request).id();
        long second = fines.create(fixture.staff(), request).id();
        assertThat(second).isNotEqualTo(first);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PAID", "CANCELLED"})
    void closedHistoryDoesNotPreventANewActiveFine(String status) {
        FineCreationFixture fixture = fixture(jdbc);
        long first = create(fixture, ViolationType.OTHER).id();
        if (status.equals("PAID")) {
            fines.markPaid(fixture.staff(), first);
        } else {
            fines.cancel(fixture.staff(), first, new CancelFineRequest("Created by mistake"));
        }
        long next = create(fixture, ViolationType.OTHER).id();
        assertThat(next).isNotEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT status::text FROM fines WHERE id = ?", String.class, first)).isEqualTo(status);
        assertThat(count(jdbc, fixture.loan())).isEqualTo(2);
    }

    @Test
    void directSqlCannotBypassTheUniqueIndex() {
        FineCreationFixture fixture = fixture(jdbc);
        create(fixture, ViolationType.OTHER);
        long auditBefore = audits();
        assertThatThrownBy(() -> insertFine(jdbc, fixture))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_fines_one_active_per_loan_reason");
        assertThat(count(jdbc, fixture.loan())).isEqualTo(1);
        assertThat(audits()).isEqualTo(auditBefore);
    }

    @Test
    void upgradeFromV12PreservesExistingFineAndInstallsTheIndex() {
        String schema = "fine_upgrade_" + SEQUENCE.incrementAndGet();
        migrate(schema, "12");
        JdbcTemplate legacy = legacy(schema);
        FineCreationFixture fixture = fixture(legacy);
        insertFine(legacy, fixture);
        var before = legacy.queryForList("SELECT * FROM fines ORDER BY id");
        migrate(schema, null);
        assertThat(legacy.queryForList("SELECT * FROM fines ORDER BY id")).isEqualTo(before);
        assertThatThrownBy(() -> insertFine(legacy, fixture)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void upgradeRefusesLegacyDuplicatesWithoutChangingFinesOrPayments() {
        String schema = "fine_duplicates_" + SEQUENCE.incrementAndGet();
        migrate(schema, "12");
        JdbcTemplate legacy = legacy(schema);
        FineCreationFixture fixture = fixture(legacy);
        insertFine(legacy, fixture);
        long second = insertFine(legacy, fixture);
        legacy.update("INSERT INTO payment_transactions(fine_id, amount, status) VALUES (?, 10, 'PENDING')", second);
        var finesBefore = legacy.queryForList("SELECT * FROM fines ORDER BY id");
        var paymentsBefore = legacy.queryForList("SELECT * FROM payment_transactions ORDER BY id");
        assertThatThrownBy(() -> migrate(schema, null)).hasStackTraceContaining("duplicate active fines exist");
        assertThat(legacy.queryForList("SELECT * FROM fines ORDER BY id")).isEqualTo(finesBefore);
        assertThat(legacy.queryForList("SELECT * FROM payment_transactions ORDER BY id")).isEqualTo(paymentsBefore);
        assertThat(legacy.queryForObject("SELECT count(*) FROM pg_indexes WHERE schemaname = ? AND indexname = ?",
                Long.class, schema, "uq_fines_one_active_per_loan_reason")).isZero();
    }

    private FineResponse create(FineCreationFixture fixture, ViolationType reason) {
        return fines.create(fixture.staff(), new CreateFineRequest(fixture.reader(), fixture.loan(), null, null,
                reason, BigDecimal.TEN));
    }

    private Object result(Future<FineResponse> future) throws Exception {
        try {
            return future.get(15, TimeUnit.SECONDS);
        } catch (ExecutionException exception) {
            return exception.getCause();
        }
    }

    private long count(JdbcTemplate database, long loan) {
        return database.queryForObject("SELECT count(*) FROM fines WHERE loan_id = ?", Long.class, loan);
    }

    private long audits() {
        return jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_type = 'FINE'", Long.class);
    }

    private long insertFine(JdbcTemplate database, FineCreationFixture fixture) {
        return database.queryForObject("INSERT INTO fines(user_id, loan_id, copy_id, reason, amount) VALUES (?, ?, ?, 'OTHER', 10) RETURNING id",
                Long.class, fixture.reader(), fixture.loan(), fixture.copy());
    }

    private void migrate(String schema, String target) {
        var configuration = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema).locations("classpath:database/migration");
        if (target != null) {
            configuration.target(target);
        }
        configuration.load().migrate();
    }

    private JdbcTemplate legacy(String schema) {
        String url = POSTGRES.getJdbcUrl();
        url += (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;
        return new JdbcTemplate(new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    private FineCreationFixture fixture(JdbcTemplate database) {
        database.update("INSERT INTO roles(code, name) VALUES ('LIBRARIAN', 'Librarian') ON CONFLICT (code) DO NOTHING");
        long reader = user(database);
        long staff = user(database);
        database.update("INSERT INTO user_roles(user_id, role_id) SELECT ?, id FROM roles WHERE code = 'LIBRARIAN'", staff);
        long library = database.queryForObject("INSERT INTO libraries(code, name) VALUES (?, 'Fine test') RETURNING id",
                Long.class, "fine-test-" + SEQUENCE.incrementAndGet());
        long branch = database.queryForObject("""
                INSERT INTO branches(library_id, name, address)
                VALUES (?, 'Fine test', '{"city":"Test","street":"Main","building":"1"}') RETURNING id
                """, Long.class, library);
        long material = database.queryForObject("INSERT INTO materials(title) VALUES ('Fine test') RETURNING id", Long.class);
        long copy = database.queryForObject("""
                INSERT INTO material_copies(material_id, branch_id, inventory_number) VALUES (?, ?, ?) RETURNING id
                """, Long.class, material, branch, "fine-test-" + SEQUENCE.incrementAndGet());
        long loan = database.queryForObject("""
                INSERT INTO loans(user_id, copy_id, branch_id, issued_by_user_id, loaned_at, due_at)
                VALUES (?, ?, ?, ?, timezone('UTC', clock_timestamp()), timezone('UTC', clock_timestamp()) + INTERVAL '14 days')
                RETURNING id
                """, Long.class, reader, copy, branch, staff);
        return new FineCreationFixture(reader, staff, loan, copy);
    }

    private long user(JdbcTemplate database) {
        long number = SEQUENCE.incrementAndGet();
        return database.queryForObject("""
                INSERT INTO users(email, phone, password_hash, first_name, last_name, status)
                VALUES (?, ?, 'test-hash', 'Fine', 'User', 'ACTIVE') RETURNING id
                """, Long.class, "fine-test-" + number + "@example.com", "+7908%07d".formatted(number));
    }
}
