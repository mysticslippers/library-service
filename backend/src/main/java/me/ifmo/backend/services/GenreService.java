package me.ifmo.backend.services;

import me.ifmo.backend.dto.catalog.request.CreateGenreRequest;
import me.ifmo.backend.dto.catalog.response.GenreResponse;
import me.ifmo.backend.dto.common.response.PageResponse;
import org.springframework.data.domain.Pageable;

public interface GenreService {

    GenreResponse create(CreateGenreRequest request);

    GenreResponse getGenreById(Long id);

    GenreResponse getGenreByCode(String code);

    PageResponse<GenreResponse> search(String query, Pageable pageable);
}
