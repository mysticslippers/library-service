package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.catalog.request.CreateGenreRequest;
import me.ifmo.backend.dto.catalog.request.UpdateGenreRequest;
import me.ifmo.backend.dto.catalog.response.GenreResponse;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.entities.Genre;
import me.ifmo.backend.entities.enums.GenreStatus;
import me.ifmo.backend.shared.error.BusinessRuleException;
import me.ifmo.backend.shared.error.DuplicateResourceException;
import me.ifmo.backend.shared.error.ResourceNotFoundException;
import me.ifmo.backend.mappers.GenreMapper;
import me.ifmo.backend.repositories.GenreRepository;
import me.ifmo.backend.services.GenreService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class GenreServiceImpl implements GenreService {

    private final GenreRepository repository;
    private final GenreMapper genreMapper;

    private String normalize(String value, String fieldName) {
        if (value == null || value.strip().isBlank())
            throw new BusinessRuleException("%s must not be blank".formatted(fieldName));

        return value.strip();
    }

    @Override
    @Transactional
    public GenreResponse create(CreateGenreRequest request) {
        String normalizedName = normalize(request.name(), "Genre name");
        String normalizedCode = normalize(request.code(), "Genre code").toUpperCase(Locale.ROOT);

        if(repository.existsByCode(normalizedCode))
            throw new DuplicateResourceException("Genre with code '%s' already exists".formatted(normalizedCode));

        if(repository.existsByNameIgnoreCase(normalizedName))
            throw new DuplicateResourceException("Genre with name '%s' already exists".formatted(normalizedName));

        Genre genre = genreMapper.toEntity(request);
        genre.setName(normalizedName);
        genre.setCode(normalizedCode);

        Genre saved = repository.save(genre);
        return genreMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public GenreResponse getGenreById(Long id){
        Genre genre = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No genre with id '%s' found".formatted(id)));

        return genreMapper.toResponse(genre);
    }

    @Override
    @Transactional(readOnly = true)
    public GenreResponse getGenreByCode(String code){
        String normalizedCode = normalize(code, "Genre code").toUpperCase(Locale.ROOT);

        Genre genre = repository.findByCode(normalizedCode)
                .orElseThrow(() -> new ResourceNotFoundException("Genre with code '%s' not found".formatted(code)));

        return genreMapper.toResponse(genre);
    }

    @Override
    @Transactional
    public GenreResponse update(Long id, UpdateGenreRequest request){
        Genre genre = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No genre with id '%s' found".formatted(id)));

        if (genre.getStatus() == GenreStatus.ARCHIVED)
            throw new BusinessRuleException("Archived genre cannot be updated");

        String normalizedName = (request.name() != null) ? normalize(request.name(), "Genre name") : null;
        String normalizedCode = (request.code() != null) ? normalize(request.code(), "Genre code").toUpperCase(Locale.ROOT) : null;

        if(normalizedName != null && !normalizedName.equalsIgnoreCase(genre.getName())
                && repository.existsByNameIgnoreCase(normalizedName))
            throw new DuplicateResourceException("Genre with name '%s' already exists".formatted(normalizedName));

        if(normalizedCode != null && !normalizedCode.equalsIgnoreCase(genre.getCode())
                && repository.existsByCode(normalizedCode))
            throw new DuplicateResourceException("Genre with code '%s' already exists".formatted(normalizedCode));

        UpdateGenreRequest normalizedRequest = new UpdateGenreRequest(normalizedCode, normalizedName);

        genreMapper.updateEntity(normalizedRequest, genre);

        Genre saved = repository.save(genre);
        return genreMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(Long id){
        Genre existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No genre with id '%s' found".formatted(id)));

        existing.setStatus(GenreStatus.ARCHIVED);

        repository.save(existing);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<GenreResponse> search(String query, Pageable pageable){
        String normalizedQuery = (query != null) ? query.strip() : "";

        Page<Genre> genres = repository.search(normalizedQuery, GenreStatus.ACTIVE, pageable);

        Page<GenreResponse> responses = genres.map(genreMapper::toResponse);

        return PageResponse.from(responses);
    }
}
