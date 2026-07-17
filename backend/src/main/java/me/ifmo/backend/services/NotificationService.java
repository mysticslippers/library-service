package me.ifmo.backend.services;

import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.dto.notification.request.CreateNotificationRequest;
import me.ifmo.backend.dto.notification.request.NotificationSearchRequest;
import me.ifmo.backend.dto.notification.request.UpdateNotificationStatusRequest;
import me.ifmo.backend.dto.notification.response.NotificationResponse;
import org.springframework.data.domain.Pageable;

public interface NotificationService {

    NotificationResponse create(CreateNotificationRequest request);

    NotificationResponse getNotificationById(Long actorUserId, Long id);

    NotificationResponse updateStatus(Long id, UpdateNotificationStatusRequest request);

    NotificationResponse markAsRead(Long actorUserId, Long id);

    NotificationResponse resend(Long actorUserId, Long id);

    PageResponse<NotificationResponse> search(Long actorUserId, NotificationSearchRequest request, Pageable pageable);
}
