package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.catalog.request.CreateGenreRequest;
import me.ifmo.backend.dto.catalog.request.UpdateGenreRequest;
import me.ifmo.backend.dto.catalog.response.GenreResponse;
import me.ifmo.backend.entities.Genre;
import me.ifmo.backend.exceptions.domain.DuplicateResourceException;
import me.ifmo.backend.exceptions.domain.ResourceNotFoundException;
import me.ifmo.backend.mappers.GenreMapper;
import me.ifmo.backend.repositories.GenreRepository;
import me.ifmo.backend.services.GenreService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class GenreServiceImpl implements GenreService {

    private final GenreRepository repository;
    private final GenreMapper mapper;

    @Override
    @Transactional
    public GenreResponse create(CreateGenreRequest request) {
        String normalizedName = request.name().strip();
        String normalizedCode = request.code().strip().toUpperCase(Locale.ROOT);

        if(repository.existsByCode(normalizedCode))
            throw new DuplicateResourceException("Genre with code '%s' already exists".formatted(normalizedCode));

        if(repository.existsByNameIgnoreCase(normalizedName))
            throw new DuplicateResourceException("Genre with name '%s' already exists".formatted(normalizedName));

        Genre genre = mapper.toEntity(request);
        genre.setName(normalizedName);
        genre.setCode(normalizedCode);

        Genre saved = repository.save(genre);
        return mapper.toResponse(saved);
    }

    @Override
    public GenreResponse getGenreById(Long id){
        Genre genre = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No genre with id '%s' found".formatted(id)));

        return mapper.toResponse(genre);
    }

    @Override
    public GenreResponse getGenreByCode(String code){
        Genre genre = repository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Genre with code '%s' not found".formatted(code)));

        return mapper.toResponse(genre);
    }



    @Override
    @Transactional
    public void delete(Long id){
        Genre existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No genre with id '%s' found".formatted(id)));

        repository.delete(existing);
    }
}
