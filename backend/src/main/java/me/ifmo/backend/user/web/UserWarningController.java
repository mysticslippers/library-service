package me.ifmo.backend.user.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.user.web.request.CancelUserWarningRequest;
import me.ifmo.backend.user.web.request.CreateUserWarningRequest;
import me.ifmo.backend.user.web.response.UserWarningResponse;
import me.ifmo.backend.user.domain.enums.UserWarningStatus;
import me.ifmo.backend.user.application.UserWarningService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user-warnings")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
@Tag(name = "User Warnings", description = "Warnings issued to library users")
public class UserWarningController {

    private final UserWarningService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a user warning")
    public UserWarningResponse create(@AuthenticationPrincipal UserDetails userDetails, @Valid @RequestBody CreateUserWarningRequest request) {
        return service.create(Long.valueOf(userDetails.getUsername()), request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a user warning by ID")
    public UserWarningResponse getUserWarningById(@PathVariable Long id) {
        return service.getUserWarningById(id);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a user warning")
    public UserWarningResponse cancel(@PathVariable Long id, @Valid @RequestBody CancelUserWarningRequest request) {
        return service.cancel(id, request);
    }

    @PostMapping("/{id}/expire")
    @Operation(summary = "Mark a user warning as expired")
    public UserWarningResponse expire(@PathVariable Long id) {
        return service.expire(id);
    }

    @GetMapping
    @Operation(summary = "Search user warnings")
    public PageResponse<UserWarningResponse> search(@RequestParam(required = false) Long userId, @RequestParam(required = false) Long createdByUserId, @RequestParam(required = false) UserWarningStatus status, @ParameterObject Pageable pageable) {
        return service.search(userId, createdByUserId, status, pageable);
    }
}
