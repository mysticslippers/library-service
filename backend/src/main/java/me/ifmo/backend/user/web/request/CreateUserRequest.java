package me.ifmo.backend.user.web.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import me.ifmo.backend.user.domain.enums.RoleCode;

import java.util.Set;

public record CreateUserRequest(
        @Email
        @NotBlank
        @Size(max = 255)
        String email,

        @NotBlank
        @Pattern(regexp = "^(\\+7|8)[0-9]{10}$")
        String phone,

        @NotBlank
        @Size(min = 8, max = 255)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)\\S{8,255}$")
        String password,

        @NotBlank
        @Size(max = 100)
        String firstName,

        @NotBlank
        @Size(max = 100)
        String lastName,

        @Size(max = 100)
        String middleName,

        Long homeBranchId,
        Set<RoleCode> roles
) {
}
