package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.catalog.request.AuthorSearchRequest;
import me.ifmo.backend.dto.catalog.request.CreateAuthorRequest;
import me.ifmo.backend.dto.catalog.request.UpdateAuthorRequest;
import me.ifmo.backend.dto.catalog.response.AuthorResponse;
import me.ifmo.backend.dto.common.response.PageResponse;
import me.ifmo.backend.entities.Author;
import me.ifmo.backend.entities.enums.AuthorStatus;
import me.ifmo.backend.exceptions.domain.BusinessRuleException;
import me.ifmo.backend.exceptions.domain.DuplicateResourceException;
import me.ifmo.backend.exceptions.domain.ResourceNotFoundException;
import me.ifmo.backend.mappers.AuthorMapper;
import me.ifmo.backend.repositories.AuthorRepository;
import me.ifmo.backend.services.AuthorService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthorServiceImpl implements AuthorService {

    private final AuthorRepository repository;
    private final AuthorMapper authorMapper;

    private String normalize(String value, String fieldName) {
        if (value == null) {
            if (fieldName.equals("Middle name"))
                return null;

            throw new BusinessRuleException("%s must not be blank".formatted(fieldName));
        }

        String normalized = value.strip();
        if (normalized.isBlank()) {
            if (fieldName.equals("Middle name"))
                return null;

            throw new BusinessRuleException("%s must not be blank".formatted(fieldName));
        }

        return normalized;
    }

    @Override
    @Transactional
    public AuthorResponse create(CreateAuthorRequest request) {
        String firstName = normalize(request.firstName(), "First name");
        String lastName = normalize(request.lastName(), "Last name");
        String middleName = normalize(request.middleName(), "Middle name");

        if (repository.existsByFullName(firstName, middleName, lastName))
            throw new DuplicateResourceException("Author with the same full name already exists");

        CreateAuthorRequest normalizedRequest = new CreateAuthorRequest(firstName, lastName, middleName);
        Author author = authorMapper.toEntity(normalizedRequest);

        Author saved = repository.save(author);
        return authorMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthorResponse getAuthorById(Long id) {
        Author author = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Author with id '%s' not found".formatted(id)));

        return authorMapper.toResponse(author);
    }

    @Override
    @Transactional
    public AuthorResponse update(Long id, UpdateAuthorRequest request) {
        Author author = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Author with id '%s' not found".formatted(id)));

        if (author.getStatus() == AuthorStatus.ARCHIVED)
            throw new BusinessRuleException("Archived author cannot be updated");

        String firstName = (request.firstName() != null) ? normalize(request.firstName(), "First name")
                : author.getFirstName();

        String lastName = (request.lastName() != null) ? normalize(request.lastName(), "Last name")
                : author.getLastName();

        String middleName = (request.middleName() != null) ? normalize(request.middleName(), "Middle name")
                : author.getMiddleName();

        repository.findByFullName(firstName, middleName, lastName)
                .filter(found -> !found.getId().equals(id))
                .ifPresent(found -> {
                    throw new DuplicateResourceException("Author with the same full name already exists");
                });

        UpdateAuthorRequest normalizedRequest = new UpdateAuthorRequest(
                request.firstName() != null ? firstName : null,
                request.lastName() != null ? lastName : null,
                request.middleName() != null ? middleName : null
        );

        authorMapper.updateEntity(normalizedRequest, author);

        if(request.middleName() != null && middleName == null)
            author.setMiddleName(null);

        Author saved = repository.save(author);
        return authorMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Author existing = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Author with id '%s' not found".formatted(id)));

        existing.setStatus(AuthorStatus.ARCHIVED);

        repository.save(existing);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuthorResponse> search(AuthorSearchRequest request, Pageable pageable) {
        String query = (request == null || request.query() == null) ? "" : request.query().strip();

        Page<AuthorResponse> responses = repository.search(query, AuthorStatus.ACTIVE, pageable).map(authorMapper::toResponse);

        return PageResponse.from(responses);
    }
}
