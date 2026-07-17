package me.ifmo.backend.notification.application;

import me.ifmo.backend.notification.web.request.UpdateNotificationPreferenceRequest;
import me.ifmo.backend.notification.web.response.NotificationPreferenceResponse;

import java.util.List;

public interface NotificationPreferenceService {

    List<NotificationPreferenceResponse> getPreferences(Long actorUserId);

    NotificationPreferenceResponse update(Long actorUserId, UpdateNotificationPreferenceRequest request);
}
