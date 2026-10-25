package me.ifmo.backend.fine.application.impl;

import jakarta.persistence.EntityManager;
import me.ifmo.backend.catalog.mapper.MaterialCopyMapperImpl;
import me.ifmo.backend.fine.application.FineService;
import me.ifmo.backend.fine.application.PaymentLocks;
import me.ifmo.backend.fine.application.PaymentTransactionService;
import me.ifmo.backend.fine.domain.Fine;
import me.ifmo.backend.fine.domain.PaymentTransaction;
import me.ifmo.backend.fine.domain.enums.FineStatus;
import me.ifmo.backend.fine.domain.enums.PaymentStatus;
import me.ifmo.backend.fine.mapper.FineMapperImpl;
import me.ifmo.backend.fine.mapper.PaymentTransactionMapperImpl;
import me.ifmo.backend.fine.persistence.FineRepository;
import me.ifmo.backend.fine.persistence.PaymentTransactionRepository;
import me.ifmo.backend.fine.web.request.CancelFineRequest;
import me.ifmo.backend.fine.web.request.CreatePaymentTransactionRequest;
import me.ifmo.backend.fine.web.request.UpdatePaymentStatusRequest;
import me.ifmo.backend.fine.web.response.PaymentTransactionResponse;
import me.ifmo.backend.fine.web.response.FineResponse;
import me.ifmo.backend.library.mapper.BranchAddressJsonMapper;
import me.ifmo.backend.library.mapper.BranchMapperImpl;
import me.ifmo.backend.library.mapper.LibraryMapperImpl;
import me.ifmo.backend.shared.exceptions.ApiException;
import me.ifmo.backend.user.mapper.RoleMapperImpl;
import me.ifmo.backend.user.mapper.UserMapperImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

