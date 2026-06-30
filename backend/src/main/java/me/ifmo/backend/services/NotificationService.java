package me.ifmo.backend.services;

import me.ifmo.backend.dto.notification.request.CreateNotificationRequest;
import me.ifmo.backend.dto.notification.request.UpdateNotificationStatusRequest;
import me.ifmo.backend.dto.notification.response.NotificationResponse;

public interface NotificationService {

    NotificationResponse create(CreateNotificationRequest request);

    NotificationResponse getNotificationById(Long id);

    NotificationResponse updateStatus(Long id, UpdateNotificationStatusRequest request);
}
