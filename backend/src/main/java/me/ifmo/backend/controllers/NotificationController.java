package me.ifmo.backend.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.common.response.PageResponse;
import me.ifmo.backend.dto.notification.request.CreateNotificationRequest;
import me.ifmo.backend.dto.notification.request.NotificationSearchRequest;
import me.ifmo.backend.dto.notification.request.UpdateNotificationStatusRequest;
import me.ifmo.backend.dto.notification.response.NotificationResponse;
import me.ifmo.backend.services.NotificationService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationResponse create(@Valid @RequestBody CreateNotificationRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    public NotificationResponse getNotificationById(@PathVariable Long id) {
        return service.getNotificationById(id);
    }

    @PatchMapping("/{id}/status")
    public NotificationResponse updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateNotificationStatusRequest request) {
        return service.updateStatus(id, request);
    }

    @GetMapping
    public PageResponse<NotificationResponse> search(@Valid @ModelAttribute NotificationSearchRequest request, Pageable pageable) {
        return service.search(request, pageable);
    }
}