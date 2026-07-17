package me.ifmo.backend.notification.application;

import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.notification.web.request.CreateNotificationTemplateRequest;
import me.ifmo.backend.notification.web.request.UpdateNotificationTemplateRequest;
import me.ifmo.backend.notification.web.response.NotificationTemplateResponse;
import me.ifmo.backend.notification.domain.enums.NotificationTemplateStatus;
import org.springframework.data.domain.Pageable;

public interface NotificationTemplateService {

    NotificationTemplateResponse create(Long actorUserId, CreateNotificationTemplateRequest request);

    NotificationTemplateResponse update(Long actorUserId, Long id, UpdateNotificationTemplateRequest request);

    NotificationTemplateResponse archive(Long actorUserId, Long id);

    NotificationTemplateResponse getTemplateById(Long id);

    PageResponse<NotificationTemplateResponse> search(NotificationTemplateStatus status, Pageable pageable);
}
