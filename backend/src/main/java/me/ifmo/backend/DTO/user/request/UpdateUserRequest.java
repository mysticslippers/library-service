package me.ifmo.backend.DTO.user.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Email
        @Size(max = 255)
        String email,

        @Pattern(regexp = "^(\\+7|8)[0-9]{10}$")
        String phone,

        @Size(max = 100)
        String firstName,

        @Size(max = 100)
        String lastName,

        @Size(max = 100)
        String middleName,

        Long homeBranchId
) {
}
