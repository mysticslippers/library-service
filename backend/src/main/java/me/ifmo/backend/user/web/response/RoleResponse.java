package me.ifmo.backend.user.web.response;

import me.ifmo.backend.user.domain.enums.RoleCode;

public record RoleResponse(
        Long id,
        RoleCode code,
        String name,
        String description
) {
}
