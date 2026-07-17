package me.ifmo.backend.catalog.web.request;

import jakarta.validation.constraints.Size;

public record UpdateAuthorRequest(
        @Size(max = 100)
        String firstName,

        @Size(max = 100)
        String lastName,

        @Size(max = 100)
        String middleName
) {
}
