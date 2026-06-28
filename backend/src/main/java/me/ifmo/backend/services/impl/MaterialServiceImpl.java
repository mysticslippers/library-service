package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.catalog.request.CreateMaterialRequest;
import me.ifmo.backend.dto.catalog.request.MaterialAuthorRequest;
import me.ifmo.backend.dto.catalog.response.MaterialResponse;
import me.ifmo.backend.entities.*;
import me.ifmo.backend.entities.id.MaterialAuthorId;
import me.ifmo.backend.entities.id.MaterialGenreId;
import me.ifmo.backend.exceptions.domain.BusinessRuleException;
import me.ifmo.backend.exceptions.domain.DuplicateResourceException;
import me.ifmo.backend.exceptions.domain.ResourceNotFoundException;
import me.ifmo.backend.mappers.MaterialMapper;
import me.ifmo.backend.repositories.*;
import me.ifmo.backend.services.MaterialService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MaterialServiceImpl implements MaterialService {

    private final MaterialRepository repository;
    private final AuthorRepository authorRepository;
    private final GenreRepository genreRepository;
    private final MaterialAuthorRepository materialAuthorRepository;
    private final MaterialGenreRepository materialGenreRepository;
    private final MaterialMapper mapper;

    private String normalize(String value, String fieldName){
        String normalized;

        if(fieldName.equals("Title")){
            normalized = value.strip();

            if (normalized.isBlank())
                throw new BusinessRuleException("%s must not be blank".formatted(fieldName));

            return normalized;
        } else {
            if(value == null)
                return null;

            normalized = value.strip();
            return normalized.isBlank() ? null : normalized;
        }
    }

    @Override
    @Transactional
    public MaterialResponse create(CreateMaterialRequest request) {
        String normalizedIsbn = normalize(request.isbn(), "Isbn");
        String normalizedTitle = normalize(request.title(), "Title");
        String normalizedDescription = normalize(request.description(), "Description");
        String normalizedPublisher = normalize(request.publisher(), "Publisher");
        String normalizedLanguage = normalize(request.language(), "Language");

        if (normalizedIsbn != null && repository.existsByIsbn(normalizedIsbn))
            throw new DuplicateResourceException(
                    "Material with isbn '%s' already exists".formatted(normalizedIsbn));

        CreateMaterialRequest normalizedRequest = new CreateMaterialRequest(normalizedIsbn, normalizedTitle,
                normalizedDescription, normalizedPublisher, request.publicationYear(), request.materialType(),
                normalizedLanguage, request.authors(), request.genreIds()
        );

        Material material = mapper.toEntity(normalizedRequest);
        Material saved = repository.save(material);

        List<MaterialAuthor> materialAuthors = new ArrayList<>();

        if (request.authors() != null) {
            Set<Long> authorIds = new HashSet<>();
            int defaultOrder = 1;

            for (MaterialAuthorRequest authorRequest : request.authors()) {
                if (authorRequest == null || authorRequest.authorId() == null)
                    throw new BusinessRuleException("Author id must not be null");

                if (!authorIds.add(authorRequest.authorId()))
                    throw new BusinessRuleException("Duplicate author id '%s'".formatted(authorRequest.authorId()));

                Author author = authorRepository.findById(authorRequest.authorId()).orElseThrow(
                                () -> new ResourceNotFoundException(
                                        "Author with id '%s' not found".formatted(authorRequest.authorId())));

                Integer authorOrder = (authorRequest.authorOrder() != null) ? authorRequest.authorOrder() : defaultOrder;

                materialAuthors.add(MaterialAuthor.builder().id(new MaterialAuthorId(saved.getId(), author.getId())).material(saved)
                        .author(author).authorOrder(authorOrder).build());

                defaultOrder++;
            }

            materialAuthors = materialAuthorRepository.saveAll(materialAuthors);
        }

        List<MaterialGenre> materialGenres = new ArrayList<>();

        if (request.genreIds() != null) {
            for (Long genreId : request.genreIds()) {
                if (genreId == null)
                    throw new BusinessRuleException("Genre id must not be null");

                Genre genre = genreRepository.findById(genreId).orElseThrow(
                                () -> new ResourceNotFoundException("Genre with id '%s' not found".formatted(genreId)));

                materialGenres.add(MaterialGenre.builder().id(new MaterialGenreId(saved.getId(), genre.getId()))
                        .material(saved).genre(genre).build());
            }

            materialGenres = materialGenreRepository.saveAll(materialGenres);
        }

        return mapper.toResponse(saved, materialAuthors, materialGenres);
    }

    @Override
    @Transactional(readOnly = true)
    public MaterialResponse getMaterialByIsbn(String isbn) {
        String normalizedIsbn = normalize(isbn, "Isbn");

        Material material = repository.findByIsbn(normalizedIsbn).orElseThrow(
                () -> new ResourceNotFoundException("Material with isbn '%s' not found".formatted(normalizedIsbn)));

        List<MaterialAuthor> authors =
                materialAuthorRepository.findByMaterial_IdOrderByAuthorOrderAsc(material.getId());

        List<MaterialGenre> genres =
                materialGenreRepository.findByMaterial_Id(material.getId());

        return mapper.toResponse(material, authors, genres);
    }
}
