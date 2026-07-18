package me.ifmo.backend.fine.application.impl;

import me.ifmo.backend.fine.web.request.CancelFineRequest;
import me.ifmo.backend.fine.web.request.CreateFineRequest;
import me.ifmo.backend.fine.web.request.FineSearchRequest;
import me.ifmo.backend.fine.web.response.FineResponse;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.fine.domain.Fine;
import me.ifmo.backend.fine.domain.FineTariff;
import me.ifmo.backend.circulation.domain.Loan;
import me.ifmo.backend.catalog.domain.MaterialCopy;
import me.ifmo.backend.user.domain.User;
import me.ifmo.backend.fine.domain.enums.FineStatus;
import me.ifmo.backend.fine.domain.enums.FineTariffStatus;
import me.ifmo.backend.circulation.domain.enums.LoanStatus;
import me.ifmo.backend.user.domain.enums.RoleCode;
import me.ifmo.backend.user.domain.enums.UserStatus;
import me.ifmo.backend.fine.domain.enums.ViolationType;
import me.ifmo.backend.shared.error.BusinessRuleException;
import me.ifmo.backend.shared.error.DuplicateResourceException;
import me.ifmo.backend.fine.mapper.FineMapper;
import me.ifmo.backend.fine.persistence.FineRepository;
import me.ifmo.backend.fine.persistence.FineTariffRepository;
import me.ifmo.backend.circulation.persistence.LoanRepository;
import me.ifmo.backend.catalog.persistence.MaterialCopyRepository;
import me.ifmo.backend.user.persistence.UserRepository;
import me.ifmo.backend.user.persistence.UserRoleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Fine service")
@ExtendWith(MockitoExtension.class)
class FineServiceImplTest {

    private static final long USER_ID = 1L;
    private static final long OTHER_USER_ID = 2L;
    private static final long STAFF_ID = 3L;
    private static final long LOAN_ID = 4L;
    private static final long COPY_ID = 5L;
    private static final long TARIFF_ID = 6L;

    @Mock
    private FineRepository fineRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private LoanRepository loanRepository;
    @Mock
    private MaterialCopyRepository materialCopyRepository;
    @Mock
    private FineTariffRepository fineTariffRepository;
    @Mock
    private UserRoleRepository userRoleRepository;
    @Mock
    private FineMapper fineMapper;

    @InjectMocks
    private FineServiceImpl service;

    private void grantStaff() {
        when(userRoleRepository.findRoleCodesByUser_Id(FineServiceImplTest.STAFF_ID)).thenReturn(List.of(RoleCode.LIBRARIAN));
    }

    private User activeUser() {
        return User.builder().id(FineServiceImplTest.USER_ID).status(UserStatus.ACTIVE).build();
    }

