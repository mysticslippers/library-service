package me.ifmo.backend.dto.catalog.response;

import me.ifmo.backend.entities.enums.AuthorStatus;

public record AuthorResponse(
        Long id,
        String firstName,
        String lastName,
        String middleName,
        AuthorStatus status
) {
}
