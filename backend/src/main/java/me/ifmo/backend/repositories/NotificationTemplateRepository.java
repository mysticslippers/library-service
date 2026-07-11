package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.NotificationTemplate;
import me.ifmo.backend.entities.enums.NotificationChannel;
import me.ifmo.backend.entities.enums.NotificationTemplateStatus;
import me.ifmo.backend.entities.enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long> {

    Optional<NotificationTemplate> findByTypeAndChannelAndStatus(NotificationType type, NotificationChannel channel, NotificationTemplateStatus status);

    boolean existsByTypeAndChannelAndStatus(NotificationType type, NotificationChannel channel, NotificationTemplateStatus status);

    Page<NotificationTemplate> findByStatus(NotificationTemplateStatus status, Pageable pageable);
}
