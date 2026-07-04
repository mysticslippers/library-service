package me.ifmo.backend.services;

import me.ifmo.backend.dto.notification.request.UpdateNotificationPreferenceRequest;
import me.ifmo.backend.dto.notification.response.NotificationPreferenceResponse;

import java.util.List;

public interface NotificationPreferenceService {

    List<NotificationPreferenceResponse> getPreferences(Long actorUserId);

    NotificationPreferenceResponse update(Long actorUserId, UpdateNotificationPreferenceRequest request);
}
