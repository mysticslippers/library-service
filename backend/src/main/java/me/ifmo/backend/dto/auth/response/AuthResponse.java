package me.ifmo.backend.dto.auth.response;

import me.ifmo.backend.dto.user.response.UserProfileResponse;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserProfileResponse user
) {
}
