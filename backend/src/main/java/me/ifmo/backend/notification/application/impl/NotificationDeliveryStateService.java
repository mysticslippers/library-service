package me.ifmo.backend.notification.application.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.notification.application.NotificationDeliveryResult;
import me.ifmo.backend.notification.application.NotificationDeliveryTask;
import me.ifmo.backend.notification.domain.Notification;
import me.ifmo.backend.notification.domain.enums.NotificationStatus;
import me.ifmo.backend.notification.persistence.NotificationRepository;
import me.ifmo.backend.shared.exceptions.BusinessRuleException;
import me.ifmo.backend.shared.exceptions.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NotificationDeliveryStateService {

    private final NotificationRepository repository;

    @Value("${notification.delivery.max-attempts:3}")
    private int maxAttempts;

    @Value("${notification.delivery.sending-timeout-ms:300000}")
    private long sendingTimeoutMs;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<NotificationDeliveryTask> claimNext() {
        if (maxAttempts <= 0)
            throw new BusinessRuleException("Notification delivery max attempts must be positive");

        return repository.findNextPendingForUpdate(maxAttempts).map(notification -> {
            notification.setStatus(NotificationStatus.SENDING);
            notification.setSendingStartedAt(LocalDateTime.now());
            notification.setAttemptCount(notification.getAttemptCount() + 1);
            notification.setSentAt(null);
            notification.setExternalMessageId(null);
            notification.setErrorMessage(null);
            return new NotificationDeliveryTask(notification.getId(), notification.getAttemptCount(),
                    notification.getChannel(), notification.getUser().getEmail(), notification.getUser().getPhone(),
                    notification.getSubject(), notification.getBody());
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotificationStatus complete(NotificationDeliveryTask task, NotificationDeliveryResult result) {
        Notification notification = repository.findByIdForUpdate(task.id()).orElseThrow(
                () -> new ResourceNotFoundException("Notification with id '%s' not found".formatted(task.id())));
        if (notification.getStatus() != NotificationStatus.SENDING
                || notification.getAttemptCount() != task.attemptNumber())
            throw new BusinessRuleException("Notification delivery attempt is no longer active");

        if (result == null || result.outcome() == null)
            result = NotificationDeliveryResult.unknown(null, "Delivery client returned no result");
        if (result.success() && (result.externalMessageId() == null || result.externalMessageId().isBlank()))
            result = NotificationDeliveryResult.unknown(null, "Provider accepted the message without a message id");

        NotificationStatus status = switch (result.outcome()) {
            case SUCCESS -> NotificationStatus.SENT;
            case FAILURE -> notification.getAttemptCount() >= maxAttempts
                    ? NotificationStatus.UNDELIVERED : NotificationStatus.FAILED;
            case UNKNOWN -> NotificationStatus.UNKNOWN;
        };
        notification.setStatus(status);
        notification.setExternalMessageId(result.externalMessageId());
        if (status == NotificationStatus.SENT) {
            notification.setSentAt(LocalDateTime.now());
            notification.setErrorMessage(null);
        } else {
            notification.setErrorMessage(result.errorMessage() == null || result.errorMessage().isBlank()
                    ? "Notification delivery could not be confirmed" : result.errorMessage());
        }
        return status;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recoverStaleSending() {
        if (sendingTimeoutMs <= 0)
            throw new BusinessRuleException("Notification sending timeout must be positive");

        return repository.markStaleSendingUnknown(LocalDateTime.now().minus(Duration.ofMillis(sendingTimeoutMs)));
    }
}
