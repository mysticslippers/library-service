package me.ifmo.backend.circulation.application.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.ifmo.backend.catalog.application.MaterialCopyService;
import me.ifmo.backend.catalog.application.impl.MaterialCopyServiceImpl;
import me.ifmo.backend.catalog.domain.enums.CopyStatus;
import me.ifmo.backend.catalog.mapper.MaterialCopyMapperImpl;
import me.ifmo.backend.catalog.mapper.MaterialMapper;
import me.ifmo.backend.catalog.web.request.ChangeMaterialCopyStatusRequest;
import me.ifmo.backend.catalog.web.request.UpdateMaterialCopyRequest;
import me.ifmo.backend.circulation.application.CirculationLocks;
import me.ifmo.backend.circulation.application.LoanService;
import me.ifmo.backend.circulation.application.ReservationService;
import me.ifmo.backend.circulation.integration.ReservationEventPublisher;
import me.ifmo.backend.circulation.mapper.LoanMapperImpl;
import me.ifmo.backend.circulation.mapper.ReservationMapperImpl;
import me.ifmo.backend.circulation.web.request.CancelReservationRequest;
import me.ifmo.backend.circulation.web.request.CreateLoanRequest;
import me.ifmo.backend.circulation.web.request.CreateReservationRequest;
import me.ifmo.backend.circulation.web.request.ReturnLoanRequest;
import me.ifmo.backend.circulation.web.request.RenewLoanRequest;
import me.ifmo.backend.circulation.web.response.LoanResponse;
import me.ifmo.backend.circulation.web.response.ReservationResponse;
import me.ifmo.backend.library.mapper.BranchAddressJsonMapper;
import me.ifmo.backend.library.mapper.BranchMapperImpl;
import me.ifmo.backend.library.mapper.LibraryMapperImpl;
import me.ifmo.backend.shared.exceptions.ApiException;
import me.ifmo.backend.shared.exceptions.ResourceNotFoundException;
import me.ifmo.backend.user.mapper.RoleMapperImpl;
import me.ifmo.backend.user.mapper.UserMapperImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(showSql = false, properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({CirculationLocks.class, LoanServiceImpl.class, ReservationServiceImpl.class, MaterialCopyServiceImpl.class,
        LoanMapperImpl.class, ReservationMapperImpl.class, MaterialCopyMapperImpl.class, UserMapperImpl.class,
        RoleMapperImpl.class, BranchMapperImpl.class, LibraryMapperImpl.class, BranchAddressJsonMapper.class,
        CopyCirculationIntegrationTest.JsonConfiguration.class})
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CopyCirculationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final AtomicLong SEQUENCE = new AtomicLong();
    @Autowired private LoanService loans;
    @Autowired private ReservationService reservations;
    @Autowired private MaterialCopyService copies;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    // Only presentation of catalog details and event transport are replaced.
    @MockitoBean private MaterialMapper materialMapper;
    @MockitoBean private ReservationEventPublisher events;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @ParameterizedTest
    @CsvSource({"RESERVE,LOAN", "LOAN,RESERVE", "LOAN,LOAN", "RESERVE,RESERVE"})
    void differentReadersCannotClaimTheSameCopy(String first, String second) throws Exception {
        Fixture f = fixture();
        Outcomes result = overlap(() -> claim(first, f, f.reader(), f.copy()),
                () -> claim(second, f, f.otherReader(), f.copy()), true, false);

        assertSuccess(result.first());
        assertThat(result.second().error()).isInstanceOf(ApiException.class);
        assertState(f.copy(), first.equals("LOAN") ? "LOANED" : "RESERVED", first.equals("LOAN") ? 0 : 1,
                first.equals("LOAN") ? 1 : 0);
        assertResponseCopy(result.first().value(), first.equals("LOAN") ? CopyStatus.LOANED : CopyStatus.RESERVED);
    }

    @Test
    void automaticSelectionSkipsTheLockedCopyAndReturnsAnotherOne() throws Exception {
        Fixture f = fixture();
        Book other = copy(f.copy().material(), f.branch());
        Outcomes result = overlap(() -> claim("RESERVE", f, f.reader(), f.copy()),
                () -> reserveAutomatically(f, f.otherReader()), false, false);

        assertSuccess(result.first());
        assertSuccess(result.second());
        assertThat(((ReservationResponse) result.second().value()).copy().id()).isEqualTo(other.id());
        assertState(f.copy(), "RESERVED", 1, 0);
        assertState(other, "RESERVED", 1, 0);
    }

    @Test
    void automaticSelectionDoesNotWaitWhenTheOnlyCopyIsLocked() throws Exception {
        Fixture f = fixture();
        Outcomes result = overlap(() -> claim("LOAN", f, f.reader(), f.copy()),
                () -> reserveAutomatically(f, f.otherReader()), false, false);
        assertSuccess(result.first());
        assertThat(result.second().error()).isInstanceOf(ResourceNotFoundException.class);
        assertState(f.copy(), "LOANED", 0, 1);
    }

    @Test
    void operationsOnDifferentCopiesProceedIndependently() throws Exception {
        Fixture f = fixture();
        Book other = book(f.branch());
        Outcomes result = overlap(() -> claim("LOAN", f, f.reader(), f.copy()),
                () -> claim("RESERVE", f, f.otherReader(), other), false, false);
        assertSuccess(result.first());
        assertSuccess(result.second());
        assertState(f.copy(), "LOANED", 0, 1);
        assertState(other, "RESERVED", 1, 0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"RESERVE", "LOAN"})
    void rollbackLetsTheWaitingReaderClaimTheCopy(String first) throws Exception {
        Fixture f = fixture();
        String second = first.equals("LOAN") ? "RESERVE" : "LOAN";
        Outcomes result = overlap(() -> claim(first, f, f.reader(), f.copy()),
                () -> claim(second, f, f.otherReader(), f.copy()), true, true);
        assertSuccess(result.first());
        assertSuccess(result.second());
        assertState(f.copy(), second.equals("LOAN") ? "LOANED" : "RESERVED", second.equals("LOAN") ? 0 : 1,
                second.equals("LOAN") ? 1 : 0);
    }

    @ParameterizedTest
    @CsvSource({"RESERVE,STATUS,true", "RESERVE,STATUS,false", "RESERVE,BRANCH,true", "RESERVE,BRANCH,false",
            "LOAN,STATUS,true", "LOAN,STATUS,false", "LOAN,BRANCH,true", "LOAN,BRANCH,false"})
    void circulationAndCopyEditsRecheckStateAfterWaiting(String operation, String edit, boolean claimFirst) throws Exception {
        Fixture f = fixture();
        Supplier<?> create = () -> claim(operation, f, f.reader(), f.copy());
        Supplier<?> change = () -> editCopy(edit, f);
        Outcomes result = overlap(claimFirst ? create : change, claimFirst ? change : create, true, false);

        assertSuccess(result.first());
        assertThat(result.second().error()).isInstanceOf(ApiException.class);
        if (claimFirst) {
            assertState(f.copy(), operation.equals("LOAN") ? "LOANED" : "RESERVED", operation.equals("LOAN") ? 0 : 1,
                    operation.equals("LOAN") ? 1 : 0);
            assertThat(branchOf(f.copy())).isEqualTo(f.branch());
        } else {
            assertState(f.copy(), edit.equals("STATUS") ? "UNDER_REPAIR" : "AVAILABLE", 0, 0);
            assertThat(branchOf(f.copy())).isEqualTo(edit.equals("BRANCH") ? f.otherBranch() : f.branch());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void issuingAReservationAndCancellingItUseTheSameLockOrder(boolean issueFirst) throws Exception {
        Fixture f = fixture();
        long reservation = ready(f);
        Supplier<?> issue = () -> issueReserved(f, reservation);
        Supplier<?> cancel = () -> reservations.cancelByUser(f.reader(), reservation, new CancelReservationRequest("Changed plans"));
        Outcomes result = overlap(issueFirst ? issue : cancel, issueFirst ? cancel : issue, true, false);

        assertSuccess(result.first());
        assertThat(result.second().error()).isInstanceOf(ApiException.class);
        assertThat(reservationStatus(reservation)).isEqualTo(issueFirst ? "USED" : "CANCELLED_BY_USER");
        assertState(f.copy(), issueFirst ? "LOANED" : "AVAILABLE", 0, issueFirst ? 1 : 0);
    }

    @ParameterizedTest
    @EnumSource(value = CopyStatus.class, names = {"AVAILABLE", "DAMAGED"})
    void returnMakesOnlyAnAvailableCopyReservable(CopyStatus physicalCondition) throws Exception {
        Fixture f = fixture();
        long loan = ((LoanResponse) claim("LOAN", f, f.reader(), f.copy())).id();
        Outcomes result = overlap(() -> loans.returnLoan(f.staff(), loan, new ReturnLoanRequest(physicalCondition, null)),
                () -> claim("RESERVE", f, f.otherReader(), f.copy()), true, false);

        assertSuccess(result.first());
        if (physicalCondition == CopyStatus.AVAILABLE)
            assertSuccess(result.second());
        else
            assertThat(result.second().error()).isInstanceOf(ApiException.class);
        assertState(f.copy(), physicalCondition == CopyStatus.AVAILABLE ? "RESERVED" : "DAMAGED",
                physicalCondition == CopyStatus.AVAILABLE ? 1 : 0, 0);
        assertThat(jdbc.queryForObject("SELECT status::text FROM loans WHERE id = ?", String.class, loan)).isEqualTo("RETURNED");
    }

    @Test
    void returningAFoundLostCopyReleasesItAndRefreshesTheResponse() {
        Fixture f = fixture();
        long loan = ((LoanResponse) claim("LOAN", f, f.reader(), f.copy())).id();
        assertThat(loans.markLost(f.staff(), loan).copy().status()).isEqualTo(CopyStatus.LOST);
        assertState(f.copy(), "LOST", 0, 1);
        assertThat(loans.returnLoan(f.staff(), loan, new ReturnLoanRequest(null, null)).copy().status()).isEqualTo(CopyStatus.AVAILABLE);
        assertState(f.copy(), "AVAILABLE", 0, 0);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void rawSqlAndServiceCommandsShareTheCopyLockWithoutForeignKeyDeadlocks(boolean sqlFirst) throws Exception {
        Fixture f = fixture();
        Supplier<?> sql = () -> {
            jdbc.update("""
                    INSERT INTO reservations(user_id, material_id, copy_id, branch_id, expires_at)
                    VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP + INTERVAL '1 day')
                    """, f.reader(), f.copy().material(), f.copy().id(), f.branch());
            return "SQL reservation";
        };
        Supplier<?> service = () -> claim("LOAN", f, f.otherReader(), f.copy());
        Outcomes result = overlap(sqlFirst ? sql : service, sqlFirst ? service : sql, true, false);
        assertSuccess(result.first());
        assertThat(result.second().error()).isInstanceOf(sqlFirst ? ApiException.class : DataIntegrityViolationException.class);
        assertState(f.copy(), sqlFirst ? "RESERVED" : "LOANED", sqlFirst ? 1 : 0, sqlFirst ? 0 : 1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void renewalCannotReopenALoanAfterReturn(boolean renewFirst) throws Exception {
        Fixture f = fixture();
        long loan = ((LoanResponse) claim("LOAN", f, f.reader(), f.copy())).id();
        Supplier<?> renew = () -> loans.renew(f.reader(), loan, new RenewLoanRequest(null));
        Supplier<?> returnLoan = () -> loans.returnLoan(f.staff(), loan, new ReturnLoanRequest(null, null));
        Outcomes result = overlap(renewFirst ? renew : returnLoan, renewFirst ? returnLoan : renew, true, false);
        assertSuccess(result.first());
        if (renewFirst) assertSuccess(result.second());
        else assertThat(result.second().error()).isInstanceOf(ApiException.class);
        assertState(f.copy(), "AVAILABLE", 0, 0);
        assertThat(jdbc.queryForObject("SELECT status::text FROM loans WHERE id = ?", String.class, loan)).isEqualTo("RETURNED");
    }

    @Test
    void expirationReleasesTheCopyAndAWaitingIssueRejectsTheExpiredReservation() throws Exception {
        Fixture f = fixture();
        long reservation = ready(f);
        jdbc.update("UPDATE reservations SET created_at = timezone('UTC', CURRENT_TIMESTAMP) - INTERVAL '1 day', expires_at = timezone('UTC', CURRENT_TIMESTAMP) - INTERVAL '1 hour' WHERE id = ?", reservation);
        Outcomes result = overlap(() -> reservations.expire(f.staff(), reservation),
                () -> issueReserved(f, reservation), true, false);
        assertSuccess(result.first());
        assertThat(result.second().error()).isInstanceOf(ApiException.class);
        assertThat(reservationStatus(reservation)).isEqualTo("EXPIRED");
        assertState(f.copy(), "AVAILABLE", 0, 0);
    }

    @Test
    void sqlInsertLocksTheCopyBeforeItsUniqueIndexCanBlockTheCopyHolder() throws Exception {
        Fixture f = fixture();
        Outcomes result = overlap(() -> claim("LOAN", f, f.otherReader(), f.copy()), () -> {
            sqlLoan(f, f.reader(), f.copy(), null, f.branch());
            return "SQL loan";
        }, true, false, f.copy());
        assertSuccess(result.first());
        assertThat(result.second().error()).isInstanceOf(DataIntegrityViolationException.class);
        assertState(f.copy(), "LOANED", 0, 1);
    }

    @ParameterizedTest
    @CsvSource({"ACTIVE,ACTIVE", "ACTIVE,OVERDUE", "ACTIVE,LOST", "READY_FOR_PICKUP,ACTIVE",
            "READY_FOR_PICKUP,OVERDUE", "READY_FOR_PICKUP,LOST"})
    void sqlCannotIssueAReservedCopyWithoutItsReservation(String state, String loanStatus) {
        Fixture f = fixture();
        long reservation = reserve(f, f.reader(), f.copy()).id();
        if (state.equals("READY_FOR_PICKUP")) reservations.markReadyForPickup(f.staff(), reservation);
        assertThatThrownBy(() -> sqlLoan(f, f.otherReader(), f.copy(), null, f.branch(), loanStatus))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertState(f.copy(), "RESERVED", 1, 0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "OVERDUE", "LOST"})
    void sqlBlockingLoansSetTheMatchingCopyState(String status) {
        Fixture f = fixture();
        sqlLoan(f, f.reader(), f.copy(), null, f.branch(), status);
        assertState(f.copy(), status.equals("LOST") ? "LOST" : "LOANED", 0, 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"READER", "COPY", "BRANCH", "ACTIVE", "EXPIRED", "USED"})
    void sqlReservedLoanMustMatchAReadyUnexpiredUnusedReservation(String mismatch) {
        Fixture f = fixture();
        long reservation = mismatch.equals("ACTIVE") ? reserve(f, f.reader(), f.copy()).id() : ready(f);
        Book selected = mismatch.equals("COPY") ? book(f.branch()) : f.copy();
        if (mismatch.equals("EXPIRED")) {
            jdbc.update("UPDATE reservations SET created_at = timezone('UTC', CURRENT_TIMESTAMP) - INTERVAL '1 day', expires_at = timezone('UTC', CURRENT_TIMESTAMP) - INTERVAL '1 hour' WHERE id = ?", reservation);
            assertThatThrownBy(() -> issueReserved(f, reservation)).isInstanceOf(ApiException.class)
                    .hasMessage("Reservation has expired");
        }
        if (mismatch.equals("USED")) {
            long loan = issueReserved(f, reservation).id();
            loans.returnLoan(f.staff(), loan, new ReturnLoanRequest(null, null));
        }
        String before = reservationStatus(reservation);
        long count = totalLoans(f.copy());

        assertThatThrownBy(() -> sqlLoan(f, mismatch.equals("READER") ? f.otherReader() : f.reader(), selected,
                reservation, mismatch.equals("BRANCH") ? f.otherBranch() : f.branch()))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(reservationStatus(reservation)).isEqualTo(before);
        assertThat(totalLoans(f.copy())).isEqualTo(count);
    }

    @ParameterizedTest
    @ValueSource(strings = {"BRANCH", "MATERIAL"})
    void sqlReservationCannotMismatchTheCopy(String mismatch) {
        Fixture f = fixture();
        long material = mismatch.equals("MATERIAL") ? book(f.branch()).material() : f.copy().material();
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO reservations(user_id, material_id, copy_id, branch_id, expires_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP + INTERVAL '1 day')
                """, f.reader(), material, f.copy().id(), mismatch.equals("BRANCH") ? f.otherBranch() : f.branch()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertState(f.copy(), "AVAILABLE", 0, 0);
    }

    @ParameterizedTest
    @CsvSource({"RESERVE,STATUS", "RESERVE,BRANCH", "LOAN,STATUS", "LOAN,BRANCH"})
    void sqlCannotOverwriteAnOccupiedCopysStatusOrBranch(String operation, String edit) {
        Fixture f = fixture();
        claim(operation, f, f.reader(), f.copy());
        assertThatThrownBy(() -> {
            if (edit.equals("STATUS")) jdbc.update("UPDATE material_copies SET status = 'AVAILABLE' WHERE id = ?", f.copy().id());
            else jdbc.update("UPDATE material_copies SET branch_id = ? WHERE id = ?", f.otherBranch(), f.copy().id());
        }).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(branchOf(f.copy())).isEqualTo(f.branch());
        assertState(f.copy(), operation.equals("LOAN") ? "LOANED" : "RESERVED", operation.equals("LOAN") ? 0 : 1,
                operation.equals("LOAN") ? 1 : 0);
    }

    private Outcomes overlap(Supplier<?> firstRequest, Supplier<?> secondRequest, boolean blocking, boolean rollbackFirst) throws Exception {
        return overlap(firstRequest, secondRequest, blocking, rollbackFirst, null);
    }

    private Outcomes overlap(Supplier<?> firstRequest, Supplier<?> secondRequest, boolean blocking,
                             boolean rollbackFirst, Book lockBeforeRequest) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstFinished = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Integer> firstPid = new CompletableFuture<>();
        CompletableFuture<Integer> secondPid = new CompletableFuture<>();
        try {
            Future<Outcome> first = executor.submit(() -> invoke(() -> {
                if (lockBeforeRequest != null) {
                    jdbc.queryForObject("SELECT id FROM material_copies WHERE id = ? FOR UPDATE", Long.class, lockBeforeRequest.id());
                    firstFinished.countDown();
                    await(release);
                }
                Object value = firstRequest.get();
                if (lockBeforeRequest == null) {
                    firstFinished.countDown();
                    await(release);
                }
                return value;
            }, firstPid, rollbackFirst));
            assertThat(firstFinished.await(20, TimeUnit.SECONDS)).as("First operation reached its SQL writes").isTrue();
            Future<Outcome> second = executor.submit(() -> invoke(secondRequest, secondPid, false));
            if (blocking) {
                int waiter = secondPid.get(15, TimeUnit.SECONDS), holder = firstPid.get(15, TimeUnit.SECONDS);
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                boolean blocked = false;
                while (!second.isDone() && System.nanoTime() < deadline) {
                    blocked = Boolean.TRUE.equals(jdbc.queryForObject("SELECT ? = ANY(pg_blocking_pids(?))", Boolean.class, holder, waiter));
                    if (blocked) break;
                    Thread.sleep(20);
                }
                assertThat(blocked).as("Second transaction actually waits for the first in PostgreSQL").isTrue();
            } else second.get(10, TimeUnit.SECONDS);
            release.countDown();
            return new Outcomes(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private Outcome invoke(Supplier<?> operation, CompletableFuture<Integer> pid, boolean rollback) {
        try {
            return new TransactionTemplate(transactions).execute(status -> {
                pid.complete(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                Object value = operation.get();
                if (rollback) status.setRollbackOnly();
                return new Outcome(value, null);
            });
        } catch (RuntimeException error) {
            pid.completeExceptionally(error);
            return new Outcome(null, error);
        }
    }

    private void await(CountDownLatch release) {
        try {
            if (!release.await(25, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out holding the copy lock");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }

    private Object claim(String operation, Fixture f, long reader, Book book) {
        return operation.equals("RESERVE") ? reserve(f, reader, book)
                : loans.create(f.staff(), new CreateLoanRequest(reader, book.id(), null, book.branch(), f.staff(), null));
    }

    private ReservationResponse reserve(Fixture f, long reader, Book book) {
        return reservations.create(f.staff(), new CreateReservationRequest(reader, book.material(), book.id(), book.branch()));
    }

    private Object reserveAutomatically(Fixture f, long reader) {
        return reservations.create(f.staff(), new CreateReservationRequest(reader, f.copy().material(), null, f.branch()));
    }

    private LoanResponse issueReserved(Fixture f, long reservation) {
        return loans.create(f.staff(), new CreateLoanRequest(f.reader(), f.copy().id(), reservation, f.branch(), f.staff(), null));
    }

    private long ready(Fixture f) {
        long id = reserve(f, f.reader(), f.copy()).id();
        reservations.markReadyForPickup(f.staff(), id);
        return id;
    }

    private Object editCopy(String edit, Fixture f) {
        return edit.equals("STATUS") ? copies.changeStatus(f.copy().id(), new ChangeMaterialCopyStatusRequest(CopyStatus.UNDER_REPAIR))
                : copies.update(f.copy().id(), new UpdateMaterialCopyRequest(f.otherBranch(), null, null));
    }

    private void sqlLoan(Fixture f, long reader, Book book, Long reservation, long branch) {
        sqlLoan(f, reader, book, reservation, branch, "ACTIVE");
    }

    private void sqlLoan(Fixture f, long reader, Book book, Long reservation, long branch, String status) {
        jdbc.update("""
                INSERT INTO loans(user_id, copy_id, reservation_id, branch_id, issued_by_user_id, due_at, status)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP + INTERVAL '7 days', CAST(? AS loan_status))
                """, reader, book.id(), reservation, branch, f.staff(), status);
    }

    private void assertSuccess(Outcome outcome) {
        assertThat(outcome.error()).isNull();
        assertThat(outcome.value()).isNotNull();
    }

    private void assertResponseCopy(Object response, CopyStatus status) {
        CopyStatus actual = response instanceof LoanResponse loan ? loan.copy().status() : ((ReservationResponse) response).copy().status();
        assertThat(actual).isEqualTo(status);
    }

    private void assertState(Book book, String status, long reservations, long loans) {
        assertThat(jdbc.queryForObject("SELECT status::text FROM material_copies WHERE id = ?", String.class, book.id())).isEqualTo(status);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservations WHERE copy_id = ? AND status IN ('ACTIVE', 'READY_FOR_PICKUP')",
                Long.class, book.id())).isEqualTo(reservations);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM loans WHERE copy_id = ? AND status IN ('ACTIVE', 'OVERDUE', 'LOST')",
                Long.class, book.id())).isEqualTo(loans);
    }

    private long branchOf(Book book) { return jdbc.queryForObject("SELECT branch_id FROM material_copies WHERE id = ?", Long.class, book.id()); }
    private String reservationStatus(long id) { return jdbc.queryForObject("SELECT status::text FROM reservations WHERE id = ?", String.class, id); }
    private long totalLoans(Book book) { return jdbc.queryForObject("SELECT count(*) FROM loans WHERE copy_id = ?", Long.class, book.id()); }

    private Fixture fixture() {
        long library = jdbc.queryForObject("INSERT INTO libraries(code, name) VALUES (?, 'Copy concurrency') RETURNING id", Long.class, "copy-race-" + SEQUENCE.incrementAndGet());
        long branch = branch(library), otherBranch = branch(library);
        long reader = user(), otherReader = user(), staff = user();
        jdbc.update("INSERT INTO user_roles(user_id, role_id) SELECT ?, id FROM roles WHERE code = 'LIBRARIAN'", staff);
        return new Fixture(reader, otherReader, staff, branch, otherBranch, book(branch));
    }

    private long branch(long library) {
        long branch = jdbc.queryForObject("""
                INSERT INTO branches(library_id, name, address) VALUES (?, ?, '{"city":"Test","street":"Main","building":"1"}') RETURNING id
                """, Long.class, library, "Copy branch " + SEQUENCE.incrementAndGet());
        jdbc.update("INSERT INTO library_rules(branch_id, valid_from) VALUES (?, CURRENT_TIMESTAMP - INTERVAL '1 day')", branch);
        return branch;
    }

    private long user() {
        long seq = SEQUENCE.incrementAndGet();
        return jdbc.queryForObject("""
                INSERT INTO users(email, phone, password_hash, first_name, last_name, status)
                VALUES (?, ?, 'test-hash', 'Copy', 'Reader', 'ACTIVE') RETURNING id
                """, Long.class, "copy-race-" + seq + "@example.com", "+7904%07d".formatted(seq));
    }

    private Book book(long branch) {
        long material = jdbc.queryForObject("INSERT INTO materials(title) VALUES ('Concurrent book') RETURNING id", Long.class);
        return copy(material, branch);
    }

    private Book copy(long material, long branch) {
        long id = jdbc.queryForObject("INSERT INTO material_copies(material_id, branch_id, inventory_number) VALUES (?, ?, ?) RETURNING id",
                Long.class, material, branch, "race-copy-" + SEQUENCE.incrementAndGet());
        return new Book(id, material, branch);
    }

    private record Book(long id, long material, long branch) {}
    private record Fixture(long reader, long otherReader, long staff, long branch, long otherBranch, Book copy) {}
    private record Outcome(Object value, RuntimeException error) {}
    private record Outcomes(Outcome first, Outcome second) {}
    @TestConfiguration(proxyBeanMethods = false)
    static class JsonConfiguration {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
    }
}
