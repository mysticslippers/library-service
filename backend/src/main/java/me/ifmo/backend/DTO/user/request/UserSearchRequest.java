package me.ifmo.backend.DTO.user.request;

import me.ifmo.backend.entities.enums.UserStatus;

public record UserSearchRequest(
        String query,
        UserStatus status,
        Long homeBranchId
) {
}
