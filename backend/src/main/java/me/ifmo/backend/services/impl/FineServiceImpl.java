package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.common.response.PageResponse;
import me.ifmo.backend.dto.fine.request.CancelFineRequest;
import me.ifmo.backend.dto.fine.request.CreateFineRequest;
import me.ifmo.backend.dto.fine.request.FineSearchRequest;
import me.ifmo.backend.dto.fine.response.FineResponse;
import me.ifmo.backend.entities.*;
import me.ifmo.backend.entities.enums.*;
import me.ifmo.backend.exceptions.domain.BusinessRuleException;
import me.ifmo.backend.exceptions.domain.DuplicateResourceException;
import me.ifmo.backend.exceptions.domain.ResourceNotFoundException;
import me.ifmo.backend.mappers.FineMapper;
import me.ifmo.backend.repositories.*;
import me.ifmo.backend.services.FineService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class FineServiceImpl implements FineService {

    private final FineRepository repository;
    private final UserRepository userRepository;
    private final LoanRepository loanRepository;
    private final MaterialCopyRepository materialCopyRepository;
    private final FineTariffRepository fineTariffRepository;
    private final UserRoleRepository userRoleRepository;
    private final FineMapper fineMapper;

    private String normalize(String value) {
        if (value == null || value.strip().isBlank())
            throw new BusinessRuleException("%s must not be blank".formatted("Cancellation reason"));

        return value.strip();
    }

    private boolean isStaff(Long actorUserId) {
        return userRoleRepository.findRoleCodesByUser_Id(actorUserId).stream()
                .anyMatch(role -> role == RoleCode.LIBRARIAN || role == RoleCode.ADMIN);
    }

    private void validateStaff(Long actorUserId) {
        if (!isStaff(actorUserId))
            throw new AccessDeniedException("Access is denied");
    }

    private void validateOwnerOrStaff(Fine fine, Long actorUserId) {
        if (!fine.getUser().getId().equals(actorUserId) && !isStaff(actorUserId))
            throw new AccessDeniedException("Access is denied");
    }

    private void validate(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)
            throw new BusinessRuleException("Fine amount must be positive");
    }

    @Override
    @Transactional
    public FineResponse create(Long actorUserId, CreateFineRequest request) {
        validateStaff(actorUserId);
        validate(request.amount());

        User user = userRepository.findById(request.userId()).orElseThrow(
                () -> new ResourceNotFoundException("User with id '%s' not found".formatted(request.userId())));

        Loan loan = null;
        if (request.loanId() != null) {
            loan = loanRepository.findById(request.loanId()).orElseThrow(
                    () -> new ResourceNotFoundException("Loan with id '%s' not found".formatted(request.loanId())));

            if (!loan.getUser().getId().equals(user.getId()))
                throw new BusinessRuleException("Loan belongs to another user");

            if (repository.findByLoan_IdAndReasonAndStatus(loan.getId(), request.reason(), FineStatus.ACTIVE).isPresent())
                throw new DuplicateResourceException("Active fine for loan id '%s' and reason '%s' already exists"
                                .formatted(loan.getId(), request.reason()));
        }

        MaterialCopy copy = null;
        if (request.copyId() != null)
            copy = materialCopyRepository.findById(request.copyId()).orElseThrow(
                    () -> new ResourceNotFoundException("Material copy with id '%s' not found".formatted(request.copyId())));
        else if (loan != null)
            copy = loan.getCopy();

        if (loan != null && copy != null && !loan.getCopy().getId().equals(copy.getId()))
            throw new BusinessRuleException("Fine material copy does not belong to loan");

        if (request.reason() == ViolationType.OVERDUE && loan == null)
            throw new BusinessRuleException("Overdue fine must be linked to loan");

        if ((request.reason() == ViolationType.DAMAGE || request.reason() == ViolationType.LOSS) && copy == null)
            throw new BusinessRuleException("Damage or loss fine must be linked to material copy");

        FineTariff tariff = null;
        if (request.tariffId() != null) {
            tariff = fineTariffRepository.findById(request.tariffId()).orElseThrow(
                    () -> new ResourceNotFoundException("Fine tariff with id '%s' not found".formatted(request.tariffId())));

            if (tariff.getStatus() == FineTariffStatus.ARCHIVED)
                throw new BusinessRuleException("Archived fine tariff cannot be used");

            if (tariff.getViolationType() != request.reason())
                throw new BusinessRuleException("Fine tariff violation type does not match fine reason");
        }

        Fine fine = fineMapper.toEntity(user, loan, copy, tariff, request.reason(), request.amount());
        fine.setStatus(FineStatus.ACTIVE);

        Fine saved = repository.save(fine);
        return fineMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public FineResponse getFineById(Long actorUserId, Long id) {
        Fine fine = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Fine with id '%s' not found".formatted(id)));
        validateOwnerOrStaff(fine, actorUserId);

        return fineMapper.toResponse(fine);
    }

    @Override
    @Transactional
    public FineResponse cancel(Long actorUserId, Long id, CancelFineRequest request) {
        validateStaff(actorUserId);

        Fine fine = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Fine with id '%s' not found".formatted(id)));

        if (fine.getStatus() != FineStatus.ACTIVE)
            throw new BusinessRuleException("Only active fine can be cancelled");

        String reason = normalize(request.reason());

        fine.setStatus(FineStatus.CANCELLED);
        fine.setCancelledAt(LocalDateTime.now());
        fine.setCancellationReason(reason);

        Fine saved = repository.save(fine);
        return fineMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public FineResponse markPaid(Long actorUserId, Long id) {
        validateStaff(actorUserId);

        Fine fine = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Fine with id '%s' not found".formatted(id)));

        if (fine.getStatus() != FineStatus.ACTIVE)
            throw new BusinessRuleException("Only active fine can be marked as paid");

        fine.setStatus(FineStatus.PAID);
        fine.setPaidAt(LocalDateTime.now());

        Fine saved = repository.save(fine);
        return fineMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FineResponse> search(Long actorUserId, FineSearchRequest request, Pageable pageable) {
        boolean staff = isStaff(actorUserId);
        Long userId = request.userId();

        if (!staff) {
            if (userId != null && !userId.equals(actorUserId))
                throw new AccessDeniedException("Access is denied");
            userId = actorUserId;
        }

        Page<Fine> fines = repository.search(userId, request.loanId(), request.copyId(), request.reason(),
                request.status(), request.createdFrom(), request.createdTo(), pageable);

        Page<FineResponse> responses = fines.map(fineMapper::toResponse);

        return PageResponse.from(responses);
    }
}
