package me.ifmo.backend.catalog.web.response;

import me.ifmo.backend.catalog.domain.enums.MaterialStatus;
import me.ifmo.backend.catalog.domain.enums.MaterialType;

import java.util.List;

public record MaterialResponse(
        Long id,
        String isbn,
        String title,
        String description,
        String publisher,
        Integer publicationYear,
        MaterialType materialType,
        String language,
        MaterialStatus status,
        List<MaterialAuthorResponse> authors,
        List<GenreResponse> genres,
        long totalCopies,
        long availableCopies,
        List<MaterialCopyResponse> copies
) {
}
