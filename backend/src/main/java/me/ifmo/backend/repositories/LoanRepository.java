package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.Loan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    Page<Loan> findByUser_Id(Long userId, Pageable pageable);
}
