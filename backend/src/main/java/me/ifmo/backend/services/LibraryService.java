package me.ifmo.backend.services;

import me.ifmo.backend.dto.common.response.PageResponse;
import me.ifmo.backend.dto.library.request.ChangeLibraryStatusRequest;
import me.ifmo.backend.dto.library.request.CreateLibraryRequest;
import me.ifmo.backend.dto.library.request.UpdateLibraryRequest;
import me.ifmo.backend.dto.library.response.LibraryResponse;
import me.ifmo.backend.entities.enums.LibraryStatus;
import org.springframework.data.domain.Pageable;

public interface LibraryService {

    LibraryResponse create(CreateLibraryRequest request);

    LibraryResponse getLibraryById(Long id);

    LibraryResponse getByCode(String code);

    LibraryResponse update(Long id, UpdateLibraryRequest request);

    LibraryResponse changeStatus(Long id, ChangeLibraryStatusRequest request);

    PageResponse<LibraryResponse> search(String query, LibraryStatus status, Pageable pageable);
}
