package me.ifmo.backend.circulation.application.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.ifmo.backend.catalog.mapper.MaterialCopyMapperImpl;
import me.ifmo.backend.circulation.application.LoanService;
import me.ifmo.backend.circulation.domain.Loan;
import me.ifmo.backend.circulation.domain.enums.LoanStatus;
import me.ifmo.backend.circulation.mapper.LoanMapper;
import me.ifmo.backend.circulation.mapper.LoanMapperImpl;
import me.ifmo.backend.circulation.web.request.CreateLoanRequest;
import me.ifmo.backend.circulation.web.request.ReturnLoanRequest;
import me.ifmo.backend.library.mapper.BranchAddressJsonMapper;
import me.ifmo.backend.library.mapper.BranchMapperImpl;
import me.ifmo.backend.library.mapper.LibraryMapperImpl;
import me.ifmo.backend.shared.exceptions.BusinessRuleException;
import me.ifmo.backend.shared.exceptions.ResourceInUseException;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@DataJpaTest(showSql = false, properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({LoanServiceImpl.class, LoanMapperImpl.class, UserMapperImpl.class, RoleMapperImpl.class,
        MaterialCopyMapperImpl.class, BranchMapperImpl.class, LibraryMapperImpl.class,
        BranchAddressJsonMapper.class, ReservationLoanIntegrationTest.JsonConfiguration.class})
@Testcontainers(disabledWithoutDocker = true)
// Service calls must commit/roll back themselves; assertions read the committed database state.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ReservationLoanIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final AtomicLong SEQUENCE = new AtomicLong();

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private LoanService service;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoSpyBean
    private LoanMapper mapper;

    @Test
    void commitsReservationCopyAndLoanTogetherAndAllowsReturn() {
        Fixture fixture = readyReservation();

        var response = service.create(fixture.staffId(), fixture.request(null));

        assertThat(response.reservationId()).isEqualTo(fixture.reservationId());
        assertThat(response.status()).isEqualTo(LoanStatus.ACTIVE);
        assertState(fixture, "USED", "LOANED", 1);
        assertThat(jdbc.queryForObject("SELECT id FROM loans WHERE reservation_id = ?", Long.class,
                fixture.reservationId())).isEqualTo(response.id());

        service.returnLoan(fixture.staffId(), response.id(), new ReturnLoanRequest(null, null));

        assertState(fixture, "USED", "AVAILABLE", 1);
        assertThat(jdbc.queryForObject("SELECT status::text FROM loans WHERE id = ?", String.class,
                response.id())).isEqualTo("RETURNED");
    }

    @Test
    void repeatedIssueDoesNotCreateAnotherLoanOrChangeStatuses() {
        Fixture fixture = readyReservation();
        service.create(fixture.staffId(), fixture.request(null));

        assertThatThrownBy(() -> service.create(fixture.staffId(), fixture.request(null)))
                .isInstanceOf(ResourceInUseException.class);

        assertState(fixture, "USED", "LOANED", 1);
    }

    @Test
    void invalidDueDateRollsBackReservationStatus() {
        Fixture fixture = readyReservation();

        assertThatThrownBy(() -> service.create(fixture.staffId(),
                fixture.request(LocalDateTime.now().minusDays(1))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Loan dueAt must be in the future");

        assertState(fixture, "READY_FOR_PICKUP", "RESERVED", 0);
    }

    @Test
    void failureAfterLoanInsertRollsBackLoanReservationAndCopy() {
        Fixture fixture = readyReservation();
        doThrow(new IllegalStateException("Simulated response failure"))
                .when(mapper).toResponse(any(Loan.class));

        assertThatThrownBy(() -> service.create(fixture.staffId(), fixture.request(null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Simulated response failure");

        assertState(fixture, "READY_FOR_PICKUP", "RESERVED", 0);
    }

    @Test
    void cannotIssueReservedCopyWithoutItsReservation() {
        Fixture fixture = readyReservation();
        var request = new CreateLoanRequest(fixture.readerId(), fixture.copyId(), null,
                fixture.branchId(), fixture.staffId(), null);

        assertThatThrownBy(() -> service.create(fixture.staffId(), request))
                .isInstanceOf(ResourceInUseException.class);

        assertState(fixture, "READY_FOR_PICKUP", "RESERVED", 0);
    }

    private void assertState(Fixture fixture, String reservationStatus, String copyStatus, long loanCount) {
        assertThat(jdbc.queryForObject("SELECT status::text FROM reservations WHERE id = ?", String.class,
                fixture.reservationId())).isEqualTo(reservationStatus);
        assertThat(jdbc.queryForObject("SELECT status::text FROM material_copies WHERE id = ?", String.class,
                fixture.copyId())).isEqualTo(copyStatus);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM loans WHERE copy_id = ?", Long.class,
                fixture.copyId())).isEqualTo(loanCount);
    }

    private Fixture readyReservation() {
        long sequence = SEQUENCE.incrementAndGet();
        Long libraryId = jdbc.queryForObject("INSERT INTO libraries (code, name) VALUES (?, 'Test library') RETURNING id",
                Long.class, "test-" + sequence);
        Long branchId = jdbc.queryForObject("""
                INSERT INTO branches (library_id, name, address)
                VALUES (?, 'Test branch', '{"city":"Test","street":"Test","building":"1"}'::jsonb)
                RETURNING id
                """, Long.class, libraryId);
        jdbc.update("INSERT INTO library_rules (branch_id, valid_from) VALUES (?, CURRENT_TIMESTAMP - INTERVAL '1 day')",
                branchId);
        Long readerId = user(sequence * 2);
        Long staffId = user(sequence * 2 + 1);
        jdbc.update("INSERT INTO user_roles (user_id, role_id) SELECT ?, id FROM roles WHERE code = 'LIBRARIAN'",
                staffId);
        Long materialId = jdbc.queryForObject("INSERT INTO materials (title) VALUES ('Test book') RETURNING id", Long.class);
        Long copyId = jdbc.queryForObject("""
                INSERT INTO material_copies (material_id, branch_id, inventory_number) VALUES (?, ?, ?) RETURNING id
                """, Long.class, materialId, branchId, "copy-" + sequence);
        // Insert ACTIVE first so the production reservation trigger reserves the copy.
        Long reservationId = jdbc.queryForObject("""
                INSERT INTO reservations (user_id, material_id, copy_id, branch_id, expires_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP + INTERVAL '1 day') RETURNING id
                """, Long.class, readerId, materialId, copyId, branchId);
        jdbc.update("UPDATE reservations SET status = 'READY_FOR_PICKUP', ready_at = CURRENT_TIMESTAMP WHERE id = ?",
                reservationId);
        return new Fixture(readerId, staffId, branchId, copyId, reservationId);
    }

    private Long user(long sequence) {
        return jdbc.queryForObject("""
                INSERT INTO users (email, phone, password_hash, first_name, last_name, status)
                VALUES (?, ?, 'test-hash', 'Test', 'User', 'ACTIVE') RETURNING id
                """, Long.class, "user" + sequence + "@example.com", "+7900%07d".formatted(sequence));
    }

    private record Fixture(Long readerId, Long staffId, Long branchId, Long copyId, Long reservationId) {
        CreateLoanRequest request(LocalDateTime dueAt) {
            return new CreateLoanRequest(readerId, copyId, reservationId, branchId, staffId, dueAt);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class JsonConfiguration {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
