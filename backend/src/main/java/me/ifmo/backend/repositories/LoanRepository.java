package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.Loan;
import me.ifmo.backend.entities.enums.LoanStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    Page<Loan> findByUser_Id(Long userId, Pageable pageable);

    Page<Loan> findByUser_IdAndStatus(Long userId, LoanStatus status, Pageable pageable);
}
