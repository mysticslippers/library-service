package me.ifmo.backend.services;

import me.ifmo.backend.dto.library.request.CreateLibraryRequest;
import me.ifmo.backend.dto.library.response.LibraryResponse;

public interface LibraryService {

    LibraryResponse create(CreateLibraryRequest request);

    LibraryResponse getById(Long id);

    LibraryResponse getByCode(String code);
}
