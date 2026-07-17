package me.ifmo.backend.user.web.request;

import jakarta.validation.constraints.NotNull;
import me.ifmo.backend.user.domain.enums.RoleCode;

public record AssignUserRoleRequest(
        @NotNull
        RoleCode roleCode
) {
}
