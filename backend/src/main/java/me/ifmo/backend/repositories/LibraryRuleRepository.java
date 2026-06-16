package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.LibraryRule;
import me.ifmo.backend.entities.enums.LibraryRuleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface LibraryRuleRepository extends JpaRepository<LibraryRule, Long> {

    Page<LibraryRule> findByBranch_Id(Long branchId, Pageable pageable);
}
