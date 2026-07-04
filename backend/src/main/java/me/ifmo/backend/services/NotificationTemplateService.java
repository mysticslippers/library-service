package me.ifmo.backend.services;

import me.ifmo.backend.dto.common.response.PageResponse;
import me.ifmo.backend.dto.notification.request.CreateNotificationTemplateRequest;
import me.ifmo.backend.dto.notification.request.UpdateNotificationTemplateRequest;
import me.ifmo.backend.dto.notification.response.NotificationTemplateResponse;
import me.ifmo.backend.entities.enums.NotificationTemplateStatus;
import org.springframework.data.domain.Pageable;

public interface NotificationTemplateService {

    NotificationTemplateResponse create(Long actorUserId, CreateNotificationTemplateRequest request);

    NotificationTemplateResponse update(Long actorUserId, Long id, UpdateNotificationTemplateRequest request);

    NotificationTemplateResponse archive(Long actorUserId, Long id);

    NotificationTemplateResponse getTemplateById(Long id);

    PageResponse<NotificationTemplateResponse> search(NotificationTemplateStatus status, Pageable pageable);
}
