package me.ifmo.backend.dto.catalog.response;

import me.ifmo.backend.entities.enums.GenreStatus;

public record GenreResponse(
        Long id,
        String code,
        String name,
        GenreStatus status
) {
}
