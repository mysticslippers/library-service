package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.AuditLog;
import me.ifmo.backend.entities.enums.AuditEntityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByUser_Id(Long userId, Pageable pageable);

    Page<AuditLog> findByType(AuditEntityType type, Pageable pageable);
}
