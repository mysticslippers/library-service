package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.fine.request.CancelFineRequest;
import me.ifmo.backend.dto.fine.request.CreateFineRequest;
import me.ifmo.backend.dto.fine.response.FineResponse;
import me.ifmo.backend.entities.*;
import me.ifmo.backend.entities.enums.FineStatus;
import me.ifmo.backend.entities.enums.FineTariffStatus;
import me.ifmo.backend.entities.enums.ViolationType;
import me.ifmo.backend.exceptions.domain.BusinessRuleException;
import me.ifmo.backend.exceptions.domain.DuplicateResourceException;
import me.ifmo.backend.exceptions.domain.ResourceNotFoundException;
import me.ifmo.backend.mappers.FineMapper;
import me.ifmo.backend.repositories.*;
import me.ifmo.backend.services.FineService;
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
    private final FineMapper mapper;

    private String normalize(String value, String fieldName) {
        if (value == null || value.strip().isBlank())
            throw new BusinessRuleException("%s must not be blank".formatted(fieldName));

        return value.strip();
    }

    private void validate(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)
            throw new BusinessRuleException("Fine amount must be positive");
    }

    @Override
    @Transactional
    public FineResponse create(CreateFineRequest request) {
        validate(request.amount());

        User user = userRepository.findById(request.userId()).orElseThrow(
                () -> new ResourceNotFoundException("User with id '%s' not found".formatted(request.userId())));

        Loan loan = null;
        if (request.loanId() != null) {
            loan = loanRepository.findById(request.loanId()).orElseThrow(
                    () -> new ResourceNotFoundException("Loan with id '%s' not found".formatted(request.loanId())));

            if (!loan.getUser().getId().equals(user.getId()))
                throw new BusinessRuleException("Loan belongs to another user");

            if (repository.findByLoan_IdAndReasonAndStatus(
                    loan.getId(), request.reason(), FineStatus.ACTIVE).isPresent())
                throw new DuplicateResourceException("Active fine for loan id '%s' and reason '%s' already exists"
                                .formatted(loan.getId(), request.reason()));
        }

        MaterialCopy copy = null;
        if (request.copyId() != null)
            copy = materialCopyRepository.findById(request.copyId()).orElseThrow(
                    () -> new ResourceNotFoundException("Material copy with id '%s' not found".formatted(request.copyId())));
        else if (loan != null)
            copy = loan.getCopy();

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

        Fine fine = mapper.toEntity(user, loan, copy, tariff, request.reason(), request.amount());
        fine.setStatus(FineStatus.ACTIVE);

        Fine saved = repository.save(fine);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public FineResponse getFineById(Long id) {
        Fine fine = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Fine with id '%s' not found".formatted(id)));

        return mapper.toResponse(fine);
    }

    @Override
    @Transactional
    public FineResponse cancel(Long id, CancelFineRequest request) {
        Fine fine = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Fine with id '%s' not found".formatted(id)));

        if (fine.getStatus() != FineStatus.ACTIVE)
            throw new BusinessRuleException("Only active fine can be cancelled");

        normalize(request.reason(), "Cancellation reason");

        fine.setStatus(FineStatus.CANCELLED);
        fine.setCancelledAt(LocalDateTime.now());

        Fine saved = repository.save(fine);
        return mapper.toResponse(saved);
    }
}
