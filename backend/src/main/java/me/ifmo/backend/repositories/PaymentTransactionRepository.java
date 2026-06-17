package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.PaymentTransaction;
import me.ifmo.backend.entities.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

public interface PaymentTransactionRepository extends CrudRepository<PaymentTransaction, Long> {

    Optional<PaymentTransaction> findByExternalPayment(String externalPayment);

    boolean existsByExternalPayment(String externalPayment);

    Page<PaymentTransaction> findByFine_Id(Long fineId, Pageable pageable);

    Page<PaymentTransaction> findByFine_IdAndStatus(Long fineId, PaymentStatus status, Pageable pageable);

    Optional<PaymentTransaction> findByFine_IdAndStatus(Long fineId, PaymentStatus status);

    Page<PaymentTransaction> findByStatus(PaymentStatus status, Pageable pageable);

    Page<PaymentTransaction> findByStatusInAndCreatedAtBefore(Collection<PaymentStatus> statuses, LocalDateTime createdAt, Pageable pageable);
}
