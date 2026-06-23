package me.ifmo.backend.services;

import me.ifmo.backend.dto.catalog.request.CreateGenreRequest;
import me.ifmo.backend.dto.catalog.response.GenreResponse;

public interface GenreService {

    GenreResponse create(CreateGenreRequest request);
}
