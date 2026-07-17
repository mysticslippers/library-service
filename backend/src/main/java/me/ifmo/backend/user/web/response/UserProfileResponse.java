package me.ifmo.backend.user.web.response;

import me.ifmo.backend.user.domain.enums.RoleCode;
import me.ifmo.backend.user.domain.enums.UserStatus;

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
