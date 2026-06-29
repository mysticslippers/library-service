package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.fine.request.CreatePaymentTransactionRequest;
import me.ifmo.backend.dto.fine.response.PaymentTransactionResponse;
import me.ifmo.backend.entities.Fine;
import me.ifmo.backend.entities.PaymentTransaction;
import me.ifmo.backend.entities.enums.FineStatus;
import me.ifmo.backend.entities.enums.PaymentStatus;
import me.ifmo.backend.exceptions.domain.BusinessRuleException;
import me.ifmo.backend.exceptions.domain.DuplicateResourceException;
import me.ifmo.backend.exceptions.domain.ResourceNotFoundException;
import me.ifmo.backend.mappers.PaymentTransactionMapper;
import me.ifmo.backend.repositories.FineRepository;
import me.ifmo.backend.repositories.PaymentTransactionRepository;
import me.ifmo.backend.services.PaymentTransactionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PaymentTransactionServiceImpl implements PaymentTransactionService {

    private static final Set<PaymentStatus> FINAL_STATUSES =
            Set.of(PaymentStatus.SUCCESS, PaymentStatus.DECLINED, PaymentStatus.CANCELLED,
                    PaymentStatus.FAILED, PaymentStatus.TIMEOUT);

    private final PaymentTransactionRepository repository;
    private final FineRepository fineRepository;
    private final PaymentTransactionMapper mapper;

    private String normalize(String value) {
        if (value == null || value.strip().isBlank()) {
            return null;
        }

        return value.strip();
    }

    private void validate(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)
            throw new BusinessRuleException("Payment amount must be positive");
    }

    private boolean isTransitionAllowed(PaymentStatus current, PaymentStatus target) {
        if (current == target)
            return true;

        if (FINAL_STATUSES.contains(current))
            return false;

        return switch (current) {
            case CREATED -> target == PaymentStatus.PENDING
                    || target == PaymentStatus.SUCCESS
                    || target == PaymentStatus.CANCELLED
                    || target == PaymentStatus.FAILED;
            case PENDING -> target == PaymentStatus.SUCCESS
                    || target == PaymentStatus.DECLINED
                    || target == PaymentStatus.CANCELLED
                    || target == PaymentStatus.FAILED
                    || target == PaymentStatus.TIMEOUT;
            case SUCCESS, DECLINED, CANCELLED, FAILED, TIMEOUT -> false;
        };
    }

    @Override
    @Transactional
    public PaymentTransactionResponse create(CreatePaymentTransactionRequest request) {
        Fine fine = fineRepository.findById(request.fineId()).orElseThrow(
                () -> new ResourceNotFoundException("Fine with id '%s' not found".formatted(request.fineId())));

        if (fine.getStatus() != FineStatus.ACTIVE)
            throw new BusinessRuleException("Payment can be created only for active fine");

        validate(request.amount());

        if (request.amount().compareTo(fine.getAmount()) != 0)
            throw new BusinessRuleException("Payment amount must be equal to fine amount");

        String externalPayment = normalize(request.externalPayment());

        if (externalPayment != null && repository.existsByExternalPayment(externalPayment))
            throw new DuplicateResourceException("Payment transaction with external payment id '%s' already exists".formatted(externalPayment));

        if (repository.findByFine_IdAndStatus(fine.getId(), PaymentStatus.PENDING).isPresent())
            throw new BusinessRuleException("Fine already has pending payment transaction");

        PaymentTransaction transaction = mapper.toEntity(fine, externalPayment, request.amount());
        transaction.setStatus(PaymentStatus.CREATED);

        PaymentTransaction saved = repository.save(transaction);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentTransactionResponse getPaymentTransactionById(Long id) {
        PaymentTransaction transaction = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Payment transaction with id '%s' not found".formatted(id)));

        return mapper.toResponse(transaction);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentTransactionResponse getByExternalPayment(String externalPayment) {
        String normalizedExternalPayment = normalize(externalPayment);

        if (normalizedExternalPayment == null)
            throw new BusinessRuleException("External payment id must not be blank");

        PaymentTransaction transaction = repository.findByExternalPayment(normalizedExternalPayment).orElseThrow(
                () -> new ResourceNotFoundException(
                        "Payment transaction with external payment id '%s' not found".formatted(normalizedExternalPayment)));

        return mapper.toResponse(transaction);
    }
}
