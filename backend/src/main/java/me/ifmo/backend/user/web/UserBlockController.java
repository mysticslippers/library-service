package me.ifmo.backend.user.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.user.web.request.CancelUserBlockRequest;
import me.ifmo.backend.user.web.request.CreateUserBlockRequest;
import me.ifmo.backend.user.web.response.UserBlockResponse;
import me.ifmo.backend.user.domain.enums.UserBlockStatus;
import me.ifmo.backend.user.application.UserBlockService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user-blocks")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
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
    public UserBlockResponse cancel(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id, @Valid @RequestBody CancelUserBlockRequest request) {
        return service.cancel(id, Long.valueOf(userDetails.getUsername()), request);
    }

    @PostMapping("/{id}/expire")
    public UserBlockResponse expire(@PathVariable Long id) {
        return service.expire(id);
    }

    @GetMapping
    public PageResponse<UserBlockResponse> search(@RequestParam(required = false) Long userId, @RequestParam(required = false) Long createdByUserId, @RequestParam(required = false) UserBlockStatus status, Pageable pageable) {
        return service.search(userId, createdByUserId, status, pageable);
    }
}
