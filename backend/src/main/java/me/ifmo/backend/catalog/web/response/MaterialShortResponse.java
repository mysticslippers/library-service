package me.ifmo.backend.catalog.web.response;

import me.ifmo.backend.catalog.domain.enums.MaterialStatus;
import me.ifmo.backend.catalog.domain.enums.MaterialType;

import java.util.List;

public record MaterialShortResponse(
        Long id,
        String isbn,
        String title,
        String publisher,
        Integer publicationYear,
        MaterialType materialType,
        String language,
        MaterialStatus status,
        List<AuthorResponse> authors,
        List<GenreResponse> genres
) {
}
