package me.ifmo.backend.services;

import me.ifmo.backend.dto.notification.response.NotificationDeliveryBatchResponse;

public interface NotificationDeliveryService {

    NotificationDeliveryBatchResponse processPending(Long actorUserId, int limit);
}
