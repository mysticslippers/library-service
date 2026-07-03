package me.ifmo.backend.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.common.response.PageResponse;
import me.ifmo.backend.dto.user.request.*;
import me.ifmo.backend.dto.user.response.UserAdminResponse;
import me.ifmo.backend.dto.user.response.UserProfileResponse;
import me.ifmo.backend.entities.enums.RoleCode;
import me.ifmo.backend.services.UserService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
public class UserController {

    private final UserService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserAdminResponse create(@AuthenticationPrincipal UserDetails userDetails, @Valid @RequestBody CreateUserRequest request) {
        return service.create(Long.valueOf(userDetails.getUsername()), request);
    }

    @GetMapping("/{id}")
    public UserAdminResponse getUserById(@PathVariable Long id) {
        return service.getUserById(id);
    }

    @GetMapping("/{id}/profile")
    public UserProfileResponse getProfile(@PathVariable Long id) {
        return service.getProfile(id);
    }

    @PatchMapping("/{id}")
    public UserAdminResponse update(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return service.update(Long.valueOf(userDetails.getUsername()), id, request);
    }

    @PatchMapping("/{id}/status")
    public UserAdminResponse changeStatus(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id, @Valid @RequestBody ChangeUserStatusRequest request) {
        return service.changeStatus(Long.valueOf(userDetails.getUsername()), id, request);
    }

    @PostMapping("/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public UserAdminResponse assignRole(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id, @Valid @RequestBody AssignUserRoleRequest request) {
        return service.assignRole(Long.valueOf(userDetails.getUsername()), id, request);
    }

    @DeleteMapping("/{id}/roles/{roleCode}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserAdminResponse revokeRole(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id, @PathVariable RoleCode roleCode) {
        return service.revokeRole(Long.valueOf(userDetails.getUsername()), id, new AssignUserRoleRequest(roleCode));
    }

    @GetMapping
    public PageResponse<UserAdminResponse> search(@Valid @ModelAttribute UserSearchRequest request, Pageable pageable) {
        return service.search(request, pageable);
    }
}
