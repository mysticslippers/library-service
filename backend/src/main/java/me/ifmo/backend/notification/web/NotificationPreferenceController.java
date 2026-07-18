package me.ifmo.backend.notification.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.notification.web.request.UpdateNotificationPreferenceRequest;
import me.ifmo.backend.notification.web.response.NotificationPreferenceResponse;
import me.ifmo.backend.notification.application.NotificationPreferenceService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/notification-preferences")
@RequiredArgsConstructor
@Tag(name = "Notification Preferences", description = "The current user's notification channel preferences")
public class NotificationPreferenceController {

    private final NotificationPreferenceService service;

    @GetMapping
    @Operation(summary = "Get the current user's notification preferences")
    public List<NotificationPreferenceResponse> getPreferences(@AuthenticationPrincipal UserDetails userDetails) {
        return service.getPreferences(Long.valueOf(userDetails.getUsername()));
    }

    @PutMapping
    @Operation(summary = "Update a notification preference")
    public NotificationPreferenceResponse update(@AuthenticationPrincipal UserDetails userDetails, @Valid @RequestBody UpdateNotificationPreferenceRequest request) {
        return service.update(Long.valueOf(userDetails.getUsername()), request);
    }
}
