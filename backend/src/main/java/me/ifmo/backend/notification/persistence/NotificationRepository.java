package me.ifmo.backend.notification.persistence;

import jakarta.persistence.LockModeType;
import me.ifmo.backend.notification.domain.Notification;
import me.ifmo.backend.notification.domain.enums.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long>, JpaSpecificationExecutor<Notification> {

    @Query(value = """
            SELECT * FROM notifications
            WHERE status = 'PENDING' AND attempt_count < :maxAttempts
            ORDER BY created_at, id
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<Notification> findNextPendingForUpdate(@Param("maxAttempts") int maxAttempts);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT notification FROM Notification notification WHERE notification.id = :id")
    Optional<Notification> findByIdForUpdate(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE notifications
            SET status = 'UNKNOWN', version = version + 1,
                error_message = 'Delivery interrupted or timed out; verify with the provider before retrying'
            WHERE status = 'SENDING' AND sending_started_at <= :cutoff
            """, nativeQuery = true)
    int markStaleSendingUnknown(@Param("cutoff") LocalDateTime cutoff);

    Page<Notification> findByUser_Id(Long userId, Pageable pageable);

    Page<Notification> findByStatus(NotificationStatus status, Pageable pageable);

    Page<Notification> findByStatusIn(Collection<NotificationStatus> statuses, Pageable pageable);

}
