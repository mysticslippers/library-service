package me.ifmo.backend.catalog.application.cover;

public record ValidatedCover(
        byte[] content,
        String contentType,
        String extension
) {
}
