package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.AuditLog;
import me.ifmo.backend.entities.enums.AuditAction;
import me.ifmo.backend.entities.enums.AuditEntityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByUser_Id(Long userId, Pageable pageable);

    @Query("""
       SELECT auditLog FROM AuditLog auditLog
           WHERE (:actorUserId IS NULL OR auditLog.user.id = :actorUserId)
             AND (:type IS NULL OR auditLog.type = :type)
             AND (:entityId IS NULL OR auditLog.entityId = :entityId)
             AND (:action IS NULL OR auditLog.action = :action)
             AND (:createdFrom IS NULL OR auditLog.createdAt >= :createdFrom)
             AND (:createdTo IS NULL OR auditLog.createdAt <= :createdTo)
           ORDER BY auditLog.createdAt DESC
    """)
    Page<AuditLog> search(@Param("actorUserId") Long actorUserId,
                          @Param("type") AuditEntityType type,
                          @Param("entityId") Long entityId,
                          @Param("action") AuditAction action,
                          @Param("createdFrom") LocalDateTime createdFrom,
                          @Param("createdTo") LocalDateTime createdTo,
                          Pageable pageable);
}
