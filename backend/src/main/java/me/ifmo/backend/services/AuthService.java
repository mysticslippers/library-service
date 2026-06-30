package me.ifmo.backend.services;

import me.ifmo.backend.dto.auth.request.LoginRequest;
import me.ifmo.backend.dto.auth.request.RegisterRequest;
import me.ifmo.backend.dto.auth.response.AuthResponse;
import me.ifmo.backend.dto.user.response.UserProfileResponse;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    UserProfileResponse me(Long userId);
}
