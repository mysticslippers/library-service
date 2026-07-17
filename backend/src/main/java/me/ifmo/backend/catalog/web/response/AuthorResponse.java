package me.ifmo.backend.catalog.web.response;

import me.ifmo.backend.catalog.domain.enums.AuthorStatus;

public record AuthorResponse(
        Long id,
        String firstName,
        String lastName,
        String middleName,
        AuthorStatus status
) {
}
