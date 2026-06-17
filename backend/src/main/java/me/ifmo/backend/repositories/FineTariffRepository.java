package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.FineTariff;
import me.ifmo.backend.entities.enums.FineTariffStatus;
import me.ifmo.backend.entities.enums.ViolationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface FineTariffRepository extends JpaRepository<FineTariff, Long> {

    Page<FineTariff> findByStatus(FineTariffStatus status, Pageable pageable);

    Page<FineTariff> findByViolationType(ViolationType violationType, Pageable pageable);

    Optional<FineTariff> findByViolationTypeAndStatus(ViolationType violationType, FineTariffStatus status);

    @Query("""
            SELECT fineTariff FROM FineTariff fineTariff
                WHERE fineTariff.violationType = :violationType
                    AND fineTariff.status = :status
                    AND fineTariff.validFrom <= :dateTime
                    AND (fineTariff.validTo IS NULL OR fineTariff.validTo > :dateTime)
                ORDER BY fineTariff.validFrom DESC
    """)
    Optional<FineTariff> findActualByViolationTypeAndStatus(@Param("violationType") ViolationType violationType,
                                                            @Param("status") FineTariffStatus status,
                                                            @Param("dateTime") LocalDateTime dateTime);
}
