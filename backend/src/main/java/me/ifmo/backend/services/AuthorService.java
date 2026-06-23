package me.ifmo.backend.services;

import me.ifmo.backend.dto.catalog.request.AuthorSearchRequest;
import me.ifmo.backend.dto.catalog.request.CreateAuthorRequest;
import me.ifmo.backend.dto.catalog.request.UpdateAuthorRequest;
import me.ifmo.backend.dto.catalog.response.AuthorResponse;
import me.ifmo.backend.dto.common.response.PageResponse;
import org.springframework.data.domain.Pageable;

public interface AuthorService {

    AuthorResponse create(CreateAuthorRequest request);

    AuthorResponse getAuthorById(Long id);

    AuthorResponse update(Long id, UpdateAuthorRequest request);

    PageResponse<AuthorResponse> search(AuthorSearchRequest request, Pageable pageable);

    void delete(Long id);
}
