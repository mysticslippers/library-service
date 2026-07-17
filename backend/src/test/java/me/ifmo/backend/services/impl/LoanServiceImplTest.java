package me.ifmo.backend.services.impl;

import me.ifmo.backend.dto.circulation.request.CreateLoanRequest;
import me.ifmo.backend.dto.circulation.request.LoanSearchRequest;
import me.ifmo.backend.dto.circulation.request.RenewLoanRequest;
import me.ifmo.backend.dto.circulation.request.ReturnLoanRequest;
import me.ifmo.backend.dto.circulation.response.LoanResponse;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.library.internal.domain.Branch;
import me.ifmo.backend.library.internal.domain.LibraryRule;
import me.ifmo.backend.entities.Loan;
import me.ifmo.backend.entities.Material;
import me.ifmo.backend.entities.MaterialCopy;
import me.ifmo.backend.entities.Reservation;
import me.ifmo.backend.entities.User;
import me.ifmo.backend.library.internal.domain.enums.BranchStatus;
import me.ifmo.backend.entities.enums.CopyStatus;
import me.ifmo.backend.entities.enums.FineStatus;
import me.ifmo.backend.library.internal.domain.enums.LibraryRuleStatus;
import me.ifmo.backend.entities.enums.LoanStatus;
import me.ifmo.backend.entities.enums.MaterialStatus;
import me.ifmo.backend.entities.enums.ReservationStatus;
import me.ifmo.backend.entities.enums.RoleCode;
import me.ifmo.backend.entities.enums.UserBlockStatus;
import me.ifmo.backend.entities.enums.UserStatus;
import me.ifmo.backend.shared.error.BusinessRuleException;
import me.ifmo.backend.mappers.LoanMapper;
import me.ifmo.backend.library.internal.persistence.BranchRepository;
import me.ifmo.backend.repositories.FineRepository;
import me.ifmo.backend.library.internal.persistence.LibraryRuleRepository;
import me.ifmo.backend.repositories.LoanRepository;
import me.ifmo.backend.repositories.MaterialCopyRepository;
import me.ifmo.backend.repositories.ReservationRepository;
import me.ifmo.backend.repositories.UserBlockRepository;
import me.ifmo.backend.repositories.UserRepository;
import me.ifmo.backend.repositories.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanServiceImplTest {

    private static final long READER_ID = 1L;
    private static final long STAFF_ID = 2L;
    private static final long COPY_ID = 3L;
    private static final long BRANCH_ID = 4L;

    @Mock
    private LoanRepository loanRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MaterialCopyRepository materialCopyRepository;
    @Mock
    private BranchRepository branchRepository;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private LibraryRuleRepository libraryRuleRepository;
    @Mock
    private UserBlockRepository userBlockRepository;
    @Mock
    private FineRepository fineRepository;
    @Mock
    private UserRoleRepository userRoleRepository;
    @Mock
    private LoanMapper loanMapper;

    @InjectMocks
    private LoanServiceImpl service;

    private User activeUser(long id) {
        return User.builder().id(id).status(UserStatus.ACTIVE).build();
    }

    private Branch activeBranch() {
        return Branch.builder().id(BRANCH_ID).status(BranchStatus.ACTIVE).build();
    }

    private MaterialCopy availableCopy(Branch branch) {
        Material material = Material.builder().id(20L).status(MaterialStatus.ACTIVE).build();
        return MaterialCopy.builder().id(COPY_ID).branch(branch).material(material).status(CopyStatus.AVAILABLE).build();
    }

    private LibraryRule libraryRule() {
        return LibraryRule.builder().branch(activeBranch()).maxActiveLoans(5).defaultLoanDays(14).renewalAllowed(true).maxRenewalCount(2).renewalPeriodDays(7).status(LibraryRuleStatus.ACTIVE).build();
    }

    @Test
    void createCreatesActiveLoanAndMarksCopyAsLoaned() {
        User reader = activeUser(READER_ID);
        User staff = activeUser(STAFF_ID);
        Branch branch = activeBranch();
        MaterialCopy copy = availableCopy(branch);
        LibraryRule rule = libraryRule();
        LoanResponse response = org.mockito.Mockito.mock(LoanResponse.class);

        grantStaff();
        when(userRepository.findById(READER_ID)).thenReturn(Optional.of(reader));
        when(userRepository.findById(STAFF_ID)).thenReturn(Optional.of(staff));
        allowBorrowing(reader);
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch));
        when(materialCopyRepository.findById(COPY_ID)).thenReturn(Optional.of(copy));
        when(loanRepository.findByCopy_IdAndStatusIn(eq(COPY_ID), anyCollection())).thenReturn(Optional.empty());
        when(libraryRuleRepository.findActualByBranchIdAndStatus(eq(BRANCH_ID), eq(LibraryRuleStatus.ACTIVE), any(LocalDateTime.class))).thenReturn(Optional.of(rule));

        when(loanMapper.toEntity(eq(reader), eq(copy), isNull(), eq(branch), eq(staff), any(LocalDateTime.class)))
                .thenAnswer(invocation -> Loan.builder()
                        .user(reader)
                        .copy(copy)
                        .branch(branch)
                        .issuedByUser(staff)
                        .dueAt(invocation.getArgument(5))
                        .build());

        when(loanRepository.save(any(Loan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(loanMapper.toResponse(any(Loan.class))).thenReturn(response);

        LocalDateTime before = LocalDateTime.now();
        LoanResponse result = service.create(STAFF_ID, new CreateLoanRequest(READER_ID, COPY_ID, null, BRANCH_ID, STAFF_ID, null));

        assertThat(result).isSameAs(response);
        assertThat(copy.getStatus()).isEqualTo(CopyStatus.LOANED);
        var savedLoan = org.mockito.ArgumentCaptor.forClass(Loan.class);
        verify(loanRepository).save(savedLoan.capture());
        assertThat(savedLoan.getValue().getStatus()).isEqualTo(LoanStatus.ACTIVE);
        assertThat(savedLoan.getValue().getRenewalCount()).isZero();
        assertThat(savedLoan.getValue().getDueAt()).isAfterOrEqualTo(before.plusDays(14));
    }

    @Test
    void createRejectsNonStaffActorBeforeReadingDomainData() {
        when(userRoleRepository.findRoleCodesByUser_Id(READER_ID)).thenReturn(List.of(RoleCode.READER));

        assertThatThrownBy(() -> service.create(READER_ID, new CreateLoanRequest(READER_ID, COPY_ID, null, BRANCH_ID, READER_ID, null)))
                .isInstanceOf(AccessDeniedException.class).hasMessage("Access is denied");

        verify(userRepository, never()).findById(anyLong());
        verify(loanRepository, never()).save(any());
    }

    @Test
    void createRejectsBlockedReader() {
        User reader = activeUser(READER_ID);
        User staff = activeUser(STAFF_ID);

        grantStaff();
        when(userRepository.findById(READER_ID)).thenReturn(Optional.of(reader));
        when(userRepository.findById(STAFF_ID)).thenReturn(Optional.of(staff));
        when(userBlockRepository.existsByUser_IdAndStatus(READER_ID, UserBlockStatus.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> service.create(STAFF_ID, new CreateLoanRequest(READER_ID, COPY_ID, null, BRANCH_ID, STAFF_ID, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessage("User has active block");

        verify(branchRepository, never()).findById(anyLong());
        verify(loanRepository, never()).save(any());
    }

    @Test
    void createConvertsReadyReservationToLoan() {
        User reader = activeUser(READER_ID);
        User staff = activeUser(STAFF_ID);
        Branch branch = activeBranch();
        MaterialCopy copy = availableCopy(branch);
        copy.setStatus(CopyStatus.RESERVED);

        Reservation reservation = Reservation.builder().id(30L).user(reader).copy(copy).branch(branch).status(ReservationStatus.READY_FOR_PICKUP)
                .expiresAt(LocalDateTime.now().plusHours(1)).build();

        Loan loan = Loan.builder().user(reader).copy(copy).reservation(reservation).branch(branch).build();

        grantStaff();
        when(userRepository.findById(READER_ID)).thenReturn(Optional.of(reader));
        when(userRepository.findById(STAFF_ID)).thenReturn(Optional.of(staff));
        allowBorrowing(reader);
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch));
        when(materialCopyRepository.findById(COPY_ID)).thenReturn(Optional.of(copy));
        when(loanRepository.findByCopy_IdAndStatusIn(eq(COPY_ID), anyCollection())).thenReturn(Optional.empty());
        when(libraryRuleRepository.findActualByBranchIdAndStatus(eq(BRANCH_ID), eq(LibraryRuleStatus.ACTIVE), any(LocalDateTime.class))).thenReturn(Optional.of(libraryRule()));
        when(reservationRepository.findById(30L)).thenReturn(Optional.of(reservation));
        when(loanRepository.findByReservation_Id(30L)).thenReturn(Optional.empty());

        when(loanMapper.toEntity(eq(reader), eq(copy), eq(reservation), eq(branch), eq(staff), any(LocalDateTime.class))).thenReturn(loan);
        when(loanRepository.save(loan)).thenReturn(loan);

        service.create(STAFF_ID, new CreateLoanRequest(READER_ID, COPY_ID, 30L, BRANCH_ID, STAFF_ID, null));

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.USED);
        assertThat(copy.getStatus()).isEqualTo(CopyStatus.LOANED);
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.ACTIVE);
    }

    @Test
    void returnLoanMarksLoanReturnedAndMakesCopyAvailableByDefault() {
        MaterialCopy copy = MaterialCopy.builder().id(COPY_ID).status(CopyStatus.LOANED).build();
        Loan loan = Loan.builder().id(10L).status(LoanStatus.ACTIVE).copy(copy).build();
        LoanResponse response = org.mockito.Mockito.mock(LoanResponse.class);

        grantStaff();
        when(loanRepository.findById(10L)).thenReturn(Optional.of(loan));
        when(loanRepository.save(loan)).thenReturn(loan);
        when(loanMapper.toResponse(loan)).thenReturn(response);

        LocalDateTime before = LocalDateTime.now();
        LoanResponse result = service.returnLoan(STAFF_ID, 10L, new ReturnLoanRequest(null, null));

        assertThat(result).isSameAs(response);
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.RETURNED);
        assertThat(loan.getReturnedAt()).isAfterOrEqualTo(before);
        assertThat(copy.getStatus()).isEqualTo(CopyStatus.AVAILABLE);
    }

    @Test
    void returnLoanRejectsReservedResultingCopyStatus() {
        MaterialCopy copy = MaterialCopy.builder().id(COPY_ID).status(CopyStatus.LOANED).build();
        Loan loan = Loan.builder().id(10L).status(LoanStatus.ACTIVE).copy(copy).build();

        grantStaff();
        when(loanRepository.findById(10L)).thenReturn(Optional.of(loan));

        assertThatThrownBy(() -> service.returnLoan(STAFF_ID, 10L, new ReturnLoanRequest(CopyStatus.RESERVED, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessage("Returned copy cannot become 'RESERVED'");

        verify(loanRepository, never()).save(any());
    }

    @Test
    void renewUsesDefaultPeriodAndIncrementsRenewalCount() {
        User reader = activeUser(READER_ID);
        Branch branch = activeBranch();
        LocalDateTime originalDueAt = LocalDateTime.now().plusDays(3);

        Loan loan = Loan.builder().id(10L).user(reader).branch(branch).status(LoanStatus.ACTIVE).dueAt(originalDueAt).renewalCount(0).build();

        LibraryRule rule = libraryRule();
        LoanResponse response = org.mockito.Mockito.mock(LoanResponse.class);

        when(loanRepository.findById(10L)).thenReturn(Optional.of(loan));
        allowBorrowing(reader);
        when(libraryRuleRepository.findActualByBranchIdAndStatus(eq(BRANCH_ID), eq(LibraryRuleStatus.ACTIVE), any(LocalDateTime.class))).thenReturn(Optional.of(rule));
        when(loanRepository.save(loan)).thenReturn(loan);
        when(loanMapper.toResponse(loan)).thenReturn(response);

        LoanResponse result = service.renew(READER_ID, 10L, new RenewLoanRequest(null));

        assertThat(result).isSameAs(response);
        assertThat(loan.getDueAt()).isEqualTo(originalDueAt.plusDays(7));
        assertThat(loan.getRenewalCount()).isEqualTo(1);
    }

    @Test
    void renewRejectsLoanWhoseDueDateHasPassed() {
        User reader = activeUser(READER_ID);
        Loan loan = Loan.builder().id(10L).user(reader).status(LoanStatus.ACTIVE).dueAt(LocalDateTime.now().minusMinutes(1)).build();
        when(loanRepository.findById(10L)).thenReturn(Optional.of(loan));

        assertThatThrownBy(() -> service.renew(READER_ID, 10L, new RenewLoanRequest(7)))
                .isInstanceOf(BusinessRuleException.class).hasMessage("Overdue loan cannot be renewed");

        verify(libraryRuleRepository, never()).findActualByBranchIdAndStatus(anyLong(), any(), any(LocalDateTime.class));
        verify(loanRepository, never()).save(any());
    }

    @Test
    void markLostUpdatesBothLoanAndCopy() {
        MaterialCopy copy = MaterialCopy.builder().id(COPY_ID).status(CopyStatus.LOANED).build();
        Loan loan = Loan.builder().id(10L).status(LoanStatus.OVERDUE).copy(copy).build();
        LoanResponse response = org.mockito.Mockito.mock(LoanResponse.class);

        grantStaff();
        when(loanRepository.findById(10L)).thenReturn(Optional.of(loan));
        when(loanRepository.save(loan)).thenReturn(loan);
        when(loanMapper.toResponse(loan)).thenReturn(response);

        LoanResponse result = service.markLost(STAFF_ID, 10L);

        assertThat(result).isSameAs(response);
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.LOST);
        assertThat(copy.getStatus()).isEqualTo(CopyStatus.LOST);
    }

    @Test
    void markOverdueChangesActivePastDueLoan() {
        Loan loan = Loan.builder().id(10L).status(LoanStatus.ACTIVE).dueAt(LocalDateTime.now().minusMinutes(1)).build();
        LoanResponse response = org.mockito.Mockito.mock(LoanResponse.class);

        grantStaff();
        when(loanRepository.findById(10L)).thenReturn(Optional.of(loan));
        when(loanRepository.save(loan)).thenReturn(loan);
        when(loanMapper.toResponse(loan)).thenReturn(response);

        LoanResponse result = service.markOverdue(STAFF_ID, 10L);

        assertThat(result).isSameAs(response);
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.OVERDUE);
    }

    @Test
    void getLoanByIdAllowsOwner() {
        User reader = activeUser(READER_ID);
        Loan loan = Loan.builder().id(10L).user(reader).status(LoanStatus.ACTIVE).build();
        LoanResponse response = org.mockito.Mockito.mock(LoanResponse.class);
        when(loanRepository.findById(10L)).thenReturn(Optional.of(loan));
        when(loanMapper.toResponse(loan)).thenReturn(response);

        assertThat(service.getLoanById(READER_ID, 10L)).isSameAs(response);
        verify(userRoleRepository, never()).findRoleCodesByUser_Id(anyLong());
    }

    @Test
    void searchRestrictsReaderToOwnLoansAndMapsPage() {
        User reader = activeUser(READER_ID);
        Loan loan = Loan.builder().id(10L).user(reader).status(LoanStatus.ACTIVE).build();
        LoanResponse response = org.mockito.Mockito.mock(LoanResponse.class);
        PageRequest pageable = PageRequest.of(0, 20);
        LocalDateTime now = LocalDateTime.now();

        LoanSearchRequest request = new LoanSearchRequest(READER_ID, COPY_ID, BRANCH_ID, STAFF_ID, LoanStatus.ACTIVE,
                now.minusDays(10), now, now.plusDays(1), now.minusDays(1), now);

        when(userRoleRepository.findRoleCodesByUser_Id(READER_ID)).thenReturn(List.of(RoleCode.READER));
        when(loanRepository.findAll(org.mockito.ArgumentMatchers.<Specification<Loan>>any(), eq(pageable))).thenReturn(new PageImpl<>(List.of(loan), pageable, 1));
        when(loanMapper.toResponse(loan)).thenReturn(response);

        PageResponse<LoanResponse> result = service.search(READER_ID, request, pageable);

        assertThat(result.content()).containsExactly(response);
        assertThat(result.totalElements()).isEqualTo(1);
    }

    private void grantStaff() {
        when(userRoleRepository.findRoleCodesByUser_Id(LoanServiceImplTest.STAFF_ID)).thenReturn(List.of(RoleCode.LIBRARIAN));
    }

    private void allowBorrowing(User user) {
        when(userBlockRepository.existsByUser_IdAndStatus(user.getId(), UserBlockStatus.ACTIVE)).thenReturn(false);
        when(fineRepository.countByUser_IdAndStatus(user.getId(), FineStatus.ACTIVE)).thenReturn(0L);
        when(loanRepository.countByUser_IdAndStatusIn(eq(user.getId()), anyCollection())).thenReturn(0L);
    }
}
