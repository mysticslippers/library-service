package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.Loan;
import me.ifmo.backend.entities.enums.LoanStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface LoanRepository extends JpaRepository<Loan, Long>, JpaSpecificationExecutor<Loan> {

    Page<Loan> findByUser_Id(Long userId, Pageable pageable);

    Page<Loan> findByUser_IdAndStatusIn(Long userId, Collection<LoanStatus> statuses, Pageable pageable);

    boolean existsByBranch_IdAndStatusIn(Long branchId, Collection<LoanStatus> statuses);

    boolean existsByBranch_Library_IdAndStatusIn(Long libraryId, Collection<LoanStatus> statuses);

    boolean existsByCopy_Material_IdAndStatusIn(Long materialId, Collection<LoanStatus> statuses);

    Optional<Loan> findByCopy_IdAndStatusIn(Long copyId, Collection<LoanStatus> statuses);

    Optional<Loan> findByReservation_Id(Long reservationId);

    Long countByUser_IdAndStatusIn(Long userId, Collection<LoanStatus> statuses);

}
