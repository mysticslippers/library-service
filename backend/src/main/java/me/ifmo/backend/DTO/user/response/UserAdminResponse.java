package me.ifmo.backend.DTO.user.response;

import me.ifmo.backend.entities.enums.UserStatus;

import java.time.LocalDateTime;
import java.util.List;

public record UserAdminResponse(
        Long id,
        String email,
        String phone,
        String firstName,
        String lastName,
        String middleName,
        UserStatus status,
        Long homeBranchId,
        String homeBranchName,
        LocalDateTime registeredAt,
        LocalDateTime activatedAt,
        LocalDateTime lastLoginAt,
        LocalDateTime lockedUntil,
        List<RoleResponse> roles
) {
}
