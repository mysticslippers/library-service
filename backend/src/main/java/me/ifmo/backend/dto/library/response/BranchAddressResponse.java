package me.ifmo.backend.dto.library.response;

public record BranchAddressResponse(
        String city,
        String street,
        String building
) {
}
