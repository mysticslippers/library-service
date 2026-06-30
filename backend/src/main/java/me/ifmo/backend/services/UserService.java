package me.ifmo.backend.services;

import me.ifmo.backend.dto.common.response.PageResponse;
import me.ifmo.backend.dto.user.request.*;
import me.ifmo.backend.dto.user.response.UserAdminResponse;
import me.ifmo.backend.dto.user.response.UserProfileResponse;
import org.springframework.data.domain.Pageable;

public interface UserService {

    UserAdminResponse create(CreateUserRequest request);

    UserAdminResponse getUserById(Long id);

    UserProfileResponse getProfile(Long id);

    UserAdminResponse update(Long id, UpdateUserRequest request);

    UserAdminResponse changeStatus(Long id, ChangeUserStatusRequest request);

    UserAdminResponse assignRole(Long id, AssignUserRoleRequest request);

    UserAdminResponse revokeRole(Long id, AssignUserRoleRequest request);

    PageResponse<UserAdminResponse> search(UserSearchRequest request, Pageable pageable);
}
