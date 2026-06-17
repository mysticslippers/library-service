package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.Fine;
import me.ifmo.backend.entities.enums.FineStatus;
import me.ifmo.backend.entities.enums.ViolationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FineRepository extends JpaRepository<Fine, Long> {

    Page<Fine> findByUser_Id(Long userId, Pageable pageable);

    Page<Fine> findByUser_IdAndStatus(Long userId, FineStatus status, Pageable pageable);

    Page<Fine> findByUser_IdAndStatusIn(Long userId, Collection<FineStatus> statuses, Pageable pageable);

    List<Fine> findByLoan_Id(Long loanId);

    Optional<Fine> findByLoan_IdAndReasonAndStatus(Long loanId, ViolationType reason, FineStatus status);

    Page<Fine> findByReasonAndStatus(ViolationType reason, FineStatus status, Pageable pageable);
}
