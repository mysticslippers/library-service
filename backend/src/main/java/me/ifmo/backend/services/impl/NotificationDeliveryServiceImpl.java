package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.notification.request.UpdateNotificationStatusRequest;
import me.ifmo.backend.dto.notification.response.NotificationDeliveryBatchResponse;
import me.ifmo.backend.entities.Notification;
import me.ifmo.backend.entities.enums.AuditAction;
import me.ifmo.backend.entities.enums.AuditEntityType;
import me.ifmo.backend.entities.enums.NotificationStatus;
import me.ifmo.backend.exceptions.domain.BusinessRuleException;
import me.ifmo.backend.repositories.NotificationRepository;
import me.ifmo.backend.services.AuditLogService;
import me.ifmo.backend.services.NotificationDeliveryClient;
import me.ifmo.backend.services.NotificationDeliveryResult;
import me.ifmo.backend.services.NotificationDeliveryService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NotificationDeliveryServiceImpl implements NotificationDeliveryService {

    private final NotificationRepository repository;
    private final NotificationDeliveryClient deliveryClient;
    private final NotificationServiceImpl notificationService;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public NotificationDeliveryBatchResponse processPending(Long actorUserId, int limit) {
        if (limit <= 0 || limit > 100)
            throw new BusinessRuleException("Delivery batch limit must be between 1 and 100");

        int sent = 0;
        int failed = 0;
        int undelivered = 0;

        for (Notification notification : repository.findByStatusIn(Set.of(NotificationStatus.PENDING), PageRequest.of(0, limit))) {
            NotificationDeliveryResult result = deliveryClient.send(notification);

            if (result.success()) {
                notificationService.updateStatus(notification.getId(),
                        new UpdateNotificationStatusRequest(NotificationStatus.SENT, result.externalMessageId(), null));
                sent++;
            } else {
                var response = notificationService.updateStatus(notification.getId(),
                        new UpdateNotificationStatusRequest(NotificationStatus.FAILED, null, result.errorMessage()));
                if (response.status() == NotificationStatus.UNDELIVERED)
                    undelivered++;
                else
                    failed++;
            }
        }

        int processed = sent + failed + undelivered;
        auditLogService.record(actorUserId, AuditEntityType.NOTIFICATION, null, AuditAction.UPDATE,
                Map.of("action", "PROCESS_PENDING", "processed", processed, "createdAt", LocalDateTime.now().toString()));
        return new NotificationDeliveryBatchResponse(processed, sent, failed, undelivered);
    }
}
