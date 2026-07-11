package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.Fine;
import me.ifmo.backend.entities.enums.FineStatus;
import me.ifmo.backend.entities.enums.ViolationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface FineRepository extends JpaRepository<Fine, Long> {

    Page<Fine> findByUser_Id(Long userId, Pageable pageable);

    Page<Fine> findByUser_IdAndStatus(Long userId, FineStatus status, Pageable pageable);

    Optional<Fine> findByLoan_IdAndReasonAndStatus(Long loanId, ViolationType reason, FineStatus status);

    Long countByUser_IdAndStatus(Long userId, FineStatus status);

    @Query("""
       SELECT fine FROM Fine fine
           WHERE (:userId IS NULL OR fine.user.id = :userId)
             AND (:loanId IS NULL OR fine.loan.id = :loanId)
             AND (:copyId IS NULL OR fine.copy.id = :copyId)
             AND (:reason IS NULL OR fine.reason = :reason)
             AND (:status IS NULL OR fine.status = :status)
             AND (:createdFrom IS NULL OR fine.createdAt >= :createdFrom)
             AND (:createdTo IS NULL OR fine.createdAt <= :createdTo)
    """)
    Page<Fine> search(@Param("userId") Long userId,
                      @Param("loanId") Long loanId,
                      @Param("copyId") Long copyId,
                      @Param("reason") ViolationType reason,
                      @Param("status") FineStatus status,
                      @Param("createdFrom") LocalDateTime createdFrom,
                      @Param("createdTo") LocalDateTime createdTo,
                      Pageable pageable);
}
