package me.ifmo.backend.user.web.response;

import me.ifmo.backend.user.domain.enums.UserStatus;

public record UserShortResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        String middleName,
        UserStatus status
) {
}
