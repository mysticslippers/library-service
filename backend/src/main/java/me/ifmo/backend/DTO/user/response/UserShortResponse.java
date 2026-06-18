package me.ifmo.backend.DTO.user.response;

import me.ifmo.backend.entities.enums.UserStatus;

public record UserShortResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        String middleName,
        UserStatus status
) {
}
