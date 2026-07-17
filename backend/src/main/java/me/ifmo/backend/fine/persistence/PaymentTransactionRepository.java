package me.ifmo.backend.fine.persistence;

import me.ifmo.backend.fine.domain.PaymentTransaction;
import me.ifmo.backend.fine.domain.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;

import java.util.Collection;
import java.util.Optional;

public interface PaymentTransactionRepository extends CrudRepository<PaymentTransaction, Long>, JpaSpecificationExecutor<PaymentTransaction> {

    Optional<PaymentTransaction> findByExternalPayment(String externalPayment);

    boolean existsByExternalPayment(String externalPayment);

    boolean existsByFine_IdAndStatus(Long fineId, PaymentStatus status);

    boolean existsByFine_IdAndStatusIn(Long fineId, Collection<PaymentStatus> statuses);

    Page<PaymentTransaction> findByStatus(PaymentStatus status, Pageable pageable);

}
