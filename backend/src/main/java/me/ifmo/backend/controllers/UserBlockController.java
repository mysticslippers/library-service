package me.ifmo.backend.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.common.response.PageResponse;
import me.ifmo.backend.dto.user.request.CancelUserBlockRequest;
import me.ifmo.backend.dto.user.request.CreateUserBlockRequest;
import me.ifmo.backend.dto.user.response.UserBlockResponse;
import me.ifmo.backend.entities.enums.UserBlockStatus;
import me.ifmo.backend.services.UserBlockService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user-blocks")
@RequiredArgsConstructor
public class UserBlockController {

    private final UserBlockService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserBlockResponse create(@AuthenticationPrincipal UserDetails userDetails, @Valid @RequestBody CreateUserBlockRequest request) {
        return service.create(Long.valueOf(userDetails.getUsername()), request);
    }

    @GetMapping("/{id}")
    public UserBlockResponse getUserBlockById(@PathVariable Long id) {
        return service.getUserBlockById(id);
    }

    @PostMapping("/{id}/cancel")
    public UserBlockResponse cancel(@PathVariable Long id, @Valid @RequestBody CancelUserBlockRequest request) {
        return service.cancel(id, request);
    }

    @PostMapping("/{id}/expire")
    public UserBlockResponse expire(@PathVariable Long id) {
        return service.expire(id);
    }

    @GetMapping
    public PageResponse<UserBlockResponse> search(@RequestParam(required = false) Long userId, @RequestParam(required = false) Long createdByUserId,
                                                  @RequestParam(required = false) UserBlockStatus status, Pageable pageable) {
        return service.search(userId, createdByUserId, status, pageable);
    }
}
