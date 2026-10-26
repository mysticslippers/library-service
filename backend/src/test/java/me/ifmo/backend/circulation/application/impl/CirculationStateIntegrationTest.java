package me.ifmo.backend.circulation.application.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import me.ifmo.backend.catalog.mapper.MaterialCopyMapperImpl;
import me.ifmo.backend.catalog.mapper.MaterialMapper;
import me.ifmo.backend.circulation.application.CirculationLocks;
import me.ifmo.backend.circulation.application.LoanService;
import me.ifmo.backend.circulation.application.ReservationService;
import me.ifmo.backend.circulation.configuration.CirculationTimeConfiguration;
import me.ifmo.backend.circulation.domain.enums.LoanStatus;
import me.ifmo.backend.circulation.domain.enums.ReservationStatus;
import me.ifmo.backend.circulation.integration.KafkaReservationEventPublisher;
import me.ifmo.backend.circulation.mapper.LoanMapperImpl;
import me.ifmo.backend.circulation.mapper.ReservationMapperImpl;
import me.ifmo.backend.circulation.web.request.CancelReservationRequest;
import me.ifmo.backend.circulation.web.request.CreateLoanRequest;
import me.ifmo.backend.circulation.web.request.RenewLoanRequest;
import me.ifmo.backend.circulation.web.request.ReturnLoanRequest;
import me.ifmo.backend.circulation.web.response.LoanResponse;
import me.ifmo.backend.library.mapper.BranchAddressJsonMapper;
import me.ifmo.backend.library.mapper.BranchMapperImpl;
import me.ifmo.backend.library.mapper.LibraryMapperImpl;
import me.ifmo.backend.shared.exceptions.ApiException;
import me.ifmo.backend.shared.exceptions.BusinessRuleException;
import me.ifmo.backend.shared.messaging.EventEnvelope;
import me.ifmo.backend.shared.messaging.KafkaMessagingProperties;
import me.ifmo.backend.shared.messaging.outbox.OutboxService;
import me.ifmo.backend.user.mapper.RoleMapperImpl;
import me.ifmo.backend.user.mapper.UserMapperImpl;
import me.ifmo.backend.user.domain.enums.RoleCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@DataJpaTest(showSql = false, properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({CirculationTimeConfiguration.class, CirculationLocks.class, LoanServiceImpl.class, ReservationServiceImpl.class,
        LoanMapperImpl.class, ReservationMapperImpl.class, MaterialCopyMapperImpl.class, UserMapperImpl.class,
        RoleMapperImpl.class, BranchMapperImpl.class, LibraryMapperImpl.class, BranchAddressJsonMapper.class,
        KafkaReservationEventPublisher.class, OutboxService.class, CirculationStateIntegrationTest.EventConfiguration.class})
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CirculationStateIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final AtomicLong SEQUENCE = new AtomicLong();
    @Autowired private LoanService loans;
    @Autowired private ReservationService reservations;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private EntityManager entityManager;
    @MockitoBean private MaterialMapper materialMapper;
    @MockitoBean private Clock clock;
    @MockitoSpyBean private ObjectMapper objectMapper;
    private final AtomicReference<Instant> time = new AtomicReference<>();

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void freezeTime() {
        time.set(Instant.now().plusSeconds(300).truncatedTo(ChronoUnit.MILLIS));
        when(clock.instant()).thenAnswer(invocation -> time.get());
        when(clock.getZone()).thenReturn(ZoneId.systemDefault());
    }

    static Stream<Arguments> loanCommands() {
        return Stream.of("RENEW", "OVERDUE", "LOST", "RETURN")
                .flatMap(command -> Arrays.stream(LoanStatus.values()).map(state -> Arguments.of(command, state)));
    }

    @ParameterizedTest
    @MethodSource("loanCommands")
    void serviceLoanCommandsValidateEverySourceState(String command, LoanStatus state) {
        Fixture f = fixture();
        long id = loan(f, state.name(), command.equals("OVERDUE") ? dbNow() : dbNow().plusDays(7), 0);
        Map<String, Object> before = loanRow(id);
        String copyBefore = copyStatus(f);
        boolean allowed = switch (command) {
            case "RENEW", "OVERDUE" -> state == LoanStatus.ACTIVE;
            case "LOST" -> state == LoanStatus.ACTIVE || state == LoanStatus.OVERDUE;
            default -> Set.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE, LoanStatus.LOST).contains(state);
        };

        if (allowed) {
            loanCommand(command, f, id);
            String expected = command.equals("RENEW") ? "ACTIVE" : command.equals("RETURN") ? "RETURNED" : command;
            assertThat(loanStatus(id)).isEqualTo(expected);
            assertThat(copyStatus(f)).isEqualTo(expected.equals("RETURNED") ? "AVAILABLE" : expected.equals("LOST") ? "LOST" : "LOANED");
            assertThat(loanRow(id).get("renewal_count")).isEqualTo(command.equals("RENEW") ? 1 : 0);
        } else {
            assertThatThrownBy(() -> loanCommand(command, f, id)).isInstanceOf(ApiException.class);
            assertThat(loanRow(id)).isEqualTo(before);
            assertThat(copyStatus(f)).isEqualTo(copyBefore);
        }
    }

    static Stream<Arguments> renewalPeriods() {
        return Stream.of(RoleCode.READER, RoleCode.LIBRARIAN, RoleCode.ADMIN)
                .flatMap(role -> Stream.<Integer>of(null, 1, 7, 8, 36500, Integer.MAX_VALUE, 0, -1)
                        .map(days -> Arguments.of(role, days)));
    }

    @ParameterizedTest
    @MethodSource("renewalPeriods")
    void renewalPeriodIsBoundedByTheRuleForEveryRole(RoleCode role, Integer days) {
        Fixture f = fixture();
        long actor = role == RoleCode.READER ? f.reader() : f.staff();
        jdbc.update("DELETE FROM user_roles WHERE user_id = ?", actor);
        jdbc.update("INSERT INTO user_roles(user_id, role_id) SELECT ?, id FROM roles WHERE code::text = ?", actor, role.name());
        long id = loan(f, "ACTIVE", dbNow().plusDays(7), 0);
        LocalDateTime originalDue = loans.getLoanById(f.reader(), id).dueAt();
        Map<String, Object> before = loanRow(id), copy = copyRow(f);
        long audit = audits();

        if (days == null || days > 0 && days <= 7) {
            LoanResponse renewed = loans.renew(actor, id, new RenewLoanRequest(days));
            assertThat(renewed.dueAt()).isEqualTo(originalDue.plusDays(days == null ? 7 : days));
            assertThat(renewed.renewalCount()).isEqualTo(1);
            assertThat(loanRow(id).get("renewal_count")).isEqualTo(1);
        } else {
            assertThatThrownBy(() -> loans.renew(actor, id, new RenewLoanRequest(days)))
                    .isInstanceOf(BusinessRuleException.class).hasMessage("Renewal days must be between 1 and 7");
            assertThat(loanRow(id)).isEqualTo(before);
            assertThat(audits()).isEqualTo(audit);
        }
        assertThat(copyRow(f)).isEqualTo(copy);
    }

    @Test
    void renewalUsesTheCurrentRuleOfTheLoansBranchForDefaultAndMaximum() {
        Fixture shortPeriod = fixture(), longPeriod = fixture();
        jdbc.update("UPDATE library_rules SET renewal_period_days = 3 WHERE branch_id = ?", shortPeriod.branch());
        jdbc.update("UPDATE library_rules SET renewal_period_days = 10 WHERE branch_id = ?", longPeriod.branch());
        long shortLoan = loan(shortPeriod, "ACTIVE", dbNow().plusDays(7), 0);
        long longLoan = loan(longPeriod, "ACTIVE", dbNow().plusDays(7), 0);
        LocalDateTime shortDue = loans.getLoanById(shortPeriod.reader(), shortLoan).dueAt();
        LocalDateTime longDue = loans.getLoanById(longPeriod.reader(), longLoan).dueAt();

        assertThatThrownBy(() -> loans.renew(shortPeriod.reader(), shortLoan, new RenewLoanRequest(7)))
                .isInstanceOf(BusinessRuleException.class).hasMessage("Renewal days must be between 1 and 3");
        assertThat(loans.renew(shortPeriod.reader(), shortLoan, new RenewLoanRequest(null)).dueAt()).isEqualTo(shortDue.plusDays(3));
        assertThat(loans.renew(longPeriod.reader(), longLoan, new RenewLoanRequest(7)).dueAt()).isEqualTo(longDue.plusDays(7));

        // Change the current rule between commands: the next default must use it immediately.
        jdbc.update("UPDATE library_rules SET renewal_period_days = 4 WHERE branch_id = ?", longPeriod.branch());
        LoanResponse renewed = loans.renew(longPeriod.reader(), longLoan, new RenewLoanRequest(null));
        assertThat(renewed.dueAt()).isEqualTo(longDue.plusDays(11));
        assertThat(renewed.renewalCount()).isEqualTo(2);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {1, 8})
    void anotherReaderCannotRenewRegardlessOfTheRequestedPeriod(Integer days) {
        Fixture f = fixture();
        long otherReader = user();
        jdbc.update("INSERT INTO user_roles(user_id, role_id) SELECT ?, id FROM roles WHERE code = 'READER'", otherReader);
        long id = loan(f, "ACTIVE", dbNow().plusDays(7), 0);
        Map<String, Object> before = loanRow(id), copy = copyRow(f);
        long audit = audits();
        assertThatThrownBy(() -> loans.renew(otherReader, id, new RenewLoanRequest(days))).isInstanceOf(AccessDeniedException.class);
        assertThat(loanRow(id)).isEqualTo(before);
        assertThat(copyRow(f)).isEqualTo(copy);
        assertThat(audits()).isEqualTo(audit);
    }

    static Stream<Arguments> reservationCommands() {
        return Stream.of("READY", "CANCEL_USER", "CANCEL_STAFF", "EXPIRE", "ISSUE")
                .flatMap(command -> Arrays.stream(ReservationStatus.values()).map(state -> Arguments.of(command, state)));
    }

    @ParameterizedTest
    @MethodSource("reservationCommands")
    void serviceReservationCommandsValidateEverySourceState(String command, ReservationStatus state) {
        Fixture f = fixture();
        long id = reservation(f, state.name(), command.equals("EXPIRE") ? dbNow() : dbNow().plusDays(1));
        Map<String, Object> before = reservationRow(id);
        String copyBefore = copyStatus(f);
        boolean allowed = switch (command) {
            case "READY" -> state == ReservationStatus.ACTIVE;
            case "ISSUE" -> state == ReservationStatus.READY_FOR_PICKUP;
            default -> state == ReservationStatus.ACTIVE || state == ReservationStatus.READY_FOR_PICKUP;
        };

        if (allowed) {
            reservationCommand(command, f, id);
            String expected = switch (command) {
                case "READY" -> "READY_FOR_PICKUP";
                case "ISSUE" -> "USED";
                case "EXPIRE" -> "EXPIRED";
                case "CANCEL_USER" -> "CANCELLED_BY_USER";
                default -> "CANCELLED_BY_LIBRARIAN";
            };
            assertThat(reservationStatus(id)).isEqualTo(expected);
            assertThat(copyStatus(f)).isEqualTo(command.equals("READY") ? "RESERVED" : command.equals("ISSUE") ? "LOANED" : "AVAILABLE");
        } else {
            assertThatThrownBy(() -> reservationCommand(command, f, id)).isInstanceOf(ApiException.class);
            assertThat(reservationRow(id)).isEqualTo(before);
            assertThat(copyStatus(f)).isEqualTo(copyBefore);
        }
        assertThat(events(id)).isEqualTo(allowed && command.equals("READY") ? 1 : 0);
    }

    static Stream<Arguments> loanTransitions() {
        return Arrays.stream(LoanStatus.values()).flatMap(from -> Arrays.stream(LoanStatus.values()).map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest
    @MethodSource("loanTransitions")
    void databaseValidatesEveryLoanStatusPair(LoanStatus from, LoanStatus to) {
        Fixture f = fixture();
        long id = loan(f, from.name(), dbNow().plusDays(7), 0);
        Map<String, Object> before = loanRow(id);
        String copyBefore = copyStatus(f);
        boolean allowed = from == to || switch (from) {
            case ACTIVE -> Set.of(LoanStatus.OVERDUE, LoanStatus.LOST, LoanStatus.RETURNED, LoanStatus.CANCELLED).contains(to);
            case OVERDUE -> Set.of(LoanStatus.LOST, LoanStatus.RETURNED, LoanStatus.CANCELLED).contains(to);
            case LOST -> to == LoanStatus.RETURNED;
            default -> false;
        };
        Runnable update = () -> jdbc.update("""
                UPDATE loans SET status = CAST(? AS loan_status),
                    returned_at = CASE WHEN ? = 'RETURNED' THEN COALESCE(returned_at, ?) ELSE returned_at END WHERE id = ?
                """, to.name(), to.name(), dbNow(), id);

        if (allowed) {
            update.run();
            assertThat(loanStatus(id)).isEqualTo(to.name());
            assertThat(copyStatus(f)).isEqualTo(to == LoanStatus.LOST ? "LOST"
                    : to == LoanStatus.ACTIVE || to == LoanStatus.OVERDUE ? "LOANED" : "AVAILABLE");
        } else {
            assertThatThrownBy(update::run).isInstanceOf(DataIntegrityViolationException.class);
            assertThat(loanRow(id)).isEqualTo(before);
            assertThat(copyStatus(f)).isEqualTo(copyBefore);
        }
    }

    static Stream<Arguments> reservationTransitions() {
        return Arrays.stream(ReservationStatus.values()).flatMap(from -> Arrays.stream(ReservationStatus.values()).map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest
    @MethodSource("reservationTransitions")
    void databaseValidatesEveryReservationStatusPair(ReservationStatus from, ReservationStatus to) {
        Fixture f = fixture();
        long id = reservation(f, from.name(), dbNow().plusDays(1));
        Map<String, Object> before = reservationRow(id);
        String copyBefore = copyStatus(f);
        boolean allowed = from == to || switch (from) {
            case ACTIVE -> Set.of(ReservationStatus.READY_FOR_PICKUP, ReservationStatus.EXPIRED,
                    ReservationStatus.CANCELLED_BY_USER, ReservationStatus.CANCELLED_BY_LIBRARIAN).contains(to);
            case READY_FOR_PICKUP -> Set.of(ReservationStatus.USED, ReservationStatus.EXPIRED,
                    ReservationStatus.CANCELLED_BY_USER, ReservationStatus.CANCELLED_BY_LIBRARIAN).contains(to);
            default -> false;
        };
        Runnable update = () -> {
            if (from == ReservationStatus.READY_FOR_PICKUP && to == ReservationStatus.USED) {
                jdbc.update("""
                        INSERT INTO loans(user_id, copy_id, reservation_id, branch_id, issued_by_user_id, loaned_at, due_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """, f.reader(), f.copy(), id, f.branch(), f.staff(), dbNow(), dbNow().plusDays(7));
            } else {
                jdbc.update("""
                        UPDATE reservations SET status = CAST(? AS reservation_status),
                            ready_at = CASE WHEN ? = 'READY_FOR_PICKUP' THEN COALESCE(ready_at, ?) ELSE ready_at END,
                            cancelled_at = CASE WHEN ? LIKE 'CANCELLED%' THEN COALESCE(cancelled_at, ?) ELSE cancelled_at END,
                            cancellation_reason = CASE WHEN ? LIKE 'CANCELLED%' THEN 'matrix' ELSE cancellation_reason END
                        WHERE id = ?
                        """, to.name(), to.name(), dbNow(), to.name(), dbNow(), to.name(), id);
            }
        };

        if (allowed) {
            update.run();
            assertThat(reservationStatus(id)).isEqualTo(to.name());
            assertThat(copyStatus(f)).isEqualTo(from == ReservationStatus.READY_FOR_PICKUP && to == ReservationStatus.USED ? "LOANED"
                    : to == ReservationStatus.ACTIVE || to == ReservationStatus.READY_FOR_PICKUP ? "RESERVED" : "AVAILABLE");
        } else {
            assertThatThrownBy(update::run).isInstanceOf(DataIntegrityViolationException.class);
            assertThat(reservationRow(id)).isEqualTo(before);
            assertThat(copyStatus(f)).isEqualTo(copyBefore);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "user_id", "copy_id", "reservation_id", "branch_id", "issued_by_user_id", "loaned_at"})
    void databaseRejectsLoanRebinding(String field) {
        Fixture f = fixture(), other = fixture();
        long id = loan(f, "ACTIVE", dbNow().plusDays(7), 0);
        Object value = switch (field) {
            case "id" -> id + 10_000_000;
            case "user_id" -> other.reader();
            case "copy_id" -> other.copy();
            case "reservation_id" -> reservation(other, "ACTIVE", dbNow().plusDays(1));
            case "branch_id" -> other.branch();
            case "issued_by_user_id" -> other.staff();
            default -> dbNow().minusDays(1);
        };
        Map<String, Object> before = loanRow(id);
        assertThatThrownBy(() -> jdbc.update("UPDATE loans SET " + field + " = ? WHERE id = ?", value, id))
                .isInstanceOf(DataIntegrityViolationException.class).rootCause().hasMessageContaining("Loan identity and origin");
        assertThat(loanRow(id)).isEqualTo(before);
        assertThat(copyStatus(f)).isEqualTo("LOANED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "user_id", "material_id", "copy_id", "branch_id"})
    void databaseRejectsReservationRebinding(String field) {
        Fixture f = fixture(), other = fixture();
        long id = reservation(f, "ACTIVE", dbNow().plusDays(1));
        long value = switch (field) {
            case "id" -> id + 10_000_000;
            case "user_id" -> other.reader();
            case "material_id" -> other.material();
            case "copy_id" -> other.copy();
            default -> other.branch();
        };
        Map<String, Object> before = reservationRow(id);
        assertThatThrownBy(() -> jdbc.update("UPDATE reservations SET " + field + " = ? WHERE id = ?", value, id))
                .isInstanceOf(DataIntegrityViolationException.class).rootCause().hasMessageContaining("Reservation identity");
        assertThat(reservationRow(id)).isEqualTo(before);
        assertThat(copyStatus(f)).isEqualTo("RESERVED");
    }

    @Test
    void databaseRejectsUsingAReservationWithoutItsLoan() {
        Fixture f = fixture();
        long id = reservation(f, "READY_FOR_PICKUP", dbNow().plusDays(1));
        assertThatThrownBy(() -> jdbc.update("UPDATE reservations SET status = 'USED' WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class).rootCause().hasMessageContaining("matching loan");
        assertThat(reservationStatus(id)).isEqualTo("READY_FOR_PICKUP");
        assertThat(copyStatus(f)).isEqualTo("RESERVED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"due_at = due_at - INTERVAL '1 day', renewal_count = renewal_count + 1",
            "due_at = due_at + INTERVAL '1 day'", "renewal_count = renewal_count + 1",
            "due_at = due_at + INTERVAL '1 day', renewal_count = renewal_count - 1"})
    void databaseRejectsInconsistentRenewal(String assignment) {
        Fixture f = fixture();
        long id = loan(f, "ACTIVE", dbNow().plusDays(7), 1);
        Map<String, Object> before = loanRow(id);
        assertThatThrownBy(() -> jdbc.update("UPDATE loans SET " + assignment + " WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class).rootCause().hasMessageContaining("Renewal must extend");
        assertThat(loanRow(id)).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"RETURNED", "CANCELLED"})
    void databasePreservesClosedLoanMetadata(String status) {
        Fixture f = fixture();
        long id = loan(f, status, dbNow().plusDays(7), 0);
        Map<String, Object> before = loanRow(id);
        assertThatThrownBy(() -> jdbc.update("UPDATE loans SET due_at = due_at + INTERVAL '1 day', renewal_count = renewal_count + 1 WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class).rootCause().hasMessageContaining("Closed loan");
        assertThat(loanRow(id)).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"USED", "EXPIRED", "CANCELLED_BY_USER", "CANCELLED_BY_LIBRARIAN"})
    void databasePreservesClosedReservationMetadata(String status) {
        Fixture f = fixture();
        long id = reservation(f, status, dbNow().plusDays(1));
        Map<String, Object> before = reservationRow(id);
        assertThatThrownBy(() -> jdbc.update("UPDATE reservations SET expires_at = expires_at + INTERVAL '1 day' WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class).rootCause().hasMessageContaining("Closed reservation");
        assertThat(reservationRow(id)).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"OVERDUE", "LOST"})
    void databaseRejectsRenewalOfANonActiveLoan(String status) {
        Fixture f = fixture();
        long id = loan(f, status, dbNow().plusDays(7), 0);
        Map<String, Object> before = loanRow(id);
        assertThatThrownBy(() -> jdbc.update("UPDATE loans SET due_at = due_at + INTERVAL '1 day', renewal_count = renewal_count + 1 WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class).rootCause().hasMessageContaining("Renewal must extend");
        assertThat(loanRow(id)).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"READY_FOR_PICKUP", "CANCELLED_BY_USER"})
    void databaseRequiresTransitionTimestamps(String status) {
        Fixture f = fixture();
        long id = reservation(f, "ACTIVE", dbNow().plusDays(1));
        Map<String, Object> before = reservationRow(id);
        assertThatThrownBy(() -> jdbc.update("""
                UPDATE reservations SET status = CAST(? AS reservation_status),
                    cancellation_reason = CASE WHEN ? = 'CANCELLED_BY_USER' THEN 'matrix' ELSE NULL END WHERE id = ?
                """, status, status, id)).isInstanceOf(DataIntegrityViolationException.class)
                .rootCause().hasMessageContaining("timestamp");
        assertThat(reservationRow(id)).isEqualTo(before);
    }

    @Test
    void databaseRejectsReturnTimestampWithoutReturn() {
        Fixture f = fixture();
        long id = loan(f, "ACTIVE", dbNow().plusDays(7), 0);
        assertThatThrownBy(() -> jdbc.update("UPDATE loans SET returned_at = ? WHERE id = ?", dbNow(), id))
                .isInstanceOf(DataIntegrityViolationException.class).rootCause().hasMessageContaining("Return timestamp");
        assertThat(loanRow(id).get("returned_at")).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void renewalAndLossRecheckStateAfterWaiting(boolean renewFirst) throws Exception {
        Fixture f = fixture();
        long id = loan(f, "ACTIVE", dbNow().plusDays(7), 0);
        Supplier<?> renew = () -> loanCommand("RENEW", f, id), lost = () -> loanCommand("LOST", f, id);
        Outcomes result = overlap(renewFirst ? renew : lost, renewFirst ? lost : renew, false);
        success(result.first());
        if (renewFirst) success(result.second());
        else rejected(result.second());
        assertThat(loanStatus(id)).isEqualTo("LOST");
        assertThat(copyStatus(f)).isEqualTo("LOST");
        assertThat(loanRow(id).get("renewal_count")).isEqualTo(renewFirst ? 1 : 0);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void overdueChecksTheRenewedDeadlineAfterWaiting(boolean renewFirst) throws Exception {
        Fixture f = fixture();
        LocalDateTime originalDue = renewFirst ? dbNow().plusHours(1) : dbNow();
        long id = loan(f, "ACTIVE", originalDue, 0);
        Supplier<?> renew = () -> {
            Object response = loanCommand("RENEW", f, id);
            time.updateAndGet(instant -> instant.plusSeconds(3601));
            return response;
        };
        Supplier<?> overdue = () -> loanCommand("OVERDUE", f, id);
        Outcomes result = overlap(renewFirst ? renew : overdue, renewFirst ? overdue : renew, false);
        success(result.first());
        rejected(result.second());
        assertThat(loanStatus(id)).isEqualTo(renewFirst ? "ACTIVE" : "OVERDUE");
        assertThat(loanRow(id).get("renewal_count")).isEqualTo(renewFirst ? 1 : 0);
        assertThat(copyStatus(f)).isEqualTo("LOANED");
    }

    @Test
    void concurrentRenewalsRespectTheLastAllowedRenewal() throws Exception {
        Fixture f = fixture();
        long id = loan(f, "ACTIVE", dbNow().plusDays(7), 1);
        LocalDateTime due = jdbc.queryForObject("SELECT due_at FROM loans WHERE id = ?", LocalDateTime.class, id);
        Outcomes result = overlap(() -> loanCommand("RENEW", f, id), () -> loanCommand("RENEW", f, id), false);
        success(result.first());
        rejected(result.second());
        assertThat(loanRow(id).get("renewal_count")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT due_at FROM loans WHERE id = ?", LocalDateTime.class, id)).isEqualTo(due.plusDays(7));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void lossAndReturnCannotReopenTheReturnedLoan(boolean lostFirst) throws Exception {
        Fixture f = fixture();
        long id = loan(f, "ACTIVE", dbNow().plusDays(7), 0);
        Supplier<?> lost = () -> loanCommand("LOST", f, id), returned = () -> loanCommand("RETURN", f, id);
        Outcomes result = overlap(lostFirst ? lost : returned, lostFirst ? returned : lost, false);
        success(result.first());
        if (lostFirst) success(result.second());
        else rejected(result.second());
        assertThat(loanStatus(id)).isEqualTo("RETURNED");
        assertThat(copyStatus(f)).isEqualTo("AVAILABLE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"LOST", "RETURN"})
    void rolledBackClosureLetsTheWaitingRenewalUseTheOriginalState(String command) throws Exception {
        Fixture f = fixture();
        long id = loan(f, "ACTIVE", dbNow().plusDays(7), 0);
        Outcomes result = overlap(() -> loanCommand(command, f, id), () -> loanCommand("RENEW", f, id), true);
        success(result.first());
        success(result.second());
        assertThat(loanStatus(id)).isEqualTo("ACTIVE");
        assertThat(loanRow(id).get("renewal_count")).isEqualTo(1);
        assertThat(loanRow(id).get("returned_at")).isNull();
        assertThat(copyStatus(f)).isEqualTo("LOANED");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void readinessAndCancellationHaveValidOutcomesInBothOrders(boolean readyFirst) throws Exception {
        Fixture f = fixture();
        long id = reservation(f, "ACTIVE", dbNow().plusDays(1));
        Supplier<?> ready = () -> reservationCommand("READY", f, id), cancel = () -> reservationCommand("CANCEL_USER", f, id);
        Outcomes result = overlap(readyFirst ? ready : cancel, readyFirst ? cancel : ready, false);
        success(result.first());
        if (readyFirst) success(result.second());
        else rejected(result.second());
        assertThat(reservationStatus(id)).isEqualTo("CANCELLED_BY_USER");
        assertThat(copyStatus(f)).isEqualTo("AVAILABLE");
        assertThat(events(id)).isEqualTo(readyFirst ? 1 : 0);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void readinessAndExpirationRecheckTimeAndState(boolean readyFirst) throws Exception {
        Fixture f = fixture();
        long id = reservation(f, "ACTIVE", readyFirst ? dbNow().plusHours(1) : dbNow());
        Supplier<?> ready = () -> {
            Object response = reservationCommand("READY", f, id);
            time.updateAndGet(instant -> instant.plusSeconds(3601));
            return response;
        };
        Supplier<?> expire = () -> reservationCommand("EXPIRE", f, id);
        Outcomes result = overlap(readyFirst ? ready : expire, readyFirst ? expire : ready, false);
        success(result.first());
        if (readyFirst) success(result.second());
        else rejected(result.second());
        assertThat(reservationStatus(id)).isEqualTo("EXPIRED");
        assertThat(copyStatus(f)).isEqualTo("AVAILABLE");
        assertThat(events(id)).isEqualTo(readyFirst ? 1 : 0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"READY", "EXPIRE", "CANCEL_USER", "CANCEL_STAFF", "RETURN"})
    void repeatedConcurrentCommandsDoNotRepeatTheTransition(String command) throws Exception {
        Fixture f = fixture();
        long id = command.equals("RETURN") ? loan(f, "ACTIVE", dbNow().plusDays(7), 0)
                : reservation(f, "ACTIVE", command.equals("EXPIRE") ? dbNow() : dbNow().plusDays(1));
        Supplier<?> action = () -> command.equals("RETURN") ? loanCommand(command, f, id) : reservationCommand(command, f, id);
        AtomicReference<Map<String, Object>> committedRow = new AtomicReference<>();
        Outcomes result = overlap(() -> {
            Object response = action.get();
            committedRow.set(command.equals("RETURN") ? loanRow(id) : reservationRow(id));
            return response;
        }, action, false);
        success(result.first());
        rejected(result.second());
        assertThat(command.equals("RETURN") ? loanRow(id) : reservationRow(id)).isEqualTo(committedRow.get());
        assertThat(copyStatus(f)).isEqualTo(command.equals("READY") ? "RESERVED" : "AVAILABLE");
        if (!command.equals("RETURN")) assertThat(events(id)).isEqualTo(command.equals("READY") ? 1 : 0);
    }

    @Test
    void rolledBackReadinessLetsTheWaitingRequestCreateExactlyOneOutboxEvent() throws Exception {
        Fixture f = fixture();
        long id = reservation(f, "ACTIVE", dbNow().plusDays(1));
        Outcomes result = overlap(() -> reservationCommand("READY", f, id), () -> reservationCommand("READY", f, id), true);
        success(result.first());
        success(result.second());
        assertThat(reservationStatus(id)).isEqualTo("READY_FOR_PICKUP");
        assertThat(copyStatus(f)).isEqualTo("RESERVED");
        assertThat(events(id)).isEqualTo(1);
        try {
            var event = objectMapper.readTree(jdbc.queryForObject("SELECT event_json FROM outbox_events WHERE aggregate_id = ?", String.class, Long.toString(id)));
            assertThat(event.path("payload").path("reservationId").asLong()).isEqualTo(id);
            assertThat(event.path("payload").path("copyId").asLong()).isEqualTo(f.copy());
        } catch (JsonProcessingException error) {
            throw new AssertionError(error);
        }
    }

    @Test
    void outboxSerializationFailureRollsBackReadinessAndItsTimestamp() throws Exception {
        Fixture f = fixture();
        long id = reservation(f, "ACTIVE", dbNow().plusDays(1));
        Map<String, Object> before = reservationRow(id);
        doThrow(new JsonProcessingException("test serialization failure") {}).when(objectMapper).writeValueAsString(any(EventEnvelope.class));
        assertThatThrownBy(() -> reservationCommand("READY", f, id)).isInstanceOf(IllegalStateException.class);
        assertThat(reservationRow(id)).isEqualTo(before);
        assertThat(copyStatus(f)).isEqualTo("RESERVED");
        assertThat(events(id)).isZero();
    }

    @Test
    void rollingBackWrittenReadinessRemovesItsOutboxEvent() {
        Fixture f = fixture();
        long id = reservation(f, "ACTIVE", dbNow().plusDays(1));
        Map<String, Object> before = reservationRow(id);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            reservationCommand("READY", f, id);
            entityManager.flush();
            assertThat(events(id)).isEqualTo(1);
            status.setRollbackOnly();
        });
        assertThat(reservationRow(id)).isEqualTo(before);
        assertThat(events(id)).isZero();
    }

    @ParameterizedTest
    @CsvSource({"RENEW,false", "OVERDUE,true", "READY,false", "EXPIRE,true"})
    void deadlineEqualityHasOneConsistentMeaning(String command, boolean allowed) {
        Fixture f = fixture();
        long id = command.equals("RENEW") || command.equals("OVERDUE") ? loan(f, "ACTIVE", dbNow(), 0)
                : reservation(f, "ACTIVE", dbNow());
        Supplier<?> action = () -> command.equals("RENEW") || command.equals("OVERDUE") ? loanCommand(command, f, id)
                : reservationCommand(command, f, id);
        if (allowed) assertThat(action.get()).isNotNull();
        else assertThatThrownBy(action::get).isInstanceOf(ApiException.class);
    }

    private Object loanCommand(String command, Fixture f, long id) {
        return switch (command) {
            case "RENEW" -> loans.renew(f.reader(), id, new RenewLoanRequest(null));
            case "OVERDUE" -> loans.markOverdue(f.staff(), id);
            case "LOST" -> loans.markLost(f.staff(), id);
            default -> loans.returnLoan(f.staff(), id, new ReturnLoanRequest(null, null));
        };
    }

    private Object reservationCommand(String command, Fixture f, long id) {
        return switch (command) {
            case "READY" -> reservations.markReadyForPickup(f.staff(), id);
            case "EXPIRE" -> reservations.expire(f.staff(), id);
            case "CANCEL_USER" -> reservations.cancelByUser(f.reader(), id, new CancelReservationRequest("matrix"));
            case "CANCEL_STAFF" -> reservations.cancelByLibrarian(f.staff(), id, new CancelReservationRequest("matrix"));
            default -> loans.create(f.staff(), new CreateLoanRequest(f.reader(), f.copy(), id, f.branch(), f.staff(), null));
        };
    }

    private Outcomes overlap(Supplier<?> firstAction, Supplier<?> secondAction, boolean rollbackFirst) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch written = new CountDownLatch(1), release = new CountDownLatch(1);
        CompletableFuture<Integer> firstPid = new CompletableFuture<>(), secondPid = new CompletableFuture<>();
        try {
            Future<Outcome> first = executor.submit(() -> invoke(() -> {
                Object response = firstAction.get();
                entityManager.flush();
                written.countDown();
                try {
                    if (!release.await(25, TimeUnit.SECONDS)) throw new IllegalStateException("Copy lock timed out");
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(error);
                }
                return response;
            }, firstPid, rollbackFirst));
            assertThat(written.await(20, TimeUnit.SECONDS)).as("First command reached its SQL writes").isTrue();
            Future<Outcome> second = executor.submit(() -> invoke(secondAction, secondPid, false));
            int holder = firstPid.get(10, TimeUnit.SECONDS), waiter = secondPid.get(10, TimeUnit.SECONDS);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            boolean blocked = false;
            while (!second.isDone() && System.nanoTime() < deadline) {
                blocked = Boolean.TRUE.equals(jdbc.queryForObject("SELECT ? = ANY(pg_blocking_pids(?))", Boolean.class, holder, waiter));
                if (blocked) break;
                Thread.sleep(20);
            }
            assertThat(blocked).as("Second command waits for the first in PostgreSQL").isTrue();
            release.countDown();
            return new Outcomes(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private Outcome invoke(Supplier<?> action, CompletableFuture<Integer> pid, boolean rollback) {
        try {
            return new TransactionTemplate(transactions).execute(status -> {
                pid.complete(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                Object response = action.get();
                if (rollback) status.setRollbackOnly();
                return new Outcome(response, null);
            });
        } catch (RuntimeException error) {
            pid.completeExceptionally(error);
            return new Outcome(null, error);
        }
    }

    private void success(Outcome result) { assertThat(result.error()).isNull(); assertThat(result.response()).isNotNull(); }
    private void rejected(Outcome result) { assertThat(result.error()).isInstanceOf(ApiException.class); }
    private LocalDateTime dbNow() { return LocalDateTime.ofInstant(time.get(), ZoneOffset.UTC); }
    private Map<String, Object> loanRow(long id) { return jdbc.queryForMap("SELECT * FROM loans WHERE id = ?", id); }
    private Map<String, Object> copyRow(Fixture f) { return jdbc.queryForMap("SELECT * FROM material_copies WHERE id = ?", f.copy()); }
    private long audits() { return jdbc.queryForObject("SELECT count(*) FROM audit_logs", Long.class); }
    private Map<String, Object> reservationRow(long id) { return jdbc.queryForMap("SELECT * FROM reservations WHERE id = ?", id); }
    private String loanStatus(long id) { return jdbc.queryForObject("SELECT status::text FROM loans WHERE id = ?", String.class, id); }
    private String reservationStatus(long id) { return jdbc.queryForObject("SELECT status::text FROM reservations WHERE id = ?", String.class, id); }
    private String copyStatus(Fixture f) { return jdbc.queryForObject("SELECT status::text FROM material_copies WHERE id = ?", String.class, f.copy()); }
    private long events(long id) { return jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE aggregate_id = ? AND event_type = 'reservation.ready-for-pickup'", Long.class, Long.toString(id)); }

    private long loan(Fixture f, String state, LocalDateTime due, int renewals) {
        return jdbc.queryForObject("""
                INSERT INTO loans(user_id, copy_id, branch_id, issued_by_user_id, loaned_at, due_at, renewal_count, status, returned_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS loan_status), ?) RETURNING id
                """, Long.class, f.reader(), f.copy(), f.branch(), f.staff(), dbNow().minusDays(2), due, renewals, state,
                state.equals("RETURNED") ? dbNow().minusDays(1) : null);
    }

    private long reservation(Fixture f, String state, LocalDateTime expires) {
        boolean cancelled = state.startsWith("CANCELLED");
        return jdbc.queryForObject("""
                INSERT INTO reservations(user_id, material_id, copy_id, branch_id, status, created_at, expires_at, ready_at, cancelled_at, cancellation_reason)
                VALUES (?, ?, ?, ?, CAST(? AS reservation_status), ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, f.reader(), f.material(), f.copy(), f.branch(), state, dbNow().minusDays(2), expires,
                state.equals("READY_FOR_PICKUP") || state.equals("USED") ? dbNow().minusDays(1) : null,
                cancelled ? dbNow().minusDays(1) : null, cancelled ? "matrix" : null);
    }

    private Fixture fixture() {
        long seq = SEQUENCE.incrementAndGet();
        long library = jdbc.queryForObject("INSERT INTO libraries(code, name) VALUES (?, 'State tests') RETURNING id", Long.class, "state-" + seq);
        long branch = jdbc.queryForObject("""
                INSERT INTO branches(library_id, name, address)
                VALUES (?, 'State branch', '{"city":"Test","street":"Main","building":"1"}') RETURNING id
                """, Long.class, library);
        jdbc.update("INSERT INTO library_rules(branch_id, valid_from, max_renewal_count, renewal_period_days) VALUES (?, ?, 2, 7)", branch, dbNow().minusDays(1));
        long reader = user(), staff = user();
        jdbc.update("INSERT INTO user_roles(user_id, role_id) SELECT ?, id FROM roles WHERE code = 'LIBRARIAN'", staff);
        long material = jdbc.queryForObject("INSERT INTO materials(title) VALUES ('State book') RETURNING id", Long.class);
        long copy = jdbc.queryForObject("INSERT INTO material_copies(material_id, branch_id, inventory_number) VALUES (?, ?, ?) RETURNING id", Long.class, material, branch, "state-copy-" + seq);
        return new Fixture(reader, staff, branch, material, copy);
    }

    private long user() {
        long seq = SEQUENCE.incrementAndGet();
        return jdbc.queryForObject("""
                INSERT INTO users(email, phone, password_hash, first_name, last_name, status)
                VALUES (?, ?, 'test-hash', 'State', 'Reader', 'ACTIVE') RETURNING id
                """, Long.class, "state-" + seq + "@example.com", "+7905%07d".formatted(seq));
    }

    private record Fixture(long reader, long staff, long branch, long material, long copy) {}
    private record Outcome(Object response, RuntimeException error) {}
    private record Outcomes(Outcome first, Outcome second) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class EventConfiguration {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean KafkaMessagingProperties messagingProperties() {
            return new KafkaMessagingProperties(new KafkaMessagingProperties.Topics("library.circulation.events"), null, null, null);
        }
    }
}
