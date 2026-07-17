package me.ifmo.backend.authentication.web.response;

import me.ifmo.backend.user.web.response.UserProfileResponse;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserProfileResponse user
) {
}