    @Test
    @DisplayName("Creates active overdue fine using loan material copy")
    void createCreatesActiveOverdueFineUsingLoanCopy() {
        User user = activeUser();
        MaterialCopy copy = MaterialCopy.builder().id(COPY_ID).build();

        Loan loan = Loan.builder().id(LOAN_ID).user(user).copy(copy).status(LoanStatus.OVERDUE).build();

        FineTariff tariff = FineTariff.builder().id(TARIFF_ID).violationType(ViolationType.OVERDUE).status(FineTariffStatus.ACTIVE).build();

        BigDecimal amount = new BigDecimal("150.00");
        Fine fine = Fine.builder().user(user).loan(loan).copy(copy).tariff(tariff).amount(amount).build();
        FineResponse response = org.mockito.Mockito.mock(FineResponse.class);

        grantStaff();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(loanRepository.findById(LOAN_ID)).thenReturn(Optional.of(loan));
        when(fineRepository.findByLoan_IdAndReasonAndStatus(LOAN_ID, ViolationType.OVERDUE, FineStatus.ACTIVE)).thenReturn(Optional.empty());
        when(fineTariffRepository.findById(TARIFF_ID)).thenReturn(Optional.of(tariff));
        when(fineMapper.toEntity(user, loan, copy, tariff, ViolationType.OVERDUE, amount)).thenReturn(fine);
        when(fineRepository.save(fine)).thenReturn(fine);
        when(fineMapper.toResponse(fine)).thenReturn(response);

        FineResponse result = service.create(STAFF_ID, new CreateFineRequest(USER_ID, LOAN_ID, null, TARIFF_ID, ViolationType.OVERDUE, amount));

        assertThat(result).isSameAs(response);
        assertThat(fine.getStatus()).isEqualTo(FineStatus.ACTIVE);
        verify(materialCopyRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("Rejects fine creation by non-staff actor")
    void createRejectsNonStaffActor() {
        when(userRoleRepository.findRoleCodesByUser_Id(USER_ID)).thenReturn(List.of(RoleCode.READER));

        assertThatThrownBy(() -> service.create(USER_ID, new CreateFineRequest(USER_ID, LOAN_ID, null, null, ViolationType.OVERDUE, BigDecimal.ONE)))
                .isInstanceOf(AccessDeniedException.class).hasMessage("Access is denied");

        verify(userRepository, never()).findById(anyLong());
        verify(fineRepository, never()).save(any());
    }

    @Test
    @DisplayName("Rejects non-positive fine amount before repository access")
    void createRejectsNonPositiveAmountBeforeRepositoryAccess() {
        grantStaff();

        assertThatThrownBy(() -> service.create(STAFF_ID, new CreateFineRequest(USER_ID, null, null, null, ViolationType.OTHER, BigDecimal.ZERO)))
                .isInstanceOf(BusinessRuleException.class).hasMessage("Fine amount must be positive");

        verify(userRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("Rejects duplicate active fine for same loan and reason")
    void createRejectsDuplicateActiveFineForSameLoanAndReason() {
        User user = activeUser();
        Loan loan = Loan.builder().id(LOAN_ID).user(user).build();
        Fine existing = Fine.builder().id(20L).status(FineStatus.ACTIVE).build();

        grantStaff();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(loanRepository.findById(LOAN_ID)).thenReturn(Optional.of(loan));
        when(fineRepository.findByLoan_IdAndReasonAndStatus(LOAN_ID, ViolationType.OVERDUE, FineStatus.ACTIVE)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(STAFF_ID, new CreateFineRequest(USER_ID, LOAN_ID, null, null, ViolationType.OVERDUE, BigDecimal.TEN)))
                .isInstanceOf(DuplicateResourceException.class).hasMessageContaining("Active fine for loan id");

        verify(fineRepository, never()).save(any());
    }

    @Test
    @DisplayName("Requires material copy for damage fine")
    void createRequiresCopyForDamageFine() {
        User user = activeUser();
        grantStaff();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.create(STAFF_ID, new CreateFineRequest(USER_ID, null, null, null, ViolationType.DAMAGE, BigDecimal.TEN)))
                .isInstanceOf(BusinessRuleException.class).hasMessage("Damage or loss fine must be linked to material copy");

        verify(fineRepository, never()).save(any());
    }

    @Test
    @DisplayName("Rejects archived fine tariff")
    void createRejectsArchivedTariff() {
        User user = activeUser();
        FineTariff tariff = FineTariff.builder().id(TARIFF_ID).violationType(ViolationType.OTHER).status(FineTariffStatus.ARCHIVED).build();

        grantStaff();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(fineTariffRepository.findById(TARIFF_ID)).thenReturn(Optional.of(tariff));

        assertThatThrownBy(() -> service.create(STAFF_ID, new CreateFineRequest(USER_ID, null, null, TARIFF_ID, ViolationType.OTHER, BigDecimal.TEN)))
                .isInstanceOf(BusinessRuleException.class).hasMessage("Archived fine tariff cannot be used");

        verify(fineRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cancels fine, trims reason, and sets cancelled status")
    void cancelTrimsReasonAndMarksFineCancelled() {
        Fine fine = Fine.builder().id(10L).status(FineStatus.ACTIVE).build();
        FineResponse response = org.mockito.Mockito.mock(FineResponse.class);

        grantStaff();
        when(fineRepository.findById(10L)).thenReturn(Optional.of(fine));
        when(fineRepository.save(fine)).thenReturn(fine);
        when(fineMapper.toResponse(fine)).thenReturn(response);

        LocalDateTime before = LocalDateTime.now();
        FineResponse result = service.cancel(STAFF_ID, 10L, new CancelFineRequest("  entered by mistake  "));

        assertThat(result).isSameAs(response);
        assertThat(fine.getStatus()).isEqualTo(FineStatus.CANCELLED);
        assertThat(fine.getCancellationReason()).isEqualTo("entered by mistake");
        assertThat(fine.getCancelledAt()).isAfterOrEqualTo(before);
    }

    @Test
    @DisplayName("Marks fine paid and sets payment timestamp")
    void markPaidUpdatesStatusAndTimestamp() {
        Fine fine = Fine.builder().id(10L).status(FineStatus.ACTIVE).build();
        FineResponse response = org.mockito.Mockito.mock(FineResponse.class);

        grantStaff();
        when(fineRepository.findById(10L)).thenReturn(Optional.of(fine));
        when(fineRepository.save(fine)).thenReturn(fine);
        when(fineMapper.toResponse(fine)).thenReturn(response);

        LocalDateTime before = LocalDateTime.now();
        FineResponse result = service.markPaid(STAFF_ID, 10L);

        assertThat(result).isSameAs(response);
        assertThat(fine.getStatus()).isEqualTo(FineStatus.PAID);
        assertThat(fine.getPaidAt()).isAfterOrEqualTo(before);
    }

    @Test
    @DisplayName("Rejects unrelated reader requesting fine by ID")
    void getFineByIdRejectsUnrelatedReader() {
        Fine fine = Fine.builder().id(10L).user(activeUser()).status(FineStatus.ACTIVE).build();
        when(fineRepository.findById(10L)).thenReturn(Optional.of(fine));
        when(userRoleRepository.findRoleCodesByUser_Id(OTHER_USER_ID)).thenReturn(List.of(RoleCode.READER));

        assertThatThrownBy(() -> service.getFineById(OTHER_USER_ID, 10L))
                .isInstanceOf(AccessDeniedException.class).hasMessage("Access is denied");

        verify(fineMapper, never()).toResponse(any());
    }

    @Test
    @DisplayName("Allows fine owner to get fine by ID")
    void getFineByIdAllowsOwner() {
        Fine fine = Fine.builder().id(10L).user(activeUser()).status(FineStatus.ACTIVE).build();
        FineResponse response = org.mockito.Mockito.mock(FineResponse.class);
        when(fineRepository.findById(10L)).thenReturn(Optional.of(fine));
        when(fineMapper.toResponse(fine)).thenReturn(response);

        assertThat(service.getFineById(USER_ID, 10L)).isSameAs(response);
        verify(userRoleRepository, never()).findRoleCodesByUser_Id(anyLong());
    }

    @Test
    @DisplayName("Restricts reader search to own fines and maps page")
    void searchRestrictsReaderToOwnFinesAndMapsPage() {
        Fine fine = Fine.builder().id(10L).user(activeUser()).status(FineStatus.ACTIVE).build();
        FineResponse response = org.mockito.Mockito.mock(FineResponse.class);
        PageRequest pageable = PageRequest.of(0, 20);
        LocalDateTime now = LocalDateTime.now();
        FineSearchRequest request = new FineSearchRequest(USER_ID, LOAN_ID, COPY_ID, ViolationType.OVERDUE, FineStatus.ACTIVE, now.minusDays(1), now);

        when(userRoleRepository.findRoleCodesByUser_Id(USER_ID)).thenReturn(List.of(RoleCode.READER));
        when(fineRepository.findAll(org.mockito.ArgumentMatchers.<Specification<Fine>>any(), eq(pageable))).thenReturn(new PageImpl<>(List.of(fine), pageable, 1));
        when(fineMapper.toResponse(fine)).thenReturn(response);

        PageResponse<FineResponse> result = service.search(USER_ID, request, pageable);

        assertThat(result.content()).containsExactly(response);
        assertThat(result.totalElements()).isEqualTo(1);
    }
}
