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
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserAdminResponse create(@Valid @RequestBody CreateUserRequest request) {
        return service.create(request);
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
    public UserAdminResponse update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public UserAdminResponse changeStatus(@PathVariable Long id, @Valid @RequestBody ChangeUserStatusRequest request) {
        return service.changeStatus(id, request);
    }

    @PostMapping("/{id}/roles")
    public UserAdminResponse assignRole(@PathVariable Long id, @Valid @RequestBody AssignUserRoleRequest request) {
        return service.assignRole(id, request);
    }

    @DeleteMapping("/{id}/roles/{roleCode}")
    public UserAdminResponse revokeRole(@PathVariable Long id, @PathVariable RoleCode roleCode) {
        return service.revokeRole(id, new AssignUserRoleRequest(roleCode));
    }

    @GetMapping
    public PageResponse<UserAdminResponse> search(@Valid @ModelAttribute UserSearchRequest request, Pageable pageable) {
        return service.search(request, pageable);
    }
}