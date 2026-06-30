package me.ifmo.backend.services;

import me.ifmo.backend.dto.auth.request.RegisterRequest;
import me.ifmo.backend.dto.auth.response.AuthResponse;

public interface AuthService {

    AuthResponse register(RegisterRequest request);
}
