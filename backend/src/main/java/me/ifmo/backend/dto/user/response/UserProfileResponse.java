package me.ifmo.backend.dto.user.response;

import me.ifmo.backend.entities.enums.RoleCode;
import me.ifmo.backend.entities.enums.UserStatus;

import java.util.Set;

public record UserProfileResponse(
        Long id,
        String email,
        String phone,
        String firstName,
        String lastName,
        String middleName,
        UserStatus status,
        Long homeBranchId,
        String homeBranchName,
        Set<RoleCode> roles
) {
}
