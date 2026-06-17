package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.Notification;
import me.ifmo.backend.entities.enums.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUser_Id(Long userId, Pageable pageable);

    Page<Notification> findByUser_IdAndStatus(Long userId, NotificationStatus status, Pageable pageable);
}
