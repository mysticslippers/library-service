package me.ifmo.backend.fine.persistence;

import me.ifmo.backend.fine.domain.PaymentTransaction;
import me.ifmo.backend.fine.domain.enums.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long>, JpaSpecificationExecutor<PaymentTransaction> {

    @Query("select payment.fine.id from PaymentTransaction payment where payment.id = :id")
    Optional<Long> findFineIdById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from PaymentTransaction payment where payment.id = :id")
    Optional<PaymentTransaction> findByIdForUpdate(@Param("id") Long id);

    Optional<PaymentTransaction> findByExternalPayment(String externalPayment);

    boolean existsByExternalPayment(String externalPayment);

    boolean existsByFine_IdAndStatus(Long fineId, PaymentStatus status);

    boolean existsByFine_IdAndStatusIn(Long fineId, Collection<PaymentStatus> statuses);

    Page<PaymentTransaction> findByStatus(PaymentStatus status, Pageable pageable);

}
