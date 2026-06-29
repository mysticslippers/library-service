package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.PaymentTransaction;
import me.ifmo.backend.entities.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

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

    @Query("""
       SELECT transaction FROM PaymentTransaction transaction
           WHERE (:fineId IS NULL OR transaction.fine.id = :fineId)
             AND (:status IS NULL OR transaction.status = :status)
             AND (:createdFrom IS NULL OR transaction.createdAt >= :createdFrom)
             AND (:createdTo IS NULL OR transaction.createdAt <= :createdTo)
    """)
    Page<PaymentTransaction> search(@Param("fineId") Long fineId,
                                    @Param("status") PaymentStatus status,
                                    @Param("createdFrom") LocalDateTime createdFrom,
                                    @Param("createdTo") LocalDateTime createdTo,
                                    Pageable pageable);
}
