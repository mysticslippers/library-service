package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.AuditLog;
import me.ifmo.backend.entities.enums.AuditAction;
import me.ifmo.backend.entities.enums.AuditEntityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByUser_Id(Long userId, Pageable pageable);

    Page<AuditLog> findByType(AuditEntityType type, Pageable pageable);

    Page<AuditLog> findByTypeAndEntityId(AuditEntityType type, Long entityId, Pageable pageable);

    Page<AuditLog> findByAction(AuditAction action, Pageable pageable);

    Page<AuditLog> findByTypeAndAction(AuditEntityType type, AuditAction action, Pageable pageable);

    Page<AuditLog> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to, Pageable pageable);

    Page<AuditLog> findByUser_IdAndCreatedAtBetween(Long userId, LocalDateTime from, LocalDateTime to, Pageable pageable);
}
