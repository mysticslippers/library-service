package me.ifmo.backend.shared.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

@Schema(description = "Generic paginated API response")
public record PageResponse<T>(
        @Schema(description = "Items in the current page")
        List<T> content,
        @Schema(description = "Zero-based page index", example = "0")
        int page,
        @Schema(description = "Requested page size", example = "20")
        int size,
        @Schema(description = "Total number of matching items", example = "42")
        long totalElements,
        @Schema(description = "Total number of pages", example = "3")
        int totalPages,
        @Schema(description = "Whether this is the first page")
        boolean first,
        @Schema(description = "Whether this is the last page")
        boolean last,
        @Schema(description = "Whether the page has no content")
        boolean empty
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                page.isEmpty()
        );
    }
}
