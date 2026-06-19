package me.ifmo.backend.dto.user.request;

import jakarta.validation.constraints.NotNull;
import me.ifmo.backend.entities.enums.RoleCode;

public record AssignUserRoleRequest(
        @NotNull
        RoleCode roleCode
) {
}
