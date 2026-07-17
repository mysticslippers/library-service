package me.ifmo.backend.notification.application;

import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.notification.web.request.CreateNotificationRequest;
import me.ifmo.backend.notification.web.request.NotificationSearchRequest;
import me.ifmo.backend.notification.web.request.UpdateNotificationStatusRequest;
import me.ifmo.backend.notification.web.response.NotificationResponse;
import org.springframework.data.domain.Pageable;

public interface NotificationService {

    NotificationResponse create(CreateNotificationRequest request);

    NotificationResponse getNotificationById(Long actorUserId, Long id);

    NotificationResponse updateStatus(Long id, UpdateNotificationStatusRequest request);

    NotificationResponse markAsRead(Long actorUserId, Long id);

    NotificationResponse resend(Long actorUserId, Long id);

    PageResponse<NotificationResponse> search(Long actorUserId, NotificationSearchRequest request, Pageable pageable);
}
