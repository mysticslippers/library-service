package me.ifmo.backend.services;

import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.dto.user.request.*;
import me.ifmo.backend.dto.user.response.UserAdminResponse;
import me.ifmo.backend.dto.user.response.UserProfileResponse;
import org.springframework.data.domain.Pageable;

public interface UserService {

    UserAdminResponse create(Long actorUserId, CreateUserRequest request);

    UserAdminResponse getUserById(Long id);

    UserProfileResponse getProfile(Long id);

    UserAdminResponse update(Long actorUserId, Long id, UpdateUserRequest request);

    UserAdminResponse changeStatus(Long actorUserId, Long id, ChangeUserStatusRequest request);

    UserAdminResponse assignRole(Long actorUserId, Long id, AssignUserRoleRequest request);

    UserAdminResponse revokeRole(Long actorUserId, Long id, AssignUserRoleRequest request);

    PageResponse<UserAdminResponse> search(UserSearchRequest request, Pageable pageable);
}
