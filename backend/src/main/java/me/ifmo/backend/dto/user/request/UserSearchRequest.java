package me.ifmo.backend.dto.user.request;

import me.ifmo.backend.entities.enums.UserStatus;

public record UserSearchRequest(
        String query,
        UserStatus status,
        Long homeBranchId
) {
}
