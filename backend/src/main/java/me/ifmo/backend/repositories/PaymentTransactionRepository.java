package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.PaymentTransaction;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface PaymentTransactionRepository extends CrudRepository<PaymentTransaction, Long> {

    Optional<PaymentTransaction> findByExternalPayment(String externalPayment);

    boolean existsByExternalPayment(String externalPayment);
}
