package me.ifmo.backend.catalog.application;

import me.ifmo.backend.catalog.web.request.AuthorSearchRequest;
import me.ifmo.backend.catalog.web.request.CreateAuthorRequest;
import me.ifmo.backend.catalog.web.request.UpdateAuthorRequest;
import me.ifmo.backend.catalog.web.response.AuthorResponse;
import me.ifmo.backend.shared.web.response.PageResponse;
import org.springframework.data.domain.Pageable;

public interface AuthorService {

    AuthorResponse create(CreateAuthorRequest request);

    AuthorResponse getAuthorById(Long id);

    AuthorResponse update(Long id, UpdateAuthorRequest request);

    PageResponse<AuthorResponse> search(AuthorSearchRequest request, Pageable pageable);

    void delete(Long id);
}
