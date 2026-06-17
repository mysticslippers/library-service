package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.PaymentTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface PaymentTransactionRepository extends CrudRepository<PaymentTransaction, Long> {

    Optional<PaymentTransaction> findByExternalPayment(String externalPayment);

    boolean existsByExternalPayment(String externalPayment);

    Page<PaymentTransaction> findByFine_Id(Long fineId, Pageable pageable);
}
