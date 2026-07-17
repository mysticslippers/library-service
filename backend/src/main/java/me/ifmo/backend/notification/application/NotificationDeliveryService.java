package me.ifmo.backend.notification.application;

import me.ifmo.backend.notification.web.response.NotificationDeliveryBatchResponse;

public interface NotificationDeliveryService {

    NotificationDeliveryBatchResponse processPending(Long actorUserId, int limit);
}
