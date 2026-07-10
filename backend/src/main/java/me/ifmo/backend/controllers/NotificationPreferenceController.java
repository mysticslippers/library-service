package me.ifmo.backend.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.notification.request.UpdateNotificationPreferenceRequest;
import me.ifmo.backend.dto.notification.response.NotificationPreferenceResponse;
import me.ifmo.backend.services.NotificationPreferenceService;
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
