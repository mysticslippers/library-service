package me.ifmo.backend.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.dto.user.request.CancelUserWarningRequest;
import me.ifmo.backend.dto.user.request.CreateUserWarningRequest;
import me.ifmo.backend.dto.user.response.UserWarningResponse;
import me.ifmo.backend.entities.enums.UserWarningStatus;
import me.ifmo.backend.services.UserWarningService;
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
public class UserWarningController {

    private final UserWarningService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserWarningResponse create(@AuthenticationPrincipal UserDetails userDetails, @Valid @RequestBody CreateUserWarningRequest request) {
        return service.create(Long.valueOf(userDetails.getUsername()), request);
    }

    @GetMapping("/{id}")
    public UserWarningResponse getUserWarningById(@PathVariable Long id) {
        return service.getUserWarningById(id);
    }

    @PostMapping("/{id}/cancel")
    public UserWarningResponse cancel(@PathVariable Long id, @Valid @RequestBody CancelUserWarningRequest request) {
        return service.cancel(id, request);
    }

    @PostMapping("/{id}/expire")
    public UserWarningResponse expire(@PathVariable Long id) {
        return service.expire(id);
    }

    @GetMapping
    public PageResponse<UserWarningResponse> search(@RequestParam(required = false) Long userId, @RequestParam(required = false) Long createdByUserId, @RequestParam(required = false) UserWarningStatus status, Pageable pageable) {
        return service.search(userId, createdByUserId, status, pageable);
    }
}
