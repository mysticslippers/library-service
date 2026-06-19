package me.ifmo.backend.dto.catalog.response;

public record AuthorResponse(
        Long id,
        String firstName,
        String lastName,
        String middleName
) {
}
