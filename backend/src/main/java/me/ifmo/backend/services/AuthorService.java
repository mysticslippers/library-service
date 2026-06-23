package me.ifmo.backend.services;

import me.ifmo.backend.dto.catalog.request.CreateAuthorRequest;
import me.ifmo.backend.dto.catalog.response.AuthorResponse;

public interface AuthorService {

    AuthorResponse create(CreateAuthorRequest request);
}
