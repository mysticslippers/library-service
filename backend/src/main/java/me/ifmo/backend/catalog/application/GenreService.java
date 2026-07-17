package me.ifmo.backend.catalog.application;

import me.ifmo.backend.catalog.web.request.CreateGenreRequest;
import me.ifmo.backend.catalog.web.request.UpdateGenreRequest;
import me.ifmo.backend.catalog.web.response.GenreResponse;
import me.ifmo.backend.shared.web.response.PageResponse;
import org.springframework.data.domain.Pageable;

public interface GenreService {

    GenreResponse create(CreateGenreRequest request);

    GenreResponse getGenreById(Long id);

    GenreResponse getGenreByCode(String code);

    GenreResponse update(Long id, UpdateGenreRequest request);

    PageResponse<GenreResponse> search(String query, Pageable pageable);

    void delete(Long id);
}
