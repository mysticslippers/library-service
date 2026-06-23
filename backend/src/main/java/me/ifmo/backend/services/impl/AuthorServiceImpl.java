package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.catalog.request.CreateAuthorRequest;
import me.ifmo.backend.dto.catalog.response.AuthorResponse;
import me.ifmo.backend.entities.Author;
import me.ifmo.backend.exceptions.domain.BusinessRuleException;
import me.ifmo.backend.exceptions.domain.DuplicateResourceException;
import me.ifmo.backend.mappers.AuthorMapper;
import me.ifmo.backend.repositories.AuthorRepository;
import me.ifmo.backend.repositories.MaterialAuthorRepository;
import me.ifmo.backend.services.AuthorService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthorServiceImpl implements AuthorService {

    private final MaterialAuthorRepository materialAuthorRepository;
    private final AuthorRepository repository;
    private final AuthorMapper mapper;

    private String normalize(String value, String fieldName) {
        String normalized = value.strip();

        if (normalized.isBlank())
            throw new BusinessRuleException("%s must not be blank".formatted(fieldName));

        return normalized;
    }

    private String normalizeMiddleName(String value) {
        if(value == null)
            return null;

        String normalized = value.strip();
        return normalized.isBlank() ? null : normalized;
    }

    @Override
    @Transactional
    public AuthorResponse create(CreateAuthorRequest request) {
        String firstName = normalize(request.firstName(), "First name");
        String lastName = normalize(request.lastName(), "Last name");
        String middleName = normalizeMiddleName(request.middleName());

        if (repository.existsByFullName(firstName, middleName, lastName))
            throw new DuplicateResourceException("Author with the same full name already exists");

        CreateAuthorRequest normalizedRequest = new CreateAuthorRequest(firstName, lastName, middleName);
        Author author = mapper.toEntity(normalizedRequest);

        Author saved = repository.save(author);
        return mapper.toResponse(saved);
    }

}
