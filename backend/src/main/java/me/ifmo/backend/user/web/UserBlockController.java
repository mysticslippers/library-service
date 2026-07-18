package me.ifmo.backend.user.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.user.web.request.CancelUserBlockRequest;
import me.ifmo.backend.user.web.request.CreateUserBlockRequest;
import me.ifmo.backend.user.web.response.UserBlockResponse;
import me.ifmo.backend.user.domain.enums.UserBlockStatus;
import me.ifmo.backend.user.application.UserBlockService;
import org.springdoc.core.annotations.ParameterObject;
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
@Tag(name = "User Blocks", description = "Temporary or permanent restrictions applied to users")
public class UserBlockController {

    private final UserBlockService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a user block")
    public UserBlockResponse create(@AuthenticationPrincipal UserDetails userDetails, @Valid @RequestBody CreateUserBlockRequest request) {
        return service.create(Long.valueOf(userDetails.getUsername()), request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a user block by ID")
    public UserBlockResponse getUserBlockById(@PathVariable Long id) {
        return service.getUserBlockById(id);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a user block")
    public UserBlockResponse cancel(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id, @Valid @RequestBody CancelUserBlockRequest request) {
        return service.cancel(id, Long.valueOf(userDetails.getUsername()), request);
    }

    @PostMapping("/{id}/expire")
    @Operation(summary = "Mark a user block as expired")
    public UserBlockResponse expire(@PathVariable Long id) {
        return service.expire(id);
    }

    @GetMapping
    @Operation(summary = "Search user blocks")
    public PageResponse<UserBlockResponse> search(@RequestParam(required = false) Long userId, @RequestParam(required = false) Long createdByUserId, @RequestParam(required = false) UserBlockStatus status, @ParameterObject Pageable pageable) {
        return service.search(userId, createdByUserId, status, pageable);
    }
}
