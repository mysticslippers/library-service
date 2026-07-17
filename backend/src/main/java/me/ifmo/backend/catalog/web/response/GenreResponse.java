package me.ifmo.backend.catalog.web.response;

import me.ifmo.backend.catalog.domain.enums.GenreStatus;

public record GenreResponse(
        Long id,
        String code,
        String name,
        GenreStatus status
) {
}
