package me.ifmo.backend.library.internal.application;

import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.library.internal.web.request.ChangeLibraryStatusRequest;
import me.ifmo.backend.library.internal.web.request.CreateLibraryRequest;
import me.ifmo.backend.library.internal.web.request.UpdateLibraryRequest;
import me.ifmo.backend.library.internal.web.response.LibraryResponse;
import me.ifmo.backend.library.internal.domain.enums.LibraryStatus;
import org.springframework.data.domain.Pageable;

public interface LibraryService {

    LibraryResponse create(CreateLibraryRequest request);

    LibraryResponse getLibraryById(Long id);

    LibraryResponse getByCode(String code);

    LibraryResponse update(Long id, UpdateLibraryRequest request);

    LibraryResponse changeStatus(Long id, ChangeLibraryStatusRequest request);

    PageResponse<LibraryResponse> search(String query, LibraryStatus status, Pageable pageable);
}
