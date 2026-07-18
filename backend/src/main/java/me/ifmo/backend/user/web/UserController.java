package me.ifmo.backend.user.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.user.web.request.*;
import me.ifmo.backend.user.web.response.UserAdminResponse;
import me.ifmo.backend.user.web.response.UserProfileResponse;
import me.ifmo.backend.user.domain.enums.RoleCode;
import me.ifmo.backend.user.application.UserService;
import org.springdoc.core.annotations.ParameterObject;
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
@Tag(name = "Users", description = "Administrative user management, statuses, and role assignments")
public class UserController {

    private final UserService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a user")
    public UserAdminResponse create(@AuthenticationPrincipal UserDetails userDetails, @Valid @RequestBody CreateUserRequest request) {
        return service.create(Long.valueOf(userDetails.getUsername()), request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a user by ID")
    public UserAdminResponse getUserById(@PathVariable Long id) {
        return service.getUserById(id);
    }

    @GetMapping("/{id}/profile")
    @Operation(summary = "Get a user's public profile")
    public UserProfileResponse getProfile(@PathVariable Long id) {
        return service.getProfile(id);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a user")
    public UserAdminResponse update(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return service.update(Long.valueOf(userDetails.getUsername()), id, request);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Change a user's status")
    public UserAdminResponse changeStatus(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id, @Valid @RequestBody ChangeUserStatusRequest request) {
        return service.changeStatus(Long.valueOf(userDetails.getUsername()), id, request);
    }

    @PostMapping("/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Assign a role to a user")
    public UserAdminResponse assignRole(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id, @Valid @RequestBody AssignUserRoleRequest request) {
        return service.assignRole(Long.valueOf(userDetails.getUsername()), id, request);
    }

    @DeleteMapping("/{id}/roles/{roleCode}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Revoke a role from a user")
    public UserAdminResponse revokeRole(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id, @PathVariable RoleCode roleCode) {
        return service.revokeRole(Long.valueOf(userDetails.getUsername()), id, new AssignUserRoleRequest(roleCode));
    }

    @GetMapping
    @Operation(summary = "Search users")
    public PageResponse<UserAdminResponse> search(@Valid @ModelAttribute UserSearchRequest request, @ParameterObject Pageable pageable) {
        return service.search(request, pageable);
    }
}
