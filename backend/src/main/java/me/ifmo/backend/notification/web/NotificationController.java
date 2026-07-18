package me.ifmo.backend.notification.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.notification.web.request.CreateNotificationRequest;
import me.ifmo.backend.notification.web.request.NotificationSearchRequest;
import me.ifmo.backend.notification.web.request.UpdateNotificationStatusRequest;
import me.ifmo.backend.notification.web.response.NotificationDeliveryBatchResponse;
import me.ifmo.backend.notification.web.response.NotificationResponse;
import me.ifmo.backend.notification.application.NotificationDeliveryService;
import me.ifmo.backend.notification.application.NotificationService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "User notifications and delivery processing")
public class NotificationController {

    private final NotificationService service;
    private final NotificationDeliveryService deliveryService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    @Operation(summary = "Create a notification")
    public NotificationResponse create(@Valid @RequestBody CreateNotificationRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a notification by ID")
    public NotificationResponse getNotificationById(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        return service.getNotificationById(Long.valueOf(userDetails.getUsername()), id);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    @Operation(summary = "Update a notification's status")
    public NotificationResponse updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateNotificationStatusRequest request) {
        return service.updateStatus(id, request);
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark a notification as read")
    public NotificationResponse markAsRead(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        return service.markAsRead(Long.valueOf(userDetails.getUsername()), id);
    }

    @PostMapping("/{id}/resend")
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    @Operation(summary = "Schedule a notification for redelivery")
    public NotificationResponse resend(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        return service.resend(Long.valueOf(userDetails.getUsername()), id);
    }

    @PostMapping("/process-pending")
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    @Operation(summary = "Process pending notification deliveries")
    public NotificationDeliveryBatchResponse processPending(@AuthenticationPrincipal UserDetails userDetails, @RequestParam(defaultValue = "50") int limit) {
        return deliveryService.processPending(Long.valueOf(userDetails.getUsername()), limit);
    }

    @GetMapping
    @Operation(summary = "Search notifications")
    public PageResponse<NotificationResponse> search(@AuthenticationPrincipal UserDetails userDetails, @Valid @ModelAttribute NotificationSearchRequest request, @ParameterObject Pageable pageable) {
        return service.search(Long.valueOf(userDetails.getUsername()), request, pageable);
    }
}
