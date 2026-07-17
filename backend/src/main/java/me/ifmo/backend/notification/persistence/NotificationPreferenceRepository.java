package me.ifmo.backend.notification.persistence;

import me.ifmo.backend.notification.domain.NotificationPreference;
import me.ifmo.backend.notification.domain.enums.NotificationChannel;
import me.ifmo.backend.notification.domain.enums.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    Optional<NotificationPreference> findByUser_IdAndTypeAndChannel(Long userId, NotificationType type, NotificationChannel channel);

    List<NotificationPreference> findByUser_Id(Long userId);
}
