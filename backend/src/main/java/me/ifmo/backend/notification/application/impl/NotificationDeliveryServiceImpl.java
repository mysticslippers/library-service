package me.ifmo.backend.notification.application.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.ifmo.backend.notification.web.response.NotificationDeliveryBatchResponse;
import me.ifmo.backend.audit.domain.enums.AuditAction;
import me.ifmo.backend.audit.domain.enums.AuditEntityType;
import me.ifmo.backend.notification.domain.enums.NotificationStatus;
import me.ifmo.backend.shared.exceptions.BusinessRuleException;
import me.ifmo.backend.audit.application.AuditLogService;
import me.ifmo.backend.notification.application.NotificationDeliveryClient;
import me.ifmo.backend.notification.application.NotificationDeliveryResult;
import me.ifmo.backend.notification.application.NotificationDeliveryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationDeliveryServiceImpl implements NotificationDeliveryService {

    private final NotificationDeliveryStateService deliveryStateService;
    private final NotificationDeliveryClient deliveryClient;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public NotificationDeliveryBatchResponse processPending(Long actorUserId, int limit) {
        if (limit <= 0 || limit > 100)
            throw new BusinessRuleException("Delivery batch limit must be between 1 and 100");

        int sent = 0;
        int failed = 0;
        int undelivered = 0;
        int unknown = 0;

        int recovered = deliveryStateService.recoverStaleSending();
        if (recovered > 0)
            log.warn("Marked {} interrupted notification deliveries as UNKNOWN", recovered);

        for (int i = 0; i < limit; i++) {
            var claimed = deliveryStateService.claimNext();
            if (claimed.isEmpty())
                break;
            var task = claimed.get();
            NotificationDeliveryResult result;
            try {
                result = deliveryClient.send(task);
            } catch (RuntimeException exception) {
                result = NotificationDeliveryResult.unknown(null, "Unexpected delivery failure: "
                        + exception.getClass().getSimpleName());
            }

            NotificationStatus status = deliveryStateService.complete(task, result);
            switch (status) {
                case SENT -> sent++;
                case FAILED -> failed++;
                case UNDELIVERED -> undelivered++;
                case UNKNOWN -> unknown++;
                default -> throw new IllegalStateException("Unexpected delivery result status: " + status);
            }
        }

        int processed = sent + failed + undelivered + unknown;
        auditLogService.record(actorUserId, AuditEntityType.NOTIFICATION, null, AuditAction.UPDATE,
                Map.of("action", "PROCESS_PENDING", "processed", processed, "createdAt", LocalDateTime.now().toString()));
        return new NotificationDeliveryBatchResponse(processed, sent, failed, undelivered, unknown);
    }
}
