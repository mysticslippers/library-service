package me.ifmo.backend.services;

import me.ifmo.backend.dto.user.request.AssignUserRoleRequest;
import me.ifmo.backend.dto.user.request.ChangeUserStatusRequest;
import me.ifmo.backend.dto.user.request.CreateUserRequest;
import me.ifmo.backend.dto.user.request.UpdateUserRequest;
import me.ifmo.backend.dto.user.response.UserAdminResponse;
import me.ifmo.backend.dto.user.response.UserProfileResponse;

public interface UserService {

    UserAdminResponse create(CreateUserRequest request);

    UserAdminResponse getUserById(Long id);

    UserProfileResponse getProfile(Long id);

    UserAdminResponse update(Long id, UpdateUserRequest request);

    UserAdminResponse changeStatus(Long id, ChangeUserStatusRequest request);

    UserAdminResponse assignRole(Long id, AssignUserRoleRequest request);

    UserAdminResponse revokeRole(Long id, AssignUserRoleRequest request);
}
