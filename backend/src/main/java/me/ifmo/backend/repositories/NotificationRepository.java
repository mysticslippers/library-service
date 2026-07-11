package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.Notification;
import me.ifmo.backend.entities.enums.NotificationChannel;
import me.ifmo.backend.entities.enums.NotificationStatus;
import me.ifmo.backend.entities.enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUser_Id(Long userId, Pageable pageable);

    Page<Notification> findByStatus(NotificationStatus status, Pageable pageable);

    Page<Notification> findByStatusIn(Collection<NotificationStatus> statuses, Pageable pageable);

    @Query("""
       SELECT notification FROM Notification notification
           WHERE (:userId IS NULL OR notification.user.id = :userId)
             AND (:reservationId IS NULL OR notification.reservation.id = :reservationId)
             AND (:loanId IS NULL OR notification.loan.id = :loanId)
             AND (:fineId IS NULL OR notification.fine.id = :fineId)
             AND (:type IS NULL OR notification.type = :type)
             AND (:channel IS NULL OR notification.channel = :channel)
             AND (:status IS NULL OR notification.status = :status)
             AND (:createdFrom IS NULL OR notification.createdAt >= :createdFrom)
             AND (:createdTo IS NULL OR notification.createdAt <= :createdTo)
             AND (:sentFrom IS NULL OR notification.sentAt >= :sentFrom)
             AND (:sentTo IS NULL OR notification.sentAt <= :sentTo)
             AND (:query IS NULL OR :query = ''
                  OR lower(coalesce(notification.subject, '')) LIKE lower(concat('%', :query, '%'))
                  OR lower(coalesce(notification.body, '')) LIKE lower(concat('%', :query, '%'))
                  OR lower(notification.user.email) LIKE lower(concat('%', :query, '%')))
    """)
    Page<Notification> search(@Param("userId") Long userId,
                              @Param("reservationId") Long reservationId,
                              @Param("loanId") Long loanId,
                              @Param("fineId") Long fineId,
                              @Param("type") NotificationType type,
                              @Param("channel") NotificationChannel channel,
                              @Param("status") NotificationStatus status,
                              @Param("createdFrom") LocalDateTime createdFrom,
                              @Param("createdTo") LocalDateTime createdTo,
                              @Param("sentFrom") LocalDateTime sentFrom,
                              @Param("sentTo") LocalDateTime sentTo,
                              @Param("query") String query,
                              Pageable pageable);
}
