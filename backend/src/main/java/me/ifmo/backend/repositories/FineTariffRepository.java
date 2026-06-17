package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.FineTariff;
import me.ifmo.backend.entities.enums.FineTariffStatus;
import me.ifmo.backend.entities.enums.ViolationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FineTariffRepository extends JpaRepository<FineTariff, Long> {

    Optional<FineTariff> findByViolationTypeAndStatus(ViolationType violationType, FineTariffStatus status);
}
