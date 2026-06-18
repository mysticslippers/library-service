package me.ifmo.backend.DTO.user.response;

import me.ifmo.backend.entities.enums.RoleCode;

public record RoleResponse(
        Long id,
        RoleCode code,
        String name,
        String description
) {
}
