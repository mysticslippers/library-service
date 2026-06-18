package me.ifmo.backend.DTO.user.request;

import jakarta.validation.constraints.NotNull;
import me.ifmo.backend.entities.enums.RoleCode;

public record AssignUserRoleRequest(
        @NotNull
        RoleCode roleCode
) {
}
