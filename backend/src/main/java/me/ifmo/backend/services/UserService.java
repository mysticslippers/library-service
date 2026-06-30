package me.ifmo.backend.services;

import me.ifmo.backend.dto.user.request.CreateUserRequest;
import me.ifmo.backend.dto.user.response.UserAdminResponse;
import me.ifmo.backend.dto.user.response.UserProfileResponse;

public interface UserService {

    UserAdminResponse create(CreateUserRequest request);

    UserAdminResponse getUserById(Long id);

    UserProfileResponse getProfile(Long id);
}
