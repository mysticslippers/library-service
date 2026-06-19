package me.ifmo.backend.dto.user.response;

import me.ifmo.backend.entities.enums.UserStatus;

public record UserProfileResponse(
        Long id,
        String email,
        String phone,
        String firstName,
        String lastName,
        String middleName,
        UserStatus status,
        Long homeBranchId,
        String homeBranchName
) {
}
