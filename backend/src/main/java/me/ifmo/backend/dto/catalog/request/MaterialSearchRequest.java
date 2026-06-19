package me.ifmo.backend.dto.catalog.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import me.ifmo.backend.entities.enums.MaterialStatus;
import me.ifmo.backend.entities.enums.MaterialType;

public record MaterialSearchRequest(
        @Size(max = 255)
        String query,

        MaterialType materialType,
        MaterialStatus status,

        @Min(1000)
        @Max(3000)
        Integer publicationYear,

        Long authorId,
        Long genreId,
        Long branchId
) {
}
