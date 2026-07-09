package me.ifmo.backend.dto.catalog.response;

import me.ifmo.backend.entities.enums.MaterialStatus;
import me.ifmo.backend.entities.enums.MaterialType;

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
