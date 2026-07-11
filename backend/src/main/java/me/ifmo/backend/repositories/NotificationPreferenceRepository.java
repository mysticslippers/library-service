package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.NotificationPreference;
import me.ifmo.backend.entities.enums.NotificationChannel;
import me.ifmo.backend.entities.enums.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    Optional<NotificationPreference> findByUser_IdAndTypeAndChannel(Long userId, NotificationType type, NotificationChannel channel);

    List<NotificationPreference> findByUser_Id(Long userId);
}
