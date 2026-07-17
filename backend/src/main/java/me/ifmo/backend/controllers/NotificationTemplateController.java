package me.ifmo.backend.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.dto.notification.request.CreateNotificationTemplateRequest;
import me.ifmo.backend.dto.notification.request.UpdateNotificationTemplateRequest;
import me.ifmo.backend.dto.notification.response.NotificationTemplateResponse;
import me.ifmo.backend.entities.enums.NotificationTemplateStatus;
import me.ifmo.backend.services.NotificationTemplateService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/notification-templates")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class NotificationTemplateController {

    private final NotificationTemplateService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationTemplateResponse create(@AuthenticationPrincipal UserDetails userDetails, @Valid @RequestBody CreateNotificationTemplateRequest request) {
        return service.create(Long.valueOf(userDetails.getUsername()), request);
    }

    @GetMapping("/{id}")
    public NotificationTemplateResponse getTemplateById(@PathVariable Long id) {
        return service.getTemplateById(id);
    }

    @PatchMapping("/{id}")
    public NotificationTemplateResponse update(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id, @Valid @RequestBody UpdateNotificationTemplateRequest request) {
        return service.update(Long.valueOf(userDetails.getUsername()), id, request);
    }

    @PostMapping("/{id}/archive")
    public NotificationTemplateResponse archive(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        return service.archive(Long.valueOf(userDetails.getUsername()), id);
    }

    @GetMapping
    public PageResponse<NotificationTemplateResponse> search(@RequestParam(required = false) NotificationTemplateStatus status, Pageable pageable) {
        return service.search(status, pageable);
    }
}
