package me.ifmo.backend.library.persistence;

import me.ifmo.backend.library.domain.LibraryRule;
import me.ifmo.backend.library.domain.enums.LibraryRuleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface LibraryRuleRepository extends JpaRepository<LibraryRule, Long> {

    Page<LibraryRule> findByBranch_Id(Long branchId, Pageable pageable);

    Page<LibraryRule> findByStatus(LibraryRuleStatus status, Pageable pageable);

    Optional<LibraryRule> findByBranch_IdAndStatus(Long branchId, LibraryRuleStatus status);

    Page<LibraryRule> findByBranch_IdAndStatus(Long branchId, LibraryRuleStatus status, Pageable pageable);

    @Query("""
            SELECT libraryRule FROM LibraryRule libraryRule
                WHERE libraryRule.branch.id = :branchId
                    AND libraryRule.status = :status
                    AND libraryRule.validFrom <= :dateTime
                    AND (libraryRule.validTo IS NULL OR libraryRule.validTo > :dateTime)
                ORDER BY libraryRule.validFrom DESC
    """)
    Optional<LibraryRule> findActualByBranchIdAndStatus(@Param("branchId") Long branchId,
                                                        @Param("status") LibraryRuleStatus status,
                                                        @Param("dateTime") LocalDateTime dateTime);
}
