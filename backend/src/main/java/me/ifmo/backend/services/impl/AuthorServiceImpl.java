package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.catalog.request.CreateAuthorRequest;
import me.ifmo.backend.dto.catalog.request.UpdateAuthorRequest;
import me.ifmo.backend.dto.catalog.response.AuthorResponse;
import me.ifmo.backend.entities.Author;
import me.ifmo.backend.exceptions.domain.BusinessRuleException;
import me.ifmo.backend.exceptions.domain.DuplicateResourceException;
import me.ifmo.backend.exceptions.domain.ResourceInUseException;
import me.ifmo.backend.exceptions.domain.ResourceNotFoundException;
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
        if (fieldName.equals("Middle name")){
            if(value == null)
                return null;

            String normalized = value.strip();
            return normalized.isBlank() ? null : normalized;
        } else {
            String normalized = value.strip();

            if (normalized.isBlank())
                throw new BusinessRuleException("%s must not be blank".formatted(fieldName));

            return normalized;
        }
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
        Author author = mapper.toEntity(normalizedRequest);

        Author saved = repository.save(author);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthorResponse getAuthorById(Long id) {
        Author author = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Author with id '%s' not found".formatted(id)));

        return mapper.toResponse(author);
    }

    @Override
    @Transactional
    public AuthorResponse update(Long id, UpdateAuthorRequest request) {
        Author author = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Author with id '%s' not found".formatted(id)));

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

        mapper.updateEntity(normalizedRequest, author);

        if(request.middleName() != null && middleName == null)
            author.setMiddleName(null);

        Author saved = repository.save(author);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Author existing = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Author with id '%s' not found".formatted(id)));

        if (materialAuthorRepository.existsByAuthor_Id(id))
            throw new ResourceInUseException("Author with id '%s' is used by materials".formatted(id));

        repository.delete(existing);
    }
}
