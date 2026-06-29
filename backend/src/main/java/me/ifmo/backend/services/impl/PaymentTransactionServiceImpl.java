package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.entities.enums.PaymentStatus;
import me.ifmo.backend.exceptions.domain.BusinessRuleException;
import me.ifmo.backend.mappers.PaymentTransactionMapper;
import me.ifmo.backend.repositories.FineRepository;
import me.ifmo.backend.repositories.PaymentTransactionRepository;
import me.ifmo.backend.services.PaymentTransactionService;
import org.springframework.stereotype.Service;

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
}
