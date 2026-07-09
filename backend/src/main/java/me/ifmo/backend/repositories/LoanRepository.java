package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.Loan;
import me.ifmo.backend.entities.enums.LoanStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    Page<Loan> findByUser_Id(Long userId, Pageable pageable);

    Page<Loan> findByUser_IdAndStatus(Long userId, LoanStatus status, Pageable pageable);

    Page<Loan> findByUser_IdAndStatusIn(Long userId, Collection<LoanStatus> statuses, Pageable pageable);

    Page<Loan> findByBranch_IdAndStatus(Long branchId, LoanStatus status, Pageable pageable);

    boolean existsByBranch_IdAndStatusIn(Long branchId, Collection<LoanStatus> statuses);

    boolean existsByBranch_Library_IdAndStatusIn(Long libraryId, Collection<LoanStatus> statuses);

    boolean existsByCopy_Material_IdAndStatusIn(Long materialId, Collection<LoanStatus> statuses);

    Optional<Loan> findByCopy_IdAndStatusIn(Long copyId, Collection<LoanStatus> statuses);

    Optional<Loan> findByReservation_Id(Long reservationId);

    List<Loan> findByStatusAndDueAtBefore(LoanStatus status, LocalDateTime dueAt);

    List<Loan> findByStatusInAndDueAtBefore(Collection<LoanStatus> statuses, LocalDateTime dueAt);

    Long countByUser_IdAndStatusIn(Long userId, Collection<LoanStatus> statuses);

    @Query("""
       SELECT loan FROM Loan loan
           WHERE (:userId IS NULL OR loan.user.id = :userId)
             AND (:copyId IS NULL OR loan.copy.id = :copyId)
             AND (:branchId IS NULL OR loan.branch.id = :branchId)
             AND (:issuedByUserId IS NULL OR loan.issuedByUser.id = :issuedByUserId)
             AND (:status IS NULL OR loan.status = :status)
             AND (:loanedFrom IS NULL OR loan.loanedAt >= :loanedFrom)
             AND (:loanedTo IS NULL OR loan.loanedAt <= :loanedTo)
             AND (:dueBefore IS NULL OR loan.dueAt <= :dueBefore)
             AND (:returnedFrom IS NULL OR loan.returnedAt >= :returnedFrom)
             AND (:returnedTo IS NULL OR loan.returnedAt <= :returnedTo)
    """)
    Page<Loan> search(@Param("userId") Long userId,
                      @Param("copyId") Long copyId,
                      @Param("branchId") Long branchId,
                      @Param("issuedByUserId") Long issuedByUserId,
                      @Param("status") LoanStatus status,
                      @Param("loanedFrom") LocalDateTime loanedFrom,
                      @Param("loanedTo") LocalDateTime loanedTo,
                      @Param("dueBefore") LocalDateTime dueBefore,
                      @Param("returnedFrom") LocalDateTime returnedFrom,
                      @Param("returnedTo") LocalDateTime returnedTo,
                      Pageable pageable);
}
