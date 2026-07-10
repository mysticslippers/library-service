package me.ifmo.backend.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.common.response.PageResponse;
import me.ifmo.backend.dto.notification.request.CreateNotificationRequest;
import me.ifmo.backend.dto.notification.request.NotificationSearchRequest;
import me.ifmo.backend.dto.notification.request.UpdateNotificationStatusRequest;
import me.ifmo.backend.dto.notification.response.NotificationDeliveryBatchResponse;
import me.ifmo.backend.dto.notification.response.NotificationResponse;
import me.ifmo.backend.services.NotificationDeliveryService;
import me.ifmo.backend.services.NotificationService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;
    private final NotificationDeliveryService deliveryService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    public NotificationResponse create(@Valid @RequestBody CreateNotificationRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    public NotificationResponse getNotificationById(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        return service.getNotificationById(Long.valueOf(userDetails.getUsername()), id);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    public NotificationResponse updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateNotificationStatusRequest request) {
        return service.updateStatus(id, request);
    }

    @PostMapping("/{id}/read")
    public NotificationResponse markAsRead(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        return service.markAsRead(Long.valueOf(userDetails.getUsername()), id);
    }

    @PostMapping("/{id}/resend")
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    public NotificationResponse resend(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        return service.resend(Long.valueOf(userDetails.getUsername()), id);
    }

    @PostMapping("/process-pending")
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    public NotificationDeliveryBatchResponse processPending(@AuthenticationPrincipal UserDetails userDetails, @RequestParam(defaultValue = "50") int limit) {
        return deliveryService.processPending(Long.valueOf(userDetails.getUsername()), limit);
    }

    @GetMapping
    public PageResponse<NotificationResponse> search(@AuthenticationPrincipal UserDetails userDetails, @Valid @ModelAttribute NotificationSearchRequest request, Pageable pageable) {
        return service.search(Long.valueOf(userDetails.getUsername()), request, pageable);
    }
}
