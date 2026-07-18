package me.ifmo.backend.circulation.application.impl;

import io.micrometer.observation.annotation.Observed;
import me.ifmo.backend.fine.domain.enums.FineStatus;
import me.ifmo.backend.fine.persistence.FineRepository;

import me.ifmo.backend.circulation.domain.enums.LoanStatus;
import me.ifmo.backend.circulation.domain.enums.ReservationStatus;
import me.ifmo.backend.circulation.domain.Loan;
import me.ifmo.backend.circulation.domain.Reservation;
import me.ifmo.backend.circulation.persistence.LoanRepository;
import me.ifmo.backend.circulation.persistence.ReservationRepository;

import me.ifmo.backend.catalog.domain.enums.CopyStatus;
import me.ifmo.backend.catalog.domain.enums.MaterialStatus;
import me.ifmo.backend.catalog.domain.MaterialCopy;
import me.ifmo.backend.catalog.persistence.MaterialCopyRepository;

import me.ifmo.backend.user.domain.enums.RoleCode;
import me.ifmo.backend.user.domain.enums.UserBlockStatus;
import me.ifmo.backend.user.domain.enums.UserStatus;
import me.ifmo.backend.user.domain.User;
import me.ifmo.backend.user.persistence.UserBlockRepository;
import me.ifmo.backend.user.persistence.UserRepository;
import me.ifmo.backend.user.persistence.UserRoleRepository;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.circulation.web.request.CreateLoanRequest;
import me.ifmo.backend.circulation.web.request.LoanSearchRequest;
import me.ifmo.backend.circulation.web.request.RenewLoanRequest;
import me.ifmo.backend.circulation.web.request.ReturnLoanRequest;
import me.ifmo.backend.circulation.web.response.LoanResponse;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.library.domain.Branch;
import me.ifmo.backend.library.domain.LibraryRule;
import me.ifmo.backend.library.domain.enums.BranchStatus;
import me.ifmo.backend.library.domain.enums.LibraryRuleStatus;
import me.ifmo.backend.shared.error.BusinessRuleException;
import me.ifmo.backend.shared.error.DuplicateResourceException;
import me.ifmo.backend.shared.error.ResourceInUseException;
import me.ifmo.backend.shared.error.ResourceNotFoundException;
import me.ifmo.backend.shared.observability.LoggableOperation;
import me.ifmo.backend.circulation.mapper.LoanMapper;
import me.ifmo.backend.library.persistence.BranchRepository;
import me.ifmo.backend.library.persistence.LibraryRuleRepository;
import me.ifmo.backend.circulation.application.LoanService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LoanServiceImpl implements LoanService {

    private static final Set<LoanStatus> BLOCKING_LOAN_STATUSES =
            Set.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE, LoanStatus.LOST);

    private final LoanRepository repository;
    private final UserRepository userRepository;
    private final MaterialCopyRepository materialCopyRepository;
    private final BranchRepository branchRepository;
    private final ReservationRepository reservationRepository;
    private final LibraryRuleRepository libraryRuleRepository;
    private final UserBlockRepository userBlockRepository;
    private final FineRepository fineRepository;
    private final UserRoleRepository userRoleRepository;
    private final LoanMapper loanMapper;

    private boolean isStaff(Long actorUserId) {
        return userRoleRepository.findRoleCodesByUser_Id(actorUserId).stream()
                .anyMatch(role -> role == RoleCode.LIBRARIAN || role == RoleCode.ADMIN);
    }

    private void validateStaff(Long actorUserId) {
        if (!isStaff(actorUserId))
            throw new AccessDeniedException("Access is denied");
    }

    private void validateOwnerOrStaff(Loan loan, Long actorUserId) {
        if (!loan.getUser().getId().equals(actorUserId) && !isStaff(actorUserId))
            throw new AccessDeniedException("Access is denied");
    }

    private LibraryRule getActualRule(Long branchId) {
        return libraryRuleRepository.findActualByBranchIdAndStatus(branchId, LibraryRuleStatus.ACTIVE, LocalDateTime.now()).orElseThrow(
                        () -> new ResourceNotFoundException("Actual library rule for branch with id '%s' not found".formatted(branchId)));
    }

    private User findUser(Long id, String fieldName) {
        User user = userRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("%s with id '%s' not found".formatted(fieldName, id)));

        if (user.getStatus() != UserStatus.ACTIVE)
            throw new BusinessRuleException("%s must be active".formatted(fieldName));

        return user;
    }

    private void validateUserCanBorrow(User user) {
        if (userBlockRepository.existsByUser_IdAndStatus(user.getId(), UserBlockStatus.ACTIVE))
            throw new BusinessRuleException("User has active block");

        if (fineRepository.countByUser_IdAndStatus(user.getId(), FineStatus.ACTIVE) > 0)
            throw new BusinessRuleException("User has unpaid fines");

        if (repository.countByUser_IdAndStatusIn(user.getId(), Set.of(LoanStatus.OVERDUE, LoanStatus.LOST)) > 0)
            throw new BusinessRuleException("User has overdue or lost loans");
    }

    private CopyStatus resolveReturnCopyStatus(ReturnLoanRequest request) {
        if (request.resultingCopyStatus() == null)
            return CopyStatus.AVAILABLE;

        CopyStatus status = request.resultingCopyStatus();

        if (status == CopyStatus.RESERVED || status == CopyStatus.LOANED)
            throw new BusinessRuleException("Returned copy cannot become '%s'".formatted(status));

        return status;
    }

    @Override
    @Transactional
    @LoggableOperation("loan.create")
    @Observed(
            name = "library.operation",
            contextualName = "loan.create",
            lowCardinalityKeyValues = {"domain", "circulation", "operation", "loan.create"}
    )
    public LoanResponse create(Long actorUserId, CreateLoanRequest request) {
        validateStaff(actorUserId);

        if (!request.issuedByUserId().equals(actorUserId))
            throw new AccessDeniedException("Access is denied");

        User user = findUser(request.userId(), "User");
        User issuedByUser = findUser(request.issuedByUserId(), "Issued by user");
        validateUserCanBorrow(user);

        Branch branch = branchRepository.findById(request.branchId()).orElseThrow(
                () -> new ResourceNotFoundException("Branch with id '%s' not found".formatted(request.branchId())));

        if (branch.getStatus() != BranchStatus.ACTIVE)
            throw new BusinessRuleException("Loan can be created only in active branch");

        MaterialCopy copy = materialCopyRepository.findById(request.copyId()).orElseThrow(
                () -> new ResourceNotFoundException("Material copy with id '%s' not found".formatted(request.copyId())));

        if (!copy.getBranch().getId().equals(branch.getId()))
            throw new BusinessRuleException("Material copy does not belong to requested branch");

        if (copy.getMaterial().getStatus() != MaterialStatus.ACTIVE)
            throw new BusinessRuleException("Loan can be created only for active material");

        if (repository.findByCopy_IdAndStatusIn(copy.getId(), BLOCKING_LOAN_STATUSES).isPresent())
            throw new ResourceInUseException("Material copy already has active or unresolved loan");

        LibraryRule rule = getActualRule(branch.getId());

        Long activeLoanCount = repository.countByUser_IdAndStatusIn(user.getId(), BLOCKING_LOAN_STATUSES);
        if (activeLoanCount >= rule.getMaxActiveLoans())
            throw new BusinessRuleException("User has reached active loan limit");

        Reservation reservation = null;

        if (request.reservationId() != null) {
            reservation = reservationRepository.findById(request.reservationId()).orElseThrow(
                    () -> new ResourceNotFoundException("Reservation with id '%s' not found".formatted(request.reservationId())));

            if (reservation.getStatus() != ReservationStatus.READY_FOR_PICKUP)
                throw new BusinessRuleException("Only ready for pickup reservation can be converted to loan");

            if (reservation.getExpiresAt().isBefore(LocalDateTime.now()))
                throw new BusinessRuleException("Reservation has expired");

            if (!reservation.getUser().getId().equals(user.getId()))
                throw new BusinessRuleException("Reservation belongs to another user");

            if (!reservation.getCopy().getId().equals(copy.getId()))
                throw new BusinessRuleException("Reservation belongs to another material copy");

            if (!reservation.getBranch().getId().equals(branch.getId()))
                throw new BusinessRuleException("Reservation belongs to another branch");

            if (repository.findByReservation_Id(reservation.getId()).isPresent())
                throw new DuplicateResourceException("Loan for reservation with id '%s' already exists".formatted(reservation.getId()));

            if (copy.getStatus() != CopyStatus.RESERVED)
                throw new BusinessRuleException("Reserved material copy has invalid status");

            reservation.setStatus(ReservationStatus.USED);
        } else if (copy.getStatus() != CopyStatus.AVAILABLE) {
            reservationRepository.findByCopy_IdAndStatusIn(copy.getId(),
                    Set.of(ReservationStatus.ACTIVE, ReservationStatus.READY_FOR_PICKUP)).ifPresent(activeReservation -> {
                        throw new ResourceInUseException("Material copy is reserved by another user");
                    });
            throw new ResourceInUseException("Material copy is not available for loan");
        }

        LocalDateTime now = LocalDateTime.now();

        LocalDateTime dueAt = request.dueAt() != null ? request.dueAt() : now.plusDays(rule.getDefaultLoanDays());

        if (!dueAt.isAfter(now))
            throw new BusinessRuleException("Loan dueAt must be in the future");

        copy.setStatus(CopyStatus.LOANED);

        Loan loan = loanMapper.toEntity(user, copy, reservation, branch, issuedByUser, dueAt);
        loan.setStatus(LoanStatus.ACTIVE);
        loan.setRenewalCount(0);

        Loan saved = repository.save(loan);
        return loanMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public LoanResponse getLoanById(Long actorUserId, Long id) {
        Loan loan = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Loan with id '%s' not found".formatted(id)));
        validateOwnerOrStaff(loan, actorUserId);

        return loanMapper.toResponse(loan);
    }

    @Override
    @Transactional
    @LoggableOperation("loan.return")
    @Observed(
            name = "library.operation",
            contextualName = "loan.return",
            lowCardinalityKeyValues = {"domain", "circulation", "operation", "loan.return"}
    )
    public LoanResponse returnLoan(Long actorUserId, Long id, ReturnLoanRequest request) {
        validateStaff(actorUserId);

        Loan loan = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Loan with id '%s' not found".formatted(id)));

        if (loan.getStatus() != LoanStatus.ACTIVE && loan.getStatus() != LoanStatus.OVERDUE && loan.getStatus() != LoanStatus.LOST)
            throw new BusinessRuleException("Only active, overdue or lost loan can be returned");

        CopyStatus resultingCopyStatus = resolveReturnCopyStatus(request);

        loan.setStatus(LoanStatus.RETURNED);
        loan.setReturnedAt(LocalDateTime.now());
        loan.getCopy().setStatus(resultingCopyStatus);

        Loan saved = repository.save(loan);
        return loanMapper.toResponse(saved);
    }

    @Override
    @Transactional
    @LoggableOperation("loan.renew")
    @Observed(
            name = "library.operation",
            contextualName = "loan.renew",
            lowCardinalityKeyValues = {"domain", "circulation", "operation", "loan.renew"}
    )
    public LoanResponse renew(Long actorUserId, Long id, RenewLoanRequest request) {
        Loan loan = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Loan with id '%s' not found".formatted(id)));
        validateOwnerOrStaff(loan, actorUserId);

        if (loan.getStatus() != LoanStatus.ACTIVE)
            throw new BusinessRuleException("Only active loan can be renewed");

        if (!loan.getDueAt().isAfter(LocalDateTime.now()))
            throw new BusinessRuleException("Overdue loan cannot be renewed");

        validateUserCanBorrow(loan.getUser());

        LibraryRule rule = getActualRule(loan.getBranch().getId());

        if (Boolean.FALSE.equals(rule.getRenewalAllowed()))
            throw new BusinessRuleException("Loan renewal is not allowed for this branch");

        if (loan.getRenewalCount() >= rule.getMaxRenewalCount())
            throw new BusinessRuleException("Loan renewal limit has been reached");

        int renewalDays = request.renewalDays() != null ? request.renewalDays() : rule.getRenewalPeriodDays();
        if (renewalDays <= 0)
            throw new BusinessRuleException("Renewal days must be positive");

        loan.setDueAt(loan.getDueAt().plusDays(renewalDays));
        loan.setRenewalCount(loan.getRenewalCount() + 1);

        Loan saved = repository.save(loan);
        return loanMapper.toResponse(saved);
    }

    @Override
    @Transactional
    @LoggableOperation("loan.mark-overdue")
    @Observed(
            name = "library.operation",
            contextualName = "loan.mark-overdue",
            lowCardinalityKeyValues = {"domain", "circulation", "operation", "loan.mark-overdue"}
    )
    public LoanResponse markOverdue(Long actorUserId, Long id) {
        validateStaff(actorUserId);

        Loan loan = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Loan with id '%s' not found".formatted(id)));

        if (loan.getStatus() != LoanStatus.ACTIVE)
            throw new BusinessRuleException("Only active loan can be marked as overdue");

        if (loan.getDueAt().isAfter(LocalDateTime.now()))
            throw new BusinessRuleException("Loan dueAt has not passed yet");

        loan.setStatus(LoanStatus.OVERDUE);

        Loan saved = repository.save(loan);
        return loanMapper.toResponse(saved);
    }

    @Override
    @Transactional
    @LoggableOperation("loan.mark-lost")
    @Observed(
            name = "library.operation",
            contextualName = "loan.mark-lost",
            lowCardinalityKeyValues = {"domain", "circulation", "operation", "loan.mark-lost"}
    )
    public LoanResponse markLost(Long actorUserId, Long id) {
        validateStaff(actorUserId);

        Loan loan = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Loan with id '%s' not found".formatted(id)));

        if (loan.getStatus() != LoanStatus.ACTIVE && loan.getStatus() != LoanStatus.OVERDUE)
            throw new BusinessRuleException("Only active or overdue loan can be marked as lost");

        loan.setStatus(LoanStatus.LOST);
        loan.getCopy().setStatus(CopyStatus.LOST);

        Loan saved = repository.save(loan);
        return loanMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<LoanResponse> search(Long actorUserId, LoanSearchRequest request, Pageable pageable) {
        boolean staff = isStaff(actorUserId);
        Long userId = request.userId();

        if (!staff) {
            if (userId != null && !userId.equals(actorUserId))
                throw new AccessDeniedException("Access is denied");
            userId = actorUserId;
        }

        Long filterUserId = userId;
        Specification<Loan> specification = (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();

        if (filterUserId != null)
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("user").get("id"), filterUserId));
        if (request.copyId() != null)
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("copy").get("id"), request.copyId()));
        if (request.branchId() != null)
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("branch").get("id"), request.branchId()));
        if (request.issuedByUserId() != null)
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("issuedByUser").get("id"), request.issuedByUserId()));
        if (request.status() != null)
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("status"), request.status()));
        if (request.loanedFrom() != null)
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.greaterThanOrEqualTo(root.get("loanedAt"), request.loanedFrom()));
        if (request.loanedTo() != null)
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.lessThanOrEqualTo(root.get("loanedAt"), request.loanedTo()));
        if (request.dueBefore() != null)
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.lessThanOrEqualTo(root.get("dueAt"), request.dueBefore()));
        if (request.returnedFrom() != null)
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.greaterThanOrEqualTo(root.get("returnedAt"), request.returnedFrom()));
        if (request.returnedTo() != null)
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.lessThanOrEqualTo(root.get("returnedAt"), request.returnedTo()));

        Page<Loan> loans = repository.findAll(specification, pageable);

        Page<LoanResponse> responses = loans.map(loanMapper::toResponse);

        return PageResponse.from(responses);
    }
}
