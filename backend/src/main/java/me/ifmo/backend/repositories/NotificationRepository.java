package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.Notification;
import me.ifmo.backend.entities.enums.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Collection;

public interface NotificationRepository extends JpaRepository<Notification, Long>, JpaSpecificationExecutor<Notification> {

    Page<Notification> findByUser_Id(Long userId, Pageable pageable);

    Page<Notification> findByStatus(NotificationStatus status, Pageable pageable);

    Page<Notification> findByStatusIn(Collection<NotificationStatus> statuses, Pageable pageable);

}
