package me.ifmo.backend.shared.scheduling;

import jakarta.persistence.EntityManager;
import me.ifmo.backend.authentication.application.AuthService;
import me.ifmo.backend.authentication.persistence.RegistrationStore;
import me.ifmo.backend.authentication.security.CustomUserDetailsService;
import me.ifmo.backend.authentication.security.ratelimit.AuthRequestLimiter;
import me.ifmo.backend.authentication.web.request.LoginRequest;
import me.ifmo.backend.catalog.mapper.MaterialMapper;
import me.ifmo.backend.catalog.persistence.MaterialCopyRepository;
import me.ifmo.backend.circulation.application.impl.LoanServiceImpl;
import me.ifmo.backend.circulation.application.impl.ReservationServiceImpl;
import me.ifmo.backend.circulation.integration.ReservationEventPublisher;
import me.ifmo.backend.circulation.web.request.CancelReservationRequest;
import me.ifmo.backend.circulation.web.request.CreateLoanRequest;
import me.ifmo.backend.circulation.web.request.CreateReservationRequest;
import me.ifmo.backend.circulation.web.request.RenewLoanRequest;
import me.ifmo.backend.circulation.web.request.ReturnLoanRequest;
import me.ifmo.backend.notification.application.NotificationService;
import me.ifmo.backend.notification.domain.enums.NotificationType;
import me.ifmo.backend.notification.web.request.CreateNotificationRequest;
import me.ifmo.backend.shared.exceptions.BusinessRuleException;
import me.ifmo.backend.user.application.UserBlockExpiration;
import me.ifmo.backend.user.application.impl.UserBlockServiceImpl;
import me.ifmo.backend.user.application.impl.UserWarningServiceImpl;
import me.ifmo.backend.user.web.request.CancelUserBlockRequest;
import me.ifmo.backend.user.web.request.CancelUserWarningRequest;
import me.ifmo.backend.user.web.request.CreateUserBlockRequest;
import me.ifmo.backend.user.web.request.CreateUserWarningRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@DataJpaTest(showSql = false, properties = {"spring.jpa.hibernate.ddl-auto=validate",
        "maintenance.deadlines.batch-size=2", "maintenance.deadlines.poll-interval-ms=3600000"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import(DeadlineTestConfiguration.class)
@ActiveProfiles("test")
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DeadlineProcessingIntegrationTest {

    private static final String PASSWORD = "deadline-password1";
    private static final AtomicLong SEQUENCE = new AtomicLong();
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private DeadlineProcessor processor;
    @Autowired private DeadlineScheduler scheduler;
    @Autowired private ScheduledAnnotationBeanPostProcessor scheduledTasks;
    @Autowired private LoanServiceImpl loans;
    @Autowired private ReservationServiceImpl reservations;
    @Autowired private MaterialCopyRepository copies;
    @Autowired private UserBlockServiceImpl blocks;
    @Autowired private UserWarningServiceImpl warnings;
    @Autowired private UserBlockExpiration blockExpiration;
    @Autowired private AuthService auth;
    @Autowired private CustomUserDetailsService userDetails;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactions;
    @MockitoBean private Clock clock;
    @MockitoBean private MaterialMapper materialMapper;
    @MockitoBean private ReservationEventPublisher events;
    @MockitoBean private AuthRequestLimiter limiter;
    @MockitoBean private RegistrationStore registration;
    @MockitoSpyBean private NotificationService notifications;
    private final AtomicReference<Instant> time = new AtomicReference<>();
    private ZoneId zone;
    private String passwordHash;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        properties.add("security.jwt.secret", () -> Base64.getEncoder().encodeToString(
                "deadline-tests-signing-key-64-bytes-0000000000000000000000000000000".getBytes(StandardCharsets.UTF_8)));
    }

    @BeforeEach
    void reset() {
        jdbc.execute("TRUNCATE libraries, users, materials, notifications, audit_logs, outbox_events RESTART IDENTITY CASCADE");
        zone = ZoneId.systemDefault();
        time.set(Instant.now().plusSeconds(1).truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        when(clock.instant()).thenAnswer(invocation -> time.get());
        when(clock.getZone()).thenAnswer(invocation -> zone);
        passwordHash = encoder.encode(PASSWORD);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void expiredReservationsReleaseTheirCopyAndRepeatedRunsDoNothing(boolean ready) {
        DeadlineFixture f = fixture();
        var reservation = reservations.create(f.reader(), reservationRequest(f, f.copy()));
        if (ready) {
            reservations.markReadyForPickup(f.staff(), reservation.id());
        }
        setTime(reservation.expiresAt().minusNanos(1_000_000));
        assertThat(processor.processBatch()).isZero();
        setTime(reservation.expiresAt());
        assertThat(processor.processBatch()).isEqualTo(1);
        assertThat(state("reservations", reservation.id())).isEqualTo("EXPIRED");
        assertThat(state("material_copies", f.copy())).isEqualTo("AVAILABLE");
        long audits = count("audit_logs");
        assertThat(processor.processBatch()).isZero();
        assertThat(audits).isEqualTo(count("audit_logs"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"UTC", "Europe/Moscow"})
    void loansBecomeOverdueAtTheExactDeadlineAcrossClockZones(String timezone) {
        zone = ZoneId.of(timezone);
        DeadlineFixture f = fixture();
        var loan = loans.create(f.staff(), loanRequest(f, f.copy()));
        setTime(loan.dueAt().minusNanos(1_000_000));
        assertThat(processor.processBatch()).isZero();
        setTime(loan.dueAt());
        assertThat(processor.processBatch()).isEqualTo(1);
        assertThat(state("loans", loan.id())).isEqualTo("OVERDUE");
        assertThat(state("material_copies", f.copy())).isEqualTo("LOANED");
        assertThat(processor.processBatch()).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void anExpiredActiveLoanPreventsNewOperationsBeforeAnyScheduledRun(boolean borrow) {
        DeadlineFixture f = fixture();
        var existing = loans.create(f.staff(), loanRequest(f, f.copy()));
        long otherCopy = copy(f);
        setTime(existing.dueAt());
        long audits = count("audit_logs");
        assertThatThrownBy(() -> {
            if (borrow) {
                loans.create(f.staff(), loanRequest(f, otherCopy));
            } else {
                reservations.create(f.reader(), reservationRequest(f, otherCopy));
            }
        }).isInstanceOf(BusinessRuleException.class).hasMessage("User has overdue or lost loans");
        assertThat(state("loans", existing.id())).isEqualTo("ACTIVE");
        assertThat(state("material_copies", otherCopy)).isEqualTo("AVAILABLE");
        assertThat(count("audit_logs")).isEqualTo(audits);
        assertThat(count("loans")).isEqualTo(1);
        assertThat(count("reservations")).isZero();
    }

    @Test
    void loginExpiresTheBlockWithoutWaitingForTheScheduler() {
        DeadlineFixture f = fixture();
        long block = block(f, now().plusDays(1));
        assertThatThrownBy(() -> login(f)).isInstanceOf(BusinessRuleException.class);
        setTime(now().plusDays(1));
        assertThat(login(f).accessToken()).isNotBlank();
        assertThat(state("user_blocks", block)).isEqualTo("EXPIRED");
        assertThat(state("users", f.reader())).isEqualTo("ACTIVE");
        assertThat(unblockMessages()).isEqualTo(1);
        assertThat(processor.processBatch()).isZero();
        assertThat(unblockMessages()).isEqualTo(1);
    }

    @Test
    void loadingJwtUserDetailsAlsoExpiresADueBlock() {
        DeadlineFixture f = fixture();
        block(f, now().plusDays(1));
        assertThat(userDetails.loadUserByUsername(Long.toString(f.reader())).isEnabled()).isFalse();
        setTime(now().plusDays(1));
        var details = userDetails.loadUserByUsername(Long.toString(f.reader()));
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.isAccountNonLocked()).isTrue();
        assertThat(unblockMessages()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void newOperationsExpireADueBlockForTheLockedReader(boolean borrow) {
        DeadlineFixture f = fixture();
        block(f, now().plusDays(1));
        setTime(now().plusDays(1));
        if (borrow) {
            loans.create(f.staff(), loanRequest(f, f.copy()));
        } else {
            reservations.create(f.reader(), reservationRequest(f, f.copy()));
        }
        assertThat(state("users", f.reader())).isEqualTo("ACTIVE");
        assertThat(unblockMessages()).isEqualTo(1);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {5})
    void permanentAndFutureBlocksRemainEffective(Integer days) {
        DeadlineFixture f = fixture();
        long block = block(f, days == null ? null : now().plusDays(days));
        setTime(now().plusDays(1));
        assertThat(processor.processBatch()).isZero();
        assertThatThrownBy(() -> login(f)).isInstanceOf(BusinessRuleException.class);
        assertThat(state("user_blocks", block)).isEqualTo("ACTIVE");
        assertThat(state("users", f.reader())).isEqualTo("BLOCKED");
        assertThat(unblockMessages()).isZero();
    }

    @Test
    void scheduledProcessingExpiresWarningsAndNeverReactivatesAnArchivedUser() {
        DeadlineFixture f = fixture();
        long block = block(f, now().plusDays(1));
        long warning = warning(f, now().plusDays(1));
        long permanentWarning = warning(f, null);
        jdbc.update("UPDATE users SET status = 'ARCHIVED' WHERE id = ?", f.reader());
        setTime(now().plusDays(1));
        assertThat(scheduledTasks.getScheduledTasks()).hasSize(1);
        scheduler.processDeadlines();
        assertThat(state("users", f.reader())).isEqualTo("ARCHIVED");
        assertThat(state("user_blocks", block)).isEqualTo("EXPIRED");
        assertThat(state("user_warnings", warning)).isEqualTo("EXPIRED");
        assertThat(state("user_warnings", permanentWarning)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT actor_user_id FROM audit_logs WHERE entity_type = 'USER_BLOCK' AND entity_id = ? AND action = 'UNBLOCK'", Long.class, block)).isNull();
    }

    @Test
    void processingIsBoundedPerTypeAndContinuesOnTheNextRun() {
        for (int i = 0; i < 3; i++) {
            DeadlineFixture f = fixture();
            reservations.create(f.reader(), reservationRequest(f, f.copy()));
            warning(f, now().plusDays(1));
        }
        setTime(now().plusDays(1));
        assertThat(processor.processBatch()).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservations WHERE status = 'ACTIVE'", Long.class)).isEqualTo(1);
        assertThat(processor.processBatch()).isEqualTo(2);
        assertThat(processor.processBatch()).isZero();
    }

    @Test
    void aFailedItemRollsBackItsSideEffectsAndDoesNotRollbackOtherItems() {
        DeadlineFixture failed = fixture();
        DeadlineFixture good = fixture();
        long failedBlock = block(failed, now().plusDays(1));
        long goodBlock = block(good, now().plusDays(1));
        doAnswer(invocation -> {
            CreateNotificationRequest request = invocation.getArgument(0);
            if (request.userId().equals(failed.reader()) && request.type() == NotificationType.USER_UNBLOCKED) {
                throw new IllegalStateException("Simulated notification storage failure");
            }
            return invocation.callRealMethod();
        }).when(notifications).create(any());
        setTime(now().plusDays(1));
        assertThat(processor.processBatch()).isEqualTo(1);
        assertThat(state("user_blocks", failedBlock)).isEqualTo("ACTIVE");
        assertThat(state("users", failed.reader())).isEqualTo("BLOCKED");
        assertThat(state("user_blocks", goodBlock)).isEqualTo("EXPIRED");
        assertThat(state("users", good.reader())).isEqualTo("ACTIVE");
        assertThat(unblockMessages()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'UNBLOCK' AND entity_id = ?", Long.class, failedBlock)).isZero();
    }

    @Test
    void twoWorkersCompleteABlockOnlyOnce() throws Exception {
        DeadlineFixture f = fixture();
        long block = block(f, now().plusDays(1));
        setTime(now().plusDays(1));
        assertThat(overlap(() -> blockExpiration.expireDue(block), () -> blockExpiration.expireDue(block), false, () -> {}))
                .isEqualTo(false);
        assertThat(unblockMessages()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_type = 'USER_BLOCK' AND entity_id = ? AND action = 'UNBLOCK'", Long.class, block)).isEqualTo(1);
    }

    @Test
    void aRolledBackWorkerLeavesTheBlockAvailableForRetry() throws Exception {
        DeadlineFixture f = fixture();
        long block = block(f, now().plusDays(1));
        setTime(now().plusDays(1));
        assertThat(overlap(() -> blockExpiration.expireDue(block), () -> blockExpiration.expireDue(block), true, () -> {}))
                .isEqualTo(true);
        assertThat(state("user_blocks", block)).isEqualTo("EXPIRED");
        assertThat(unblockMessages()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"RETURN", "RENEW", "CANCEL_RESERVATION", "ISSUE_RESERVATION", "CANCEL_BLOCK", "CANCEL_WARNING"})
    void waitingWorkersRecheckTheResultOfManualCommands(String command) throws Exception {
        DeadlineFixture f = fixture();
        LocalDateTime deadline = now().plusDays(1);
        Supplier<?> first;
        Supplier<?> second;
        long id;
        switch (command) {
            case "RETURN", "RENEW" -> {
                id = loans.create(f.staff(), loanRequest(f, f.copy())).id();
                first = command.equals("RETURN")
                        ? () -> loans.returnLoan(f.staff(), id, new ReturnLoanRequest(null, null))
                        : () -> loans.renew(f.reader(), id, new RenewLoanRequest(null));
                second = () -> loans.markOverdueIfDue(id);
            }
            case "CANCEL_BLOCK" -> {
                id = block(f, deadline);
                first = () -> blocks.cancel(id, f.admin(), new CancelUserBlockRequest("Manual cancellation"));
                second = () -> blockExpiration.expireDue(id);
            }
            case "CANCEL_WARNING" -> {
                id = warning(f, deadline);
                first = () -> warnings.cancel(f.admin(), id, new CancelUserWarningRequest("Manual cancellation"));
                second = () -> warnings.expireDue(id);
            }
            default -> {
                id = reservations.create(f.reader(), reservationRequest(f, f.copy())).id();
                if (command.equals("ISSUE_RESERVATION")) {
                    reservations.markReadyForPickup(f.staff(), id);
                    first = () -> loans.create(f.staff(), new CreateLoanRequest(f.reader(), f.copy(), id, f.branch(), f.staff(), now().plusDays(7)));
                } else {
                    first = () -> reservations.cancelByUser(f.reader(), id, new CancelReservationRequest("Manual cancellation"));
                }
                second = () -> reservations.expireDue(id);
            }
        }
        assertThat(overlap(first, second, false, () -> setTime(deadline))).isEqualTo(false);
        if (command.equals("RETURN") || command.equals("CANCEL_RESERVATION")) {
            assertThat(state("material_copies", f.copy())).isEqualTo("AVAILABLE");
        }
        if (command.equals("ISSUE_RESERVATION")) {
            assertThat(state("reservations", id)).isEqualTo("USED");
            assertThat(state("material_copies", f.copy())).isEqualTo("LOANED");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void waitingCreatesRecheckOverdueLoansAfterAcquiringTheCopy(boolean borrow) throws Exception {
        DeadlineFixture f = fixture();
        var existing = loans.create(f.staff(), loanRequest(f, f.copy()));
        long otherCopy = copy(f);
        Supplier<?> lockCopy = () -> copies.findByIdForUpdate(otherCopy).orElseThrow().getId();
        Supplier<?> create = borrow
                ? () -> loans.create(f.staff(), loanRequest(f, otherCopy))
                : () -> reservations.create(f.reader(), reservationRequest(f, otherCopy));
        Object result = overlap(lockCopy, create, false, () -> {}, () -> setTime(existing.dueAt()));
        assertThat(result).isInstanceOf(BusinessRuleException.class);
        assertThat(((BusinessRuleException) result).getMessage()).isEqualTo("User has overdue or lost loans");
        assertThat(state("material_copies", otherCopy)).isEqualTo("AVAILABLE");
        assertThat(count("loans")).isEqualTo(1);
        assertThat(count("reservations")).isZero();
    }

    private Object overlap(Supplier<?> first, Supplier<?> second, boolean rollback, Runnable afterFirst) throws Exception {
        return overlap(first, second, rollback, afterFirst, () -> {});
    }

    private Object overlap(Supplier<?> first, Supplier<?> second, boolean rollback,
                           Runnable afterFirst, Runnable afterWaiting) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var firstPid = new CompletableFuture<Integer>();
        var secondPid = new CompletableFuture<Integer>();
        CountDownLatch written = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            var holder = executor.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                firstPid.complete(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                Object result = first.get();
                entityManager.flush();
                written.countDown();
                try {
                    if (!release.await(20, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting for the second transaction");
                    }
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(error);
                }
                if (rollback) {
                    status.setRollbackOnly();
                }
                return result;
            }));
            assertThat(written.await(10, TimeUnit.SECONDS)).isTrue();
            afterFirst.run();
            var waiter = executor.submit(() -> {
                try {
                    return new TransactionTemplate(transactions).execute(status -> {
                        secondPid.complete(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                        return second.get();
                    });
                } catch (RuntimeException error) {
                    return error;
                }
            });
            int holding = firstPid.get(5, TimeUnit.SECONDS);
            int waiting = secondPid.get(5, TimeUnit.SECONDS);
            await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(20))
                    .until(() -> Boolean.TRUE.equals(jdbc.queryForObject("SELECT ? = ANY(pg_blocking_pids(?))", Boolean.class, holding, waiting)));
            afterWaiting.run();
            release.countDown();
            assertThat(holder.get(10, TimeUnit.SECONDS)).isNotNull();
            return waiter.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private DeadlineFixture fixture() {
        long seq = SEQUENCE.incrementAndGet();
        long library = jdbc.queryForObject("INSERT INTO libraries(code, name) VALUES (?, 'Deadline library') RETURNING id", Long.class, "deadline-" + seq);
        long branch = jdbc.queryForObject("INSERT INTO branches(library_id, name, address) VALUES (?, 'Deadline branch', '{\"city\":\"Test\",\"street\":\"Main\",\"building\":\"1\"}') RETURNING id", Long.class, library);
        jdbc.update("INSERT INTO library_rules(branch_id, valid_from, reservation_ttl_days, renewal_period_days) VALUES (?, now() - INTERVAL '2 days', 1, 7)", branch);
        long reader = user("READER");
        long staff = user("LIBRARIAN");
        long admin = user("ADMIN");
        long material = jdbc.queryForObject("INSERT INTO materials(title) VALUES ('Deadline book') RETURNING id", Long.class);
        DeadlineFixture f = new DeadlineFixture(reader, staff, admin, branch, material, 0);
        return new DeadlineFixture(reader, staff, admin, branch, material, copy(f));
    }

    private long user(String role) {
        long seq = SEQUENCE.incrementAndGet();
        long id = jdbc.queryForObject("INSERT INTO users(email, phone, password_hash, first_name, last_name, status, activated_at) VALUES (?, ?, ?, 'Deadline', 'Reader', 'ACTIVE', now()) RETURNING id", Long.class,
                "deadline-" + seq + "@example.com", "+7907%07d".formatted(seq), passwordHash);
        jdbc.update("INSERT INTO user_roles(user_id, role_id) SELECT ?, id FROM roles WHERE code = CAST(? AS role_code)", id, role);
        return id;
    }

    private long copy(DeadlineFixture f) {
        return jdbc.queryForObject("INSERT INTO material_copies(material_id, branch_id, inventory_number) VALUES (?, ?, ?) RETURNING id", Long.class,
                f.material(), f.branch(), "deadline-copy-" + SEQUENCE.incrementAndGet());
    }

    private CreateLoanRequest loanRequest(DeadlineFixture f, long copy) {
        return new CreateLoanRequest(f.reader(), copy, null, f.branch(), f.staff(), now().plusDays(1));
    }

    private CreateReservationRequest reservationRequest(DeadlineFixture f, long copy) {
        return new CreateReservationRequest(f.reader(), f.material(), copy, f.branch());
    }

    private long block(DeadlineFixture f, LocalDateTime expires) {
        return blocks.create(f.admin(), new CreateUserBlockRequest(f.reader(), "Temporary block", expires)).id();
    }

    private long warning(DeadlineFixture f, LocalDateTime expires) {
        return warnings.create(f.admin(), new CreateUserWarningRequest(f.reader(), "Temporary warning", null, expires)).id();
    }

    private me.ifmo.backend.authentication.web.response.AuthResponse login(DeadlineFixture f) {
        String email = jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, f.reader());
        return auth.login(new LoginRequest(email, PASSWORD));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private void setTime(LocalDateTime value) {
        time.set(value.atZone(zone).toInstant());
    }

    private String state(String table, long id) {
        return jdbc.queryForObject("SELECT status::text FROM " + table + " WHERE id = ?", String.class, id);
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private long unblockMessages() {
        return jdbc.queryForObject("SELECT count(*) FROM notifications WHERE type = 'USER_UNBLOCKED'", Long.class);
    }
}
