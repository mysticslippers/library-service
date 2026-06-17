package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.Notification;
import me.ifmo.backend.entities.enums.NotificationChannel;
import me.ifmo.backend.entities.enums.NotificationStatus;
import me.ifmo.backend.entities.enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUser_Id(Long userId, Pageable pageable);

    Page<Notification> findByUser_IdAndStatus(Long userId, NotificationStatus status, Pageable pageable);

    Page<Notification> findByUser_IdAndType(Long userId, NotificationType type, Pageable pageable);

    Page<Notification> findByStatus(NotificationStatus status, Pageable pageable);

    List<Notification> findByStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(Collection<NotificationStatus> statuses, LocalDateTime createdAt);

    Page<Notification> findByType(NotificationType type, Pageable pageable);

    Page<Notification> findByChannelAndStatus(NotificationChannel channel, NotificationStatus status, Pageable pageable);

    Page<Notification> findByReservation_Id(Long reservationId, Pageable pageable);

    Page<Notification> findByLoan_Id(Long loanId, Pageable pageable);

    Page<Notification> findByFine_Id(Long fineId, Pageable pageable);
}
