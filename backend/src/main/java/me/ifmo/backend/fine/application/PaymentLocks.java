package me.ifmo.backend.fine.application;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.fine.domain.Fine;
import me.ifmo.backend.fine.domain.PaymentTransaction;
import me.ifmo.backend.fine.persistence.FineRepository;
import me.ifmo.backend.fine.persistence.PaymentTransactionRepository;
import me.ifmo.backend.shared.exceptions.ResourceNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class PaymentLocks {

    private final FineRepository fines;
    private final PaymentTransactionRepository payments;
    private final EntityManager entityManager;

    public Fine fine(Long id) {
        Fine fine = fines.findByIdForUpdate(id).orElseThrow(
                () -> new ResourceNotFoundException("Fine with id '%s' not found".formatted(id)));
        entityManager.refresh(fine);

        return fine;
    }

    public PaymentTransaction payment(Long id) {
        Long fineId = payments.findFineIdById(id).orElseThrow(
                () -> new ResourceNotFoundException("Payment transaction with id '%s' not found".formatted(id)));
        fine(fineId);

        PaymentTransaction payment = payments.findByIdForUpdate(id).orElseThrow(
                () -> new ResourceNotFoundException("Payment transaction with id '%s' not found".formatted(id)));
        entityManager.refresh(payment);

        return payment;
    }
}
