package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.circulation.request.CreateLoanRequest;
import me.ifmo.backend.dto.circulation.request.LoanSearchRequest;
import me.ifmo.backend.dto.circulation.request.RenewLoanRequest;
import me.ifmo.backend.dto.circulation.request.ReturnLoanRequest;
import me.ifmo.backend.dto.circulation.response.LoanResponse;
import me.ifmo.backend.dto.common.response.PageResponse;
import me.ifmo.backend.entities.*;
import me.ifmo.backend.entities.enums.*;
import me.ifmo.backend.exceptions.domain.BusinessRuleException;
import me.ifmo.backend.exceptions.domain.DuplicateResourceException;
import me.ifmo.backend.exceptions.domain.ResourceInUseException;
import me.ifmo.backend.exceptions.domain.ResourceNotFoundException;
import me.ifmo.backend.mappers.LoanMapper;
import me.ifmo.backend.repositories.*;
import me.ifmo.backend.services.LoanService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final LoanMapper mapper;

    private LibraryRule getActualRule(Long branchId) {
        return libraryRuleRepository.findActualByBranchIdAndStatus(branchId, LibraryRuleStatus.ACTIVE, LocalDateTime.now())
                .orElseThrow(
                        () -> new ResourceNotFoundException("Actual library rule for branch with id '%s' not found".formatted(branchId)));
    }

    private User findUser(Long id, String fieldName) {
        User user = userRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("%s with id '%s' not found".formatted(fieldName, id)));

        if (user.getStatus() != UserStatus.ACTIVE)
            throw new BusinessRuleException("%s must be active".formatted(fieldName));

        return user;
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
    public LoanResponse create(CreateLoanRequest request) {
        User user = findUser(request.userId(), "User");
        User issuedByUser = findUser(request.issuedByUserId(), "Issued by user");

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
        } else if (copy.getStatus() != CopyStatus.AVAILABLE)
            throw new ResourceInUseException("Material copy is not available for loan");

        LocalDateTime now = LocalDateTime.now();

        LocalDateTime dueAt = request.dueAt() != null ? request.dueAt() : now.plusDays(rule.getDefaultLoanDays());

        if (!dueAt.isAfter(now))
            throw new BusinessRuleException("Loan dueAt must be in the future");

        copy.setStatus(CopyStatus.LOANED);

        Loan loan = mapper.toEntity(user, copy, reservation, branch, issuedByUser, dueAt);
        loan.setStatus(LoanStatus.ACTIVE);
        loan.setRenewalCount(0);

        Loan saved = repository.save(loan);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public LoanResponse getLoanById(Long id) {
        Loan loan = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Loan with id '%s' not found".formatted(id)));

        return mapper.toResponse(loan);
    }

    @Override
    @Transactional
    public LoanResponse returnLoan(Long id, ReturnLoanRequest request) {
        Loan loan = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Loan with id '%s' not found".formatted(id)));

        if (loan.getStatus() != LoanStatus.ACTIVE && loan.getStatus() != LoanStatus.OVERDUE && loan.getStatus() != LoanStatus.LOST)
            throw new BusinessRuleException("Only active, overdue or lost loan can be returned");

        CopyStatus resultingCopyStatus = resolveReturnCopyStatus(request);

        loan.setStatus(LoanStatus.RETURNED);
        loan.setReturnedAt(LocalDateTime.now());
        loan.getCopy().setStatus(resultingCopyStatus);

        Loan saved = repository.save(loan);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public LoanResponse renew(Long id, RenewLoanRequest request) {
        Loan loan = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Loan with id '%s' not found".formatted(id)));

        if (loan.getStatus() != LoanStatus.ACTIVE)
            throw new BusinessRuleException("Only active loan can be renewed");

        LibraryRule rule = getActualRule(loan.getBranch().getId());

        if (Boolean.FALSE.equals(rule.getRenewalAllowed()))
            throw new BusinessRuleException("Loan renewal is not allowed for this branch");

        if (loan.getRenewalCount() >= rule.getMaxRenewalCount())
            throw new BusinessRuleException("Loan renewal limit has been reached");

        int renewalDays = request.renewalDays() != null ? request.renewalDays() : rule.getRenewalPeriodDays();

        loan.setDueAt(loan.getDueAt().plusDays(renewalDays));
        loan.setRenewalCount(loan.getRenewalCount() + 1);

        Loan saved = repository.save(loan);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public LoanResponse markOverdue(Long id) {
        Loan loan = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Loan with id '%s' not found".formatted(id)));

        if (loan.getStatus() != LoanStatus.ACTIVE)
            throw new BusinessRuleException("Only active loan can be marked as overdue");

        if (loan.getDueAt().isAfter(LocalDateTime.now()))
            throw new BusinessRuleException("Loan dueAt has not passed yet");

        loan.setStatus(LoanStatus.OVERDUE);

        Loan saved = repository.save(loan);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public LoanResponse markLost(Long id) {
        Loan loan = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Loan with id '%s' not found".formatted(id)));

        if (loan.getStatus() != LoanStatus.ACTIVE && loan.getStatus() != LoanStatus.OVERDUE)
            throw new BusinessRuleException("Only active or overdue loan can be marked as lost");

        loan.setStatus(LoanStatus.LOST);
        loan.getCopy().setStatus(CopyStatus.LOST);

        Loan saved = repository.save(loan);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<LoanResponse> search(LoanSearchRequest request, Pageable pageable) {
        Page<Loan> loans = repository.search(request.userId(), request.copyId(), request.branchId(), request.issuedByUserId(),
                request.status(), request.loanedFrom(), request.loanedTo(), request.dueBefore(), request.returnedFrom(),
                request.returnedTo(), pageable);

        Page<LoanResponse> responses = loans.map(mapper::toResponse);

        return PageResponse.from(responses);
    }
}
