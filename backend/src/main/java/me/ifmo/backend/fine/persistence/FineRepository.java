package me.ifmo.backend.fine.persistence;

import me.ifmo.backend.fine.domain.Fine;
import me.ifmo.backend.fine.domain.enums.FineStatus;
import me.ifmo.backend.fine.domain.enums.ViolationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FineRepository extends JpaRepository<Fine, Long>, JpaSpecificationExecutor<Fine> {

    @Query(value = "SELECT fine.* FROM fines fine WHERE fine.id = :id FOR NO KEY UPDATE", nativeQuery = true)
    Optional<Fine> findByIdForUpdate(@Param("id") Long id);

    Page<Fine> findByUser_Id(Long userId, Pageable pageable);

    Page<Fine> findByUser_IdAndStatus(Long userId, FineStatus status, Pageable pageable);

    Optional<Fine> findByLoan_IdAndReasonAndStatus(Long loanId, ViolationType reason, FineStatus status);

    Long countByUser_IdAndStatus(Long userId, FineStatus status);

}
