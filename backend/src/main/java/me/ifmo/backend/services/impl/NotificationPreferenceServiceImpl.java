package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.notification.request.UpdateNotificationPreferenceRequest;
import me.ifmo.backend.dto.notification.response.NotificationPreferenceResponse;
import me.ifmo.backend.entities.NotificationPreference;
import me.ifmo.backend.user.domain.User;
import me.ifmo.backend.entities.enums.AuditAction;
import me.ifmo.backend.entities.enums.AuditEntityType;
import me.ifmo.backend.entities.enums.NotificationChannel;
import me.ifmo.backend.entities.enums.NotificationType;
import me.ifmo.backend.shared.error.BusinessRuleException;
import me.ifmo.backend.shared.error.ResourceNotFoundException;
import me.ifmo.backend.mappers.NotificationPreferenceMapper;
import me.ifmo.backend.repositories.NotificationPreferenceRepository;
import me.ifmo.backend.user.persistence.UserRepository;
import me.ifmo.backend.services.AuditLogService;
import me.ifmo.backend.services.NotificationPreferenceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NotificationPreferenceServiceImpl implements NotificationPreferenceService {

    private static final Set<NotificationType> MANDATORY_TYPES = EnumSet.of(
            NotificationType.ACCOUNT_ACTIVATION,
            NotificationType.PASSWORD_RECOVERY,
            NotificationType.ACCOUNT_STATUS_CHANGED,
            NotificationType.FINE_CREATED,
            NotificationType.FINE_PAID,
            NotificationType.USER_BLOCKED,
            NotificationType.USER_UNBLOCKED,
            NotificationType.USER_WARNING_CREATED
    );

    private final NotificationPreferenceRepository repository;
    private final UserRepository userRepository;
    private final NotificationPreferenceMapper notificationPreferenceMapper;
    private final AuditLogService auditLogService;

    static boolean isMandatory(NotificationType type) {
        return MANDATORY_TYPES.contains(type);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationPreferenceResponse> getPreferences(Long actorUserId) {
        Map<NotificationType, Map<NotificationChannel, NotificationPreference>> existing = new EnumMap<>(NotificationType.class);
        repository.findByUser_Id(actorUserId)
                .forEach(preference -> existing
                        .computeIfAbsent(preference.getType(), type -> new EnumMap<>(NotificationChannel.class))
                        .put(preference.getChannel(), preference));

        return EnumSet.allOf(NotificationType.class).stream()
                .flatMap(type -> EnumSet.allOf(NotificationChannel.class).stream()
                        .map(channel -> {
                            NotificationPreference preference = existing.getOrDefault(type, Map.of()).get(channel);
                            if (preference != null)
                                return notificationPreferenceMapper.toResponse(preference, isMandatory(type));

                            return new NotificationPreferenceResponse(null, actorUserId, type, channel,
                                    true, channel == NotificationChannel.EMAIL, isMandatory(type), null, null);
                        }))
                .toList();
    }

    @Override
    @Transactional
    public NotificationPreferenceResponse update(Long actorUserId, UpdateNotificationPreferenceRequest request) {
        if (isMandatory(request.type()) && Boolean.FALSE.equals(request.enabled()))
            throw new BusinessRuleException("Mandatory notification type cannot be disabled");

        if (Boolean.TRUE.equals(request.preferred()) && Boolean.FALSE.equals(request.enabled()))
            throw new BusinessRuleException("Disabled notification channel cannot be preferred");

        User user = userRepository.findById(actorUserId).orElseThrow(
                () -> new ResourceNotFoundException("User with id '%s' not found".formatted(actorUserId)));

        NotificationPreference preference = repository.findByUser_IdAndTypeAndChannel(
                        actorUserId, request.type(), request.channel())
                .orElseGet(() -> NotificationPreference.builder()
                        .user(user)
                        .type(request.type())
                        .channel(request.channel())
                        .build());

        boolean isNew = preference.getId() == null;

        preference.setEnabled(request.enabled());
        preference.setPreferred(request.preferred());

        if (Boolean.TRUE.equals(request.preferred())) {
            repository.findByUser_Id(actorUserId).stream()
                    .filter(existing -> existing.getType() == request.type())
                    .filter(existing -> existing.getChannel() != request.channel())
                    .forEach(existing -> existing.setPreferred(false));
        }

        NotificationPreference saved = repository.save(preference);
        auditLogService.record(actorUserId, AuditEntityType.NOTIFICATION_PREFERENCE, saved.getId(),
                isNew ? AuditAction.CREATE : AuditAction.UPDATE,
                Map.of("type", saved.getType().name(), "channel", saved.getChannel().name(),
                        "enabled", saved.getEnabled(), "preferred", saved.getPreferred()));
        return notificationPreferenceMapper.toResponse(saved, isMandatory(saved.getType()));
    }

    public boolean isNotificationEnabled(Long userId, NotificationType type, NotificationChannel channel) {
        if (isMandatory(type))
            return true;

        return repository.findByUser_IdAndTypeAndChannel(userId, type, channel)
                .map(NotificationPreference::getEnabled)
                .orElse(true);
    }

    public NotificationChannel resolveChannel(Long userId, NotificationType type, NotificationChannel requestedChannel) {
        if (requestedChannel != null)
            return requestedChannel;

        return repository.findByUser_Id(userId).stream()
                .filter(preference -> preference.getType() == type)
                .filter(preference -> Boolean.TRUE.equals(preference.getPreferred()))
                .map(NotificationPreference::getChannel)
                .findFirst()
                .orElse(NotificationChannel.EMAIL);
    }
}
