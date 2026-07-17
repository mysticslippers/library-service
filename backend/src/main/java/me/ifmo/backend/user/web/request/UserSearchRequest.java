package me.ifmo.backend.user.web.request;

import me.ifmo.backend.user.domain.enums.UserStatus;

public record UserSearchRequest(
        String query,
        UserStatus status,
        Long homeBranchId
) {
}
