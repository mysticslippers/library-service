package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.entities.enums.PaymentStatus;
import me.ifmo.backend.mappers.PaymentTransactionMapper;
import me.ifmo.backend.repositories.FineRepository;
import me.ifmo.backend.repositories.PaymentTransactionRepository;
import me.ifmo.backend.services.PaymentTransactionService;
import org.springframework.stereotype.Service;

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
}
