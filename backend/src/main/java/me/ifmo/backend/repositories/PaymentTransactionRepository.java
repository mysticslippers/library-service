package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.PaymentTransaction;
import me.ifmo.backend.entities.enums.PaymentStatus;
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
