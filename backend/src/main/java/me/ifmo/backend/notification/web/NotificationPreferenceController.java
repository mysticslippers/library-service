package me.ifmo.backend.notification.web;

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
public class NotificationPreferenceController {

    private final NotificationPreferenceService service;

    @GetMapping
    public List<NotificationPreferenceResponse> getPreferences(@AuthenticationPrincipal UserDetails userDetails) {
        return service.getPreferences(Long.valueOf(userDetails.getUsername()));
    }

    @PutMapping
    public NotificationPreferenceResponse update(@AuthenticationPrincipal UserDetails userDetails, @Valid @RequestBody UpdateNotificationPreferenceRequest request) {
        return service.update(Long.valueOf(userDetails.getUsername()), request);
    }
}
