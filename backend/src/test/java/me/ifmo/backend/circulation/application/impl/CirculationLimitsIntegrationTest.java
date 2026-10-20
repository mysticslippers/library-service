package me.ifmo.backend.circulation.application.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.ifmo.backend.catalog.mapper.MaterialCopyMapperImpl;
import me.ifmo.backend.catalog.mapper.MaterialMapper;
import me.ifmo.backend.circulation.application.LoanService;
import me.ifmo.backend.circulation.application.ReservationService;
import me.ifmo.backend.circulation.integration.ReservationEventPublisher;
import me.ifmo.backend.circulation.mapper.LoanMapperImpl;
import me.ifmo.backend.circulation.mapper.ReservationMapperImpl;
import me.ifmo.backend.circulation.web.request.CreateLoanRequest;
import me.ifmo.backend.circulation.web.request.CreateReservationRequest;
import me.ifmo.backend.library.mapper.BranchAddressJsonMapper;
import me.ifmo.backend.library.mapper.BranchMapperImpl;
import me.ifmo.backend.library.mapper.LibraryMapperImpl;
import me.ifmo.backend.shared.exceptions.BusinessRuleException;
import me.ifmo.backend.user.mapper.RoleMapperImpl;
import me.ifmo.backend.user.mapper.UserMapperImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false, properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({ReservationServiceImpl.class, LoanServiceImpl.class, ReservationMapperImpl.class, LoanMapperImpl.class,
        UserMapperImpl.class, RoleMapperImpl.class, MaterialCopyMapperImpl.class, BranchMapperImpl.class,
        LibraryMapperImpl.class, BranchAddressJsonMapper.class, CirculationLimitsIntegrationTest.JsonConfiguration.class})
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CirculationLimitsIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final AtomicLong SEQUENCE = new AtomicLong();

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private ReservationService reservations;
    @Autowired private LoanService loans;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    // Only catalog response rendering and event transport are stubbed; circulation uses real JPA and triggers.
    @MockitoBean private MaterialMapper materialMapper;
    @MockitoBean private ReservationEventPublisher eventPublisher;

    @Test
    void concurrentReservationsRespectTheReadersLimitAcrossBranchesAndStaff() throws Exception {
        Fixture fixture = fixture(1, 3);
        Copy first = book(fixture.firstBranch());
        Copy second = book(fixture.secondBranch());

        RuntimeException rejected = overlap(
                () -> reserve(fixture.firstStaff(), fixture.reader(), first),
                () -> reserve(fixture.secondStaff(), fixture.reader(), second), false);

        assertThat(rejected).isInstanceOf(BusinessRuleException.class)
                .hasMessage("User has reached active reservation limit");
        assertThat(activeReservations(fixture.reader())).isEqualTo(1);
        assertCopy(first, "RESERVED");
        assertCopy(second, "AVAILABLE");
    }

    @Test
    void concurrentReservationsCannotDuplicateAMaterialUsingDifferentCopies() throws Exception {
        Fixture fixture = fixture(2, 3);
        Copy first = book(fixture.firstBranch());
        Copy second = copy(first.materialId(), fixture.secondBranch());

        RuntimeException rejected = overlap(
                () -> reserve(fixture.firstStaff(), fixture.reader(), first),
                () -> reserve(fixture.secondStaff(), fixture.reader(), second), false);

        assertThat(rejected).isInstanceOf(BusinessRuleException.class)
                .hasMessage("User already has active reservation for this material");
        assertThat(activeReservations(fixture.reader())).isEqualTo(1);
        assertCopy(first, "RESERVED");
        assertCopy(second, "AVAILABLE");
    }

    @Test
    void rolledBackReservationReleasesTheLockAndDoesNotConsumeCapacity() throws Exception {
        Fixture fixture = fixture(1, 3);
        Copy first = book(fixture.firstBranch());
        Copy second = book(fixture.secondBranch());

        RuntimeException rejected = overlap(
                () -> reserve(fixture.firstStaff(), fixture.reader(), first),
                () -> reserve(fixture.secondStaff(), fixture.reader(), second), true);

        assertThat(rejected).isNull();
        assertThat(activeReservations(fixture.reader())).isEqualTo(1);
        assertCopy(first, "AVAILABLE");
        assertCopy(second, "RESERVED");
    }

    @Test
    void concurrentLoansRespectTheReadersLimitAcrossBranchesAndStaff() throws Exception {
        Fixture fixture = fixture(3, 1);
        Copy first = book(fixture.firstBranch());
        Copy second = book(fixture.secondBranch());

        RuntimeException rejected = overlap(
                () -> issue(fixture.firstStaff(), fixture.reader(), first),
                () -> issue(fixture.secondStaff(), fixture.reader(), second), false);

        assertThat(rejected).isInstanceOf(BusinessRuleException.class)
                .hasMessage("User has reached active loan limit");
        assertThat(activeLoans(fixture.reader())).isEqualTo(1);
        assertCopy(first, "LOANED");
        assertCopy(second, "AVAILABLE");
    }

    @Test
    void rolledBackLoanReleasesTheLockAndDoesNotConsumeCapacity() throws Exception {
        Fixture fixture = fixture(3, 1);
        Copy first = book(fixture.firstBranch());
        Copy second = book(fixture.secondBranch());

        RuntimeException rejected = overlap(
                () -> issue(fixture.firstStaff(), fixture.reader(), first),
                () -> issue(fixture.secondStaff(), fixture.reader(), second), true);

        assertThat(rejected).isNull();
        assertThat(activeLoans(fixture.reader())).isEqualTo(1);
        assertCopy(first, "AVAILABLE");
        assertCopy(second, "LOANED");
    }

    @Test
    void differentReadersCanReserveWhileAnotherReadersTransactionIsOpen() throws Exception {
        Fixture fixture = fixture(1, 3);
        Long otherReader = user();
        Copy first = book(fixture.firstBranch());
        Copy second = book(fixture.secondBranch());

        RuntimeException rejected = overlap(
                () -> reserve(fixture.firstStaff(), fixture.reader(), first),
                () -> reserve(fixture.firstStaff(), otherReader, second), false, false);

        assertThat(rejected).isNull();
        assertThat(activeReservations(fixture.reader())).isEqualTo(1);
        assertThat(activeReservations(otherReader)).isEqualTo(1);
    }

    private RuntimeException overlap(Supplier<?> firstRequest, Supplier<?> secondRequest, boolean rollbackFirst)
            throws Exception {
        return overlap(firstRequest, secondRequest, rollbackFirst, true);
    }

    private RuntimeException overlap(Supplier<?> firstRequest, Supplier<?> secondRequest,
                                     boolean rollbackFirst, boolean expectBlocking) throws Exception {
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CompletableFuture<Integer> firstPid = new CompletableFuture<>();
        CompletableFuture<Integer> secondPid = new CompletableFuture<>();
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<RuntimeException> first = executor.submit(() -> {
                try {
                    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                        firstRequest.get();
                        // Keep the real insert uncommitted so an unlocked count would miss it.
                        firstPid.complete(backendPid());
                        awaitRelease(releaseFirst);
                        if (rollbackFirst)
                            status.setRollbackOnly();
                    });
                    return null;
                } catch (RuntimeException exception) {
                    firstPid.completeExceptionally(exception);
                    return exception;
                }
            });
            int holder = firstPid.get(15, TimeUnit.SECONDS);
            Future<RuntimeException> second = executor.submit(() -> {
                try {
                    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                        secondPid.complete(backendPid());
                        secondRequest.get();
                    });
                    return null;
                } catch (RuntimeException exception) {
                    secondPid.completeExceptionally(exception);
                    return exception;
                }
            });
            if (expectBlocking)
                awaitBlockedBy(secondPid.get(15, TimeUnit.SECONDS), holder, second);
            else
                assertThat(second.get(5, TimeUnit.SECONDS)).isNull();
            releaseFirst.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS)).isNull();
            return second.get(15, TimeUnit.SECONDS);
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void awaitBlockedBy(int waitingPid, int holdingPid, Future<?> second) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        boolean blocked = false;
        while (!second.isDone() && System.nanoTime() < deadline) {
            blocked = Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT ? = ANY(pg_blocking_pids(?))", Boolean.class, holdingPid, waitingPid));
            if (blocked)
                break;
            Thread.sleep(20);
        }
        assertThat(blocked).as("Second request waits for the first reader transaction in PostgreSQL").isTrue();
    }

    private int backendPid() {
        return jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
    }

    private void awaitRelease(CountDownLatch release) {
        try {
            if (!release.await(20, TimeUnit.SECONDS))
                throw new IllegalStateException("Timed out holding the first transaction open");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private Object reserve(Long staff, Long reader, Copy copy) {
        return reservations.create(staff, new CreateReservationRequest(reader, copy.materialId(), copy.id(), copy.branchId()));
    }

    private Object issue(Long staff, Long reader, Copy copy) {
        return loans.create(staff, new CreateLoanRequest(reader, copy.id(), null, copy.branchId(), staff, null));
    }

    private long activeReservations(Long reader) {
        return jdbc.queryForObject("SELECT count(*) FROM reservations WHERE user_id = ? AND status IN ('ACTIVE', 'READY_FOR_PICKUP')",
                Long.class, reader);
    }

    private long activeLoans(Long reader) {
        return jdbc.queryForObject("SELECT count(*) FROM loans WHERE user_id = ? AND status IN ('ACTIVE', 'OVERDUE', 'LOST')",
                Long.class, reader);
    }

    private void assertCopy(Copy copy, String status) {
        assertThat(jdbc.queryForObject("SELECT status::text FROM material_copies WHERE id = ?", String.class, copy.id()))
                .isEqualTo(status);
    }

    private Fixture fixture(int reservationLimit, int loanLimit) {
        Long library = jdbc.queryForObject("INSERT INTO libraries (code, name) VALUES (?, 'Limits test') RETURNING id",
                Long.class, "limits-" + SEQUENCE.incrementAndGet());
        Long firstBranch = branch(library, reservationLimit, loanLimit);
        Long secondBranch = branch(library, reservationLimit, loanLimit);
        Long reader = user();
        Long firstStaff = user();
        Long secondStaff = user();
        for (Long staff : new Long[]{firstStaff, secondStaff})
            jdbc.update("INSERT INTO user_roles (user_id, role_id) SELECT ?, id FROM roles WHERE code = 'LIBRARIAN'", staff);
        return new Fixture(reader, firstStaff, secondStaff, firstBranch, secondBranch);
    }

    private Long branch(Long library, int reservationLimit, int loanLimit) {
        Long branch = jdbc.queryForObject("""
                INSERT INTO branches (library_id, name, address)
                VALUES (?, ?, '{"city":"Test","street":"Test","building":"1"}'::jsonb) RETURNING id
                """, Long.class, library, "Limits branch " + SEQUENCE.incrementAndGet());
        jdbc.update("""
                INSERT INTO library_rules (branch_id, max_active_reservations, max_active_loans, valid_from)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP - INTERVAL '1 day')
                """, branch, reservationLimit, loanLimit);
        return branch;
    }

    private Long user() {
        long sequence = SEQUENCE.incrementAndGet();
        return jdbc.queryForObject("""
                INSERT INTO users (email, phone, password_hash, first_name, last_name, status)
                VALUES (?, ?, 'test-hash', 'Test', 'User', 'ACTIVE') RETURNING id
                """, Long.class, "limits" + sequence + "@example.com", "+7900%07d".formatted(sequence));
    }

    private Copy book(Long branch) {
        Long material = jdbc.queryForObject("INSERT INTO materials (title) VALUES ('Limits book') RETURNING id", Long.class);
        return copy(material, branch);
    }

    private Copy copy(Long material, Long branch) {
        Long id = jdbc.queryForObject("""
                INSERT INTO material_copies (material_id, branch_id, inventory_number) VALUES (?, ?, ?) RETURNING id
                """, Long.class, material, branch, "limits-copy-" + SEQUENCE.incrementAndGet());
        return new Copy(id, material, branch);
    }

    private record Fixture(Long reader, Long firstStaff, Long secondStaff, Long firstBranch, Long secondBranch) {}
    private record Copy(Long id, Long materialId, Long branchId) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class JsonConfiguration {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