@DataJpaTest(showSql = false, properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({PaymentLocks.class, PaymentTransactionServiceImpl.class, FineServiceImpl.class,
        PaymentTransactionMapperImpl.class, FineMapperImpl.class, MaterialCopyMapperImpl.class,
        UserMapperImpl.class, RoleMapperImpl.class, BranchMapperImpl.class, LibraryMapperImpl.class,
        BranchAddressJsonMapper.class, PaymentConsistencyIntegrationTest.JsonConfiguration.class})
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PaymentConsistencyIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final BigDecimal AMOUNT = new BigDecimal("125.00");
    @Autowired private PaymentTransactionService payments;
    @Autowired private FineService fines;
    @Autowired private FineRepository fineRepository;
    @Autowired private PaymentTransactionRepository paymentRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private EntityManager entityManager;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "provider-1", " provider-1 "})
    void identicalSuccessReplayPreservesResponseDatesRowsAndAudit(String external) {
        Fixture f = fixture();
        String assignedId = external();
        long id = create(f, assignedId).id();
        PaymentTransactionResponse first = update(f, id, PaymentStatus.SUCCESS, null);
        Map<String, Object> payment = paymentRow(id), fine = fineRow(f);
        long audit = audits();

        String replayId = external == null ? null : external.replace("provider-1", assignedId);
        assertThat(update(f, id, PaymentStatus.SUCCESS, replayId)).isEqualTo(first);
        assertThat(paymentRow(id)).isEqualTo(payment);
        assertThat(fineRow(f)).isEqualTo(fine);
        assertThat(audits()).isEqualTo(audit);
        assertThat(fines.getFineById(f.reader(), f.fine()).status()).isEqualTo(FineStatus.PAID);
    }

    @Test
    void successWorksWhenTheFineWasLoadedBeforeThePaymentAndRefreshesIt() {
        Fixture f = fixture();
        long id = create(f, null).id();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            Fine loaded = fineRepository.findById(f.fine()).orElseThrow();
            assertThat(loaded.getStatus()).isEqualTo(FineStatus.ACTIVE);
            PaymentTransactionResponse result = update(f, id, PaymentStatus.SUCCESS, "assigned-on-success");
            assertThat(result.updatedAt()).isNotNull();
            assertThat(loaded.getStatus()).isEqualTo(FineStatus.PAID);
            assertThat(loaded.getPaidAt()).isNotNull();
        });
        assertPaid(f, id);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"CREATED", "PENDING"})
    void timestampOrderingSurvivesAJvmClockAheadOfTheDatabase(PaymentStatus state) {
        Fixture f = fixture();
        long id = jdbc.queryForObject("""
                INSERT INTO payment_transactions(fine_id, amount, status, created_at)
                VALUES (?, ?, CAST(? AS payment_status), timezone('UTC', clock_timestamp()) + INTERVAL '1 hour') RETURNING id
                """, Long.class, f.fine(), AMOUNT, state.name());
        PaymentTransactionResponse response = update(f, id, PaymentStatus.SUCCESS, null);
        assertThat(response.updatedAt()).isAfterOrEqualTo(response.createdAt());
        assertThat(fines.getFineById(f.reader(), f.fine()).paidAt()).isAfterOrEqualTo(response.updatedAt());
        assertThat(update(f, id, PaymentStatus.SUCCESS, null)).isEqualTo(response);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PAY", "CANCEL"})
    void manualClosureRefreshesDatesWhenTheDatabaseCreationClockWasAhead(String command) {
        Fixture f = fixture(true);
        FineResponse response = (FineResponse) close(f, command);
        assertThat(command.equals("PAY") ? response.paidAt() : response.cancelledAt()).isAfterOrEqualTo(response.createdAt());
    }

    static Stream<Arguments> transitions() {
        return Arrays.stream(PaymentStatus.values()).flatMap(from -> Arrays.stream(PaymentStatus.values()).map(to -> Arguments.of(from, to)));
    }

    private boolean allowed(PaymentStatus from, PaymentStatus to) {
        return from == to || switch (from) {
            case CREATED -> Set.of(PaymentStatus.PENDING, PaymentStatus.SUCCESS, PaymentStatus.CANCELLED, PaymentStatus.FAILED).contains(to);
            case PENDING -> Set.of(PaymentStatus.SUCCESS, PaymentStatus.DECLINED, PaymentStatus.CANCELLED, PaymentStatus.FAILED, PaymentStatus.TIMEOUT).contains(to);
            default -> false;
        };
    }

    @ParameterizedTest
    @MethodSource("transitions")
    void serviceValidatesEveryPaymentStatusPair(PaymentStatus from, PaymentStatus to) {
        Fixture f = fixture();
        long id = sqlPayment(f, from, external());
        Map<String, Object> before = paymentRow(id), fine = fineRow(f);
        long audit = audits();
        if (allowed(from, to)) {
            assertThat(update(f, id, to, null).status()).isEqualTo(to);
            assertThat(fineStatus(f)).isEqualTo(to == PaymentStatus.SUCCESS ? "PAID" : "ACTIVE");
            if (from == to) {
                assertThat(paymentRow(id)).isEqualTo(before);
                assertThat(fineRow(f)).isEqualTo(fine);
                assertThat(audits()).isEqualTo(audit);
            }
        } else {
            assertThatThrownBy(() -> update(f, id, to, null)).isInstanceOf(ApiException.class);
            assertThat(paymentRow(id)).isEqualTo(before);
            assertThat(fineRow(f)).isEqualTo(fine);
            assertThat(audits()).isEqualTo(audit);
        }
    }

    @ParameterizedTest
    @MethodSource("transitions")
    void databaseValidatesEveryPaymentStatusPair(PaymentStatus from, PaymentStatus to) {
        Fixture f = fixture();
        long id = sqlPayment(f, from, external());
        Map<String, Object> before = paymentRow(id), fine = fineRow(f);
        long audit = audits();
        if (allowed(from, to)) {
            int changed = jdbc.update("UPDATE payment_transactions SET status = CAST(? AS payment_status) WHERE id = ?", to.name(), id);
            assertThat(changed).isEqualTo(from == to ? 0 : 1);
            assertThat(paymentStatus(id)).isEqualTo(to.name());
            assertThat(fineStatus(f)).isEqualTo(to == PaymentStatus.SUCCESS ? "PAID" : "ACTIVE");
            if (from == to) {
                assertThat(paymentRow(id)).isEqualTo(before);
                assertThat(fineRow(f)).isEqualTo(fine);
                assertThat(audits()).isEqualTo(audit);
            }
        } else {
            assertThatThrownBy(() -> jdbc.update("UPDATE payment_transactions SET status = CAST(? AS payment_status) WHERE id = ?", to.name(), id))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(paymentRow(id)).isEqualTo(before);
            assertThat(fineRow(f)).isEqualTo(fine);
            assertThat(audits()).isEqualTo(audit);
        }
    }

    @ParameterizedTest
    @EnumSource(PaymentStatus.class)
    void assignedExternalIdCannotBeReplaced(PaymentStatus state) {
        Fixture f = fixture();
        long id = sqlPayment(f, state, external());
        Map<String, Object> before = paymentRow(id);
        assertThatThrownBy(() -> update(f, id, state, "replacement")).isInstanceOf(ApiException.class);
        assertThat(paymentRow(id)).isEqualTo(before);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"SUCCESS", "DECLINED", "CANCELLED", "FAILED", "TIMEOUT"})
    void finalPaymentCannotAcquireAnExternalIdOrChangeItsTimestamp(PaymentStatus state) {
        Fixture f = fixture();
        long id = sqlPayment(f, state, null);
        Map<String, Object> before = paymentRow(id);
        assertThatThrownBy(() -> update(f, id, state, external())).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE payment_transactions SET updated_at = updated_at + INTERVAL '1 day' WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class).rootCause().hasMessageContaining("Final payment");
        assertThat(paymentRow(id)).isEqualTo(before);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"CREATED", "PENDING"})
    void externalIdCanBeAssignedOnceBeforeCompletion(PaymentStatus state) {
        Fixture f = fixture();
        long id = sqlPayment(f, state, null);
        String external = external();
        PaymentTransactionResponse assigned = update(f, id, state, " " + external + " ");
        assertThat(assigned.externalPayment()).isEqualTo(external);
        assertThat(update(f, id, state, null)).isEqualTo(assigned);
        assertThat(update(f, id, state, " " + external + " ")).isEqualTo(assigned);
    }

    @ParameterizedTest
    @CsvSource({"CREATED,false", "CREATED,true", "PENDING,false", "PENDING,true"})
    void sqlCannotReplaceOrClearAnAssignedExternalId(PaymentStatus state, boolean clear) {
        Fixture f = fixture();
        long id = sqlPayment(f, state, external());
        Map<String, Object> before = paymentRow(id);
        assertThatThrownBy(() -> jdbc.update("UPDATE payment_transactions SET external_payment_id = ? WHERE id = ?", clear ? null : external(), id))
                .isInstanceOf(DataIntegrityViolationException.class).rootCause().hasMessageContaining("External payment id");
        assertThat(paymentRow(id)).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "fine_id", "amount", "created_at"})
    void sqlCannotRebindOrChangeThePaymentAmount(String field) {
        Fixture f = fixture(), other = fixture();
        long id = sqlPayment(f, PaymentStatus.PENDING, external());
        Map<String, Object> before = paymentRow(id);
        Object replacement = switch (field) {
            case "id" -> id + 1_000_000;
            case "fine_id" -> other.fine();
            case "amount" -> BigDecimal.ONE;
            default -> LocalDateTime.of(2020, 1, 1, 0, 0);
        };
        assertThatThrownBy(() -> jdbc.update("UPDATE payment_transactions SET " + field + " = ? WHERE id = ?", replacement, id))
                .isInstanceOf(DataIntegrityViolationException.class).rootCause().hasMessageContaining("Payment identity and amount");
        assertThat(paymentRow(id)).isEqualTo(before);
    }

    static Stream<Arguments> fineTransitions() {
        return Arrays.stream(FineStatus.values()).flatMap(from -> Arrays.stream(FineStatus.values()).map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest
    @MethodSource("fineTransitions")
    void sqlCannotReopenOrChangeAClosedFine(FineStatus from, FineStatus to) {
        Fixture f = fixture();
        if (from != FineStatus.ACTIVE) close(f, from == FineStatus.PAID ? "PAY" : "CANCEL");
        Map<String, Object> before = fineRow(f);
        Runnable command = () -> {
            if (from == to) jdbc.update("UPDATE fines SET status = status WHERE id = ?", f.fine());
            else sqlClose(f, to);
        };
        if (from == to || from == FineStatus.ACTIVE) {
            command.run();
            assertThat(fineStatus(f)).isEqualTo(to.name());
        } else {
            assertThatThrownBy(command::run).isInstanceOf(DataIntegrityViolationException.class);
            assertThat(fineRow(f)).isEqualTo(before);
        }
    }

    @ParameterizedTest
    @CsvSource({"CREATED,PAY", "CREATED,CANCEL", "PENDING,PAY", "PENDING,CANCEL"})
    void pendingPaymentBlocksManualFineClosureInJavaAndSql(PaymentStatus state, String command) {
        Fixture f = fixture();
        long id = sqlPayment(f, state, external());
        Map<String, Object> fine = fineRow(f), payment = paymentRow(id);
        assertThatThrownBy(() -> close(f, command)).isInstanceOf(ApiException.class).hasMessageContaining("in progress");
        assertThatThrownBy(() -> sqlClose(f, command.equals("PAY") ? FineStatus.PAID : FineStatus.CANCELLED))
                .isInstanceOf(DataIntegrityViolationException.class).rootCause().hasMessageContaining("in progress");
        assertThat(fineRow(f)).isEqualTo(fine);
        assertThat(paymentRow(id)).isEqualTo(payment);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PAY", "CANCEL"})
    void aClosedFineRejectsNewPaymentsAndRawSqlSuccess(String command) {
        Fixture f = fixture();
        close(f, command);
        Map<String, Object> before = fineRow(f);
        assertThatThrownBy(() -> create(f, external())).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> sqlPayment(f, PaymentStatus.SUCCESS, external())).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(fineRow(f)).isEqualTo(before);
        assertThat(paymentCount(f)).isZero();
    }

    @Test
    void creationChecksTheAmountAndDatabasePreventsLaterFineAmountChanges() {
        Fixture f = fixture();
        assertThatThrownBy(() -> payments.create(f.reader(), new CreatePaymentTransactionRequest(f.fine(), null, BigDecimal.ONE)))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO payment_transactions(fine_id, amount) VALUES (?, 1)", f.fine()))
                .isInstanceOf(DataIntegrityViolationException.class);
        long id = create(f, null).id();
        assertThatThrownBy(() -> jdbc.update("UPDATE fines SET amount = 1 WHERE id = ?", f.fine()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(paymentRow(id).get("amount")).isEqualTo(AMOUNT);
    }

    @Test
    void duplicateExternalIdsAreRejectedAndWhitespaceIsNormalized() {
        Fixture f = fixture(), other = fixture();
        String id = external();
        assertThat(create(f, " " + id + " ").externalPayment()).isEqualTo(id);
        assertThatThrownBy(() -> create(other, id)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> sqlPayment(other, PaymentStatus.CREATED, " " + id + " "))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void concurrentIdenticalSuccessReturnsTheSameResultEvenWithStaleManagedEntities() throws Exception {
        Fixture f = fixture();
        long id = create(f, external()).id();
        AtomicReference<Map<String, Object>> paid = new AtomicReference<>();
        AtomicReference<Long> audit = new AtomicReference<>();
        Outcomes result = overlap(() -> {
            var response = update(f, id, PaymentStatus.SUCCESS, null);
            paid.set(fineRow(f));
            audit.set(audits());
            return response;
        }, () -> {
            PaymentTransaction stale = paymentRepository.findById(id).orElseThrow();
            assertThat(stale.getStatus()).isEqualTo(PaymentStatus.CREATED);
            assertThat(stale.getFine().getStatus()).isEqualTo(FineStatus.ACTIVE);
            return update(f, id, PaymentStatus.SUCCESS, null);
        }, true, false, null);
        success(result.first()); success(result.second());
        assertThat(result.second().response()).isEqualTo(result.first().response());
        assertThat(fineRow(f)).isEqualTo(paid.get());
        assertThat(audits()).isEqualTo(audit.get());
        assertPaid(f, id);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void successAndPaymentCancellationCannotOverwriteEachOther(boolean successFirst) throws Exception {
        Fixture f = fixture();
        long id = sqlPayment(f, PaymentStatus.PENDING, external());
        Supplier<?> confirm = () -> update(f, id, PaymentStatus.SUCCESS, null), cancel = () -> update(f, id, PaymentStatus.CANCELLED, null);
        Outcomes result = overlap(successFirst ? confirm : cancel, successFirst ? cancel : confirm, true, false, null);
        success(result.first()); rejected(result.second());
        assertThat(paymentStatus(id)).isEqualTo(successFirst ? "SUCCESS" : "CANCELLED");
        assertThat(fineStatus(f)).isEqualTo(successFirst ? "PAID" : "ACTIVE");
    }

    @ParameterizedTest
    @CsvSource({"PAY,true", "PAY,false", "CANCEL,true", "CANCEL,false"})
    void confirmationAndManualClosureUseTheSameFineLock(String command, boolean confirmFirst) throws Exception {
        Fixture f = fixture();
        long id = sqlPayment(f, PaymentStatus.PENDING, external());
        Supplier<?> confirm = () -> update(f, id, PaymentStatus.SUCCESS, null), manual = () -> close(f, command);
        Outcomes result = overlap(confirmFirst ? confirm : manual, confirmFirst ? manual : confirm, true, false, null);
        success(confirmFirst ? result.first() : result.second());
        rejected(confirmFirst ? result.second() : result.first());
        assertPaid(f, id);
    }

    @ParameterizedTest
    @CsvSource({"PAY,true", "PAY,false", "CANCEL,true", "CANCEL,false"})
    void creationAndManualClosureRecheckTheFineAfterWaiting(String command, boolean createFirst) throws Exception {
        Fixture f = fixture();
        Supplier<?> start = () -> create(f, null), manual = () -> close(f, command);
        Outcomes result = overlap(createFirst ? start : manual, createFirst ? manual : start, true, false, null);
        success(result.first()); rejected(result.second());
        assertThat(paymentCount(f)).isEqualTo(createFirst ? 1 : 0);
        assertThat(fineStatus(f)).isEqualTo(createFirst ? "ACTIVE" : command.equals("PAY") ? "PAID" : "CANCELLED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PAY", "CANCEL"})
    void cancellingThePaymentLetsTheWaitingManualClosureProceed(String command) throws Exception {
        Fixture f = fixture();
        long id = create(f, null).id();
        Outcomes result = overlap(() -> update(f, id, PaymentStatus.CANCELLED, null), () -> close(f, command), true, false, null);
        success(result.first()); success(result.second());
        assertThat(paymentStatus(id)).isEqualTo("CANCELLED");
        assertThat(fineStatus(f)).isEqualTo(command.equals("PAY") ? "PAID" : "CANCELLED");
    }

    @Test
    void concurrentCreationAllowsOnlyOneInProgressPayment() throws Exception {
        Fixture f = fixture();
        Outcomes result = overlap(() -> create(f, null), () -> create(f, null), true, false, null);
        success(result.first()); rejected(result.second());
        assertThat(paymentCount(f)).isEqualTo(1);
    }

    @Test
    void paymentsForDifferentFinesProceedIndependently() throws Exception {
        Fixture f = fixture(), other = fixture();
        Outcomes result = overlap(() -> create(f, null), () -> create(other, null), false, false, null);
        success(result.first()); success(result.second());
    }

    @Test
    void concurrentExternalIdClaimIsStillProtectedAcrossDifferentFines() throws Exception {
        Fixture f = fixture(), other = fixture();
        String external = external();
        Outcomes result = overlap(() -> create(f, external), () -> create(other, external), true, false, null);
        success(result.first());
        assertThat(result.second().error()).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(paymentCount(other)).isZero();
    }

    @Test
    void rolledBackSuccessLetsTheWaitingConfirmationSucceed() throws Exception {
        Fixture f = fixture();
        long id = create(f, null).id();
        Outcomes result = overlap(() -> update(f, id, PaymentStatus.SUCCESS, null), () -> update(f, id, PaymentStatus.SUCCESS, null), true, true, null);
        success(result.first()); success(result.second());
        assertPaid(f, id);
    }

    @Test
    void rolledBackSuccessRestoresThePendingPaymentAndBlocksManualPayment() throws Exception {
        Fixture f = fixture();
        long id = sqlPayment(f, PaymentStatus.PENDING, external());
        Map<String, Object> fine = fineRow(f), payment = paymentRow(id);
        long audit = audits();
        Outcomes result = overlap(() -> update(f, id, PaymentStatus.SUCCESS, null), () -> close(f, "PAY"), true, true, null);
        success(result.first()); rejected(result.second());
        assertThat(fineRow(f)).isEqualTo(fine);
        assertThat(paymentRow(id)).isEqualTo(payment);
        assertThat(audits()).isEqualTo(audit);
    }

    @Test
    void rawInsertLocksTheFineBeforeItsUniqueIndexCanBlockTheService() throws Exception {
        Fixture f = fixture();
        Outcomes result = overlap(() -> create(f, null), () -> sqlPayment(f, PaymentStatus.CREATED, null), true, false, f.fine());
        success(result.first());
        assertThat(result.second().error()).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(paymentCount(f)).isEqualTo(1);
    }

    @Test
    void rawUpdateRejectsAnInvertedLockOrderWithoutDeadlockingTheService() throws Exception {
        Fixture f = fixture();
        long id = create(f, null).id();
        Outcomes result = overlap(() -> update(f, id, PaymentStatus.SUCCESS, null), () ->
                jdbc.update("UPDATE payment_transactions SET status = 'CANCELLED' WHERE id = ?", id), false, false, f.fine());
        success(result.first());
        assertThat(result.second().error()).isInstanceOf(DataIntegrityViolationException.class)
                .rootCause().hasMessageContaining("Fine is being changed");
        assertPaid(f, id);
    }

    @Test
    void rawInsertWaitingForManualClosureCannotCreateAPaymentForTheClosedFine() throws Exception {
        Fixture f = fixture();
        Outcomes result = overlap(() -> close(f, "CANCEL"), () -> sqlPayment(f, PaymentStatus.SUCCESS, external()), true, false, null);
        success(result.first());
        assertThat(result.second().error()).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(fineStatus(f)).isEqualTo("CANCELLED");
        assertThat(paymentCount(f)).isZero();
    }

    @Test
    void readersCannotConfirmPaymentsOrCreatePaymentsForAnotherReader() {
        Fixture f = fixture(), other = fixture();
        long id = create(f, null).id();
        assertThatThrownBy(() -> payments.updateStatus(f.reader(), id, new UpdatePaymentStatusRequest(PaymentStatus.SUCCESS, null)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> payments.create(other.reader(), new CreatePaymentTransactionRequest(f.fine(), null, AMOUNT)))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(paymentStatus(id)).isEqualTo("CREATED");
        assertThat(fineStatus(f)).isEqualTo("ACTIVE");
    }

    private Outcomes overlap(Supplier<?> firstAction, Supplier<?> secondAction, boolean blocking,
                             boolean rollbackFirst, Long lockBeforeAction) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1);
        CompletableFuture<Integer> firstPid = new CompletableFuture<>(), secondPid = new CompletableFuture<>();
        try {
            Future<Outcome> first = executor.submit(() -> invoke(() -> {
                if (lockBeforeAction != null) {
                    jdbc.queryForObject("SELECT id FROM fines WHERE id = ? FOR NO KEY UPDATE", Long.class, lockBeforeAction);
                    held.countDown(); await(release);
                    return firstAction.get();
                }
                Object response = null;
                RuntimeException failure = null;
                try {
                    response = firstAction.get();
                    entityManager.flush();
                } catch (RuntimeException error) {
                    failure = error;
                }
                held.countDown(); await(release);
                if (failure != null) throw failure;
                return response;
            }, firstPid, rollbackFirst));
            assertThat(held.await(20, TimeUnit.SECONDS)).as("First command holds the fine lock").isTrue();
            Future<Outcome> second = executor.submit(() -> invoke(secondAction, secondPid, false));
            if (blocking) {
                int holder = firstPid.get(10, TimeUnit.SECONDS), waiter = secondPid.get(10, TimeUnit.SECONDS);
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                boolean blocked = false;
                while (!second.isDone() && System.nanoTime() < deadline) {
                    blocked = Boolean.TRUE.equals(jdbc.queryForObject("SELECT ? = ANY(pg_blocking_pids(?))", Boolean.class, holder, waiter));
                    if (blocked) break;
                    Thread.sleep(20);
                }
                assertThat(blocked).as("Second command actually waits in PostgreSQL").isTrue();
            } else second.get(10, TimeUnit.SECONDS);
            release.countDown();
            return new Outcomes(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        } finally {
            release.countDown(); executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(25, TimeUnit.SECONDS)) throw new IllegalStateException("Fine lock timed out");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
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

    private PaymentTransactionResponse create(Fixture f, String external) {
        return payments.create(f.reader(), new CreatePaymentTransactionRequest(f.fine(), external, AMOUNT));
    }
    private PaymentTransactionResponse update(Fixture f, long id, PaymentStatus status, String external) {
        return payments.updateStatus(f.staff(), id, new UpdatePaymentStatusRequest(status, external));
    }
    private Object close(Fixture f, String command) {
        return command.equals("PAY") ? fines.markPaid(f.staff(), f.fine()) : fines.cancel(f.staff(), f.fine(), new CancelFineRequest("Mistake"));
    }
    private void sqlClose(Fixture f, FineStatus target) {
        jdbc.update("""
                UPDATE fines SET status = CAST(? AS fine_status),
                    paid_at = CASE WHEN ? = 'PAID' THEN timezone('UTC', clock_timestamp()) ELSE NULL END,
                    cancelled_at = CASE WHEN ? = 'CANCELLED' THEN timezone('UTC', clock_timestamp()) ELSE NULL END,
                    cancellation_reason = CASE WHEN ? = 'CANCELLED' THEN 'Mistake' ELSE NULL END WHERE id = ?
                """, target.name(), target.name(), target.name(), target.name(), f.fine());
    }
    private long sqlPayment(Fixture f, PaymentStatus state, String external) {
        return jdbc.queryForObject("""
                INSERT INTO payment_transactions(fine_id, amount, status, external_payment_id)
                VALUES (?, ?, CAST(? AS payment_status), ?) RETURNING id
                """, Long.class, f.fine(), AMOUNT, state.name(), external);
    }
    private Map<String, Object> paymentRow(long id) { return jdbc.queryForMap("SELECT * FROM payment_transactions WHERE id = ?", id); }
    private Map<String, Object> fineRow(Fixture f) { return jdbc.queryForMap("SELECT * FROM fines WHERE id = ?", f.fine()); }
    private String paymentStatus(long id) { return jdbc.queryForObject("SELECT status::text FROM payment_transactions WHERE id = ?", String.class, id); }
    private String fineStatus(Fixture f) { return jdbc.queryForObject("SELECT status::text FROM fines WHERE id = ?", String.class, f.fine()); }
    private long paymentCount(Fixture f) { return jdbc.queryForObject("SELECT count(*) FROM payment_transactions WHERE fine_id = ?", Long.class, f.fine()); }
    private long audits() { return jdbc.queryForObject("SELECT count(*) FROM audit_logs", Long.class); }
    private String external() { return "payment-" + SEQUENCE.incrementAndGet(); }
    private void success(Outcome result) { assertThat(result.error()).isNull(); assertThat(result.response()).isNotNull(); }
    private void rejected(Outcome result) { assertThat(result.error()).isInstanceOf(ApiException.class); }
    private void assertPaid(Fixture f, long id) {
        assertThat(paymentStatus(id)).isEqualTo("SUCCESS");
        assertThat(fineStatus(f)).isEqualTo("PAID");
        assertThat(fineRow(f).get("paid_at")).isNotNull();
        assertThat(fineRow(f).get("cancelled_at")).isNull();
        assertThat(paymentCount(f)).isEqualTo(1);
    }

    private Fixture fixture() {
        return fixture(false);
    }
    private Fixture fixture(boolean futureCreation) {
        long reader = user(), staff = user();
        jdbc.update("INSERT INTO user_roles(user_id, role_id) SELECT ?, id FROM roles WHERE code = 'LIBRARIAN'", staff);
        long fine = jdbc.queryForObject("""
                INSERT INTO fines(user_id, reason, amount, created_at)
                VALUES (?, 'OTHER', ?, timezone('UTC', clock_timestamp()) + CAST(? AS interval)) RETURNING id
                """, Long.class, reader, AMOUNT, futureCreation ? "1 hour" : "0 seconds");
        return new Fixture(reader, staff, fine);
    }
    private long user() {
        long seq = SEQUENCE.incrementAndGet();
        return jdbc.queryForObject("""
                INSERT INTO users(email, phone, password_hash, first_name, last_name, status)
                VALUES (?, ?, 'test-hash', 'Payment', 'Reader', 'ACTIVE') RETURNING id
                """, Long.class, "payment-" + seq + "@example.com", "+7906%07d".formatted(seq));
    }
    private record Fixture(long reader, long staff, long fine) {}
    private record Outcome(Object response, RuntimeException error) {}
    private record Outcomes(Outcome first, Outcome second) {}
    @TestConfiguration(proxyBeanMethods = false)
    static class JsonConfiguration {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
    }
}
