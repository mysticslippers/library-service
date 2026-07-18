package me.ifmo.backend.notification.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.notification.web.request.CreateNotificationTemplateRequest;
import me.ifmo.backend.notification.web.request.UpdateNotificationTemplateRequest;
import me.ifmo.backend.notification.web.response.NotificationTemplateResponse;
import me.ifmo.backend.notification.domain.enums.NotificationTemplateStatus;
import me.ifmo.backend.notification.application.NotificationTemplateService;
import org.springdoc.core.annotations.ParameterObject;
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
@Tag(name = "Notification Templates", description = "Administrative notification template management")
public class NotificationTemplateController {

    private final NotificationTemplateService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a notification template")
    public NotificationTemplateResponse create(@AuthenticationPrincipal UserDetails userDetails, @Valid @RequestBody CreateNotificationTemplateRequest request) {
        return service.create(Long.valueOf(userDetails.getUsername()), request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a notification template by ID")
    public NotificationTemplateResponse getTemplateById(@PathVariable Long id) {
        return service.getTemplateById(id);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a notification template")
    public NotificationTemplateResponse update(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id, @Valid @RequestBody UpdateNotificationTemplateRequest request) {
        return service.update(Long.valueOf(userDetails.getUsername()), id, request);
    }

    @PostMapping("/{id}/archive")
    @Operation(summary = "Archive a notification template")
    public NotificationTemplateResponse archive(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        return service.archive(Long.valueOf(userDetails.getUsername()), id);
    }

    @GetMapping
    @Operation(summary = "Search notification templates")
    public PageResponse<NotificationTemplateResponse> search(@RequestParam(required = false) NotificationTemplateStatus status, @ParameterObject Pageable pageable) {
        return service.search(status, pageable);
    }
}
