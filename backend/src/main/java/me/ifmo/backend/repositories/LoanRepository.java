package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.Loan;
import me.ifmo.backend.entities.enums.LoanStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    Page<Loan> findByUser_Id(Long userId, Pageable pageable);

    Page<Loan> findByUser_IdAndStatus(Long userId, LoanStatus status, Pageable pageable);

    Page<Loan> findByUser_IdAndStatusIn(Long userId, Collection<LoanStatus> statuses, Pageable pageable);

    Page<Loan> findByBranch_IdAndStatus(Long branchId, LoanStatus status, Pageable pageable);

    Page<Loan> findByCopy_IdAndStatusIn(Long copyId, Collection<LoanStatus> statuses, Pageable pageable);
}
