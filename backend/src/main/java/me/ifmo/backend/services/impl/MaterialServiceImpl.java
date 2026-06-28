package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.catalog.request.ChangeMaterialStatusRequest;
import me.ifmo.backend.dto.catalog.request.CreateMaterialRequest;
import me.ifmo.backend.dto.catalog.request.MaterialAuthorRequest;
import me.ifmo.backend.dto.catalog.request.UpdateMaterialRequest;
import me.ifmo.backend.dto.catalog.response.MaterialResponse;
import me.ifmo.backend.entities.*;
import me.ifmo.backend.entities.enums.CopyStatus;
import me.ifmo.backend.entities.enums.MaterialStatus;
import me.ifmo.backend.entities.id.MaterialAuthorId;
import me.ifmo.backend.entities.id.MaterialGenreId;
import me.ifmo.backend.exceptions.domain.BusinessRuleException;
import me.ifmo.backend.exceptions.domain.DuplicateResourceException;
import me.ifmo.backend.exceptions.domain.ResourceInUseException;
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
    private final MaterialCopyRepository materialCopyRepository;
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

    private List<MaterialAuthor> saveAuthors(Material material, List<MaterialAuthorRequest> authorRequests) {
        List<MaterialAuthor> materialAuthors = new ArrayList<>();

        if (authorRequests == null)
            return materialAuthors;

        Set<Long> authorIds = new HashSet<>();
        int defaultOrder = 1;

        for (MaterialAuthorRequest authorRequest : authorRequests) {
            if (authorRequest == null || authorRequest.authorId() == null)
                throw new BusinessRuleException("Author id must not be null");

            if (!authorIds.add(authorRequest.authorId()))
                throw new BusinessRuleException("Duplicate author id '%s'".formatted(authorRequest.authorId()));

            Author author = authorRepository.findById(authorRequest.authorId()).orElseThrow(
                    () -> new ResourceNotFoundException(
                            "Author with id '%s' not found".formatted(authorRequest.authorId())));

            Integer authorOrder = (authorRequest.authorOrder() != null) ? authorRequest.authorOrder() : defaultOrder;

            materialAuthors.add(MaterialAuthor.builder().id(new MaterialAuthorId(material.getId(), author.getId())).material(material)
                    .author(author).authorOrder(authorOrder).build());

            defaultOrder++;
        }

        return materialAuthorRepository.saveAll(materialAuthors);
    }

    private List<MaterialGenre> saveGenres(Material material, Set<Long> genreIds) {
        List<MaterialGenre> materialGenres = new ArrayList<>();

        if (genreIds == null)
            return materialGenres;

        for (Long genreId : genreIds) {
            if (genreId == null)
                throw new BusinessRuleException("Genre id must not be null");

            Genre genre = genreRepository.findById(genreId).orElseThrow(
                    () -> new ResourceNotFoundException("Genre with id '%s' not found".formatted(genreId)));

            materialGenres.add(MaterialGenre.builder().id(new MaterialGenreId(material.getId(), genre.getId()))
                    .material(material).genre(genre).build());
        }

        return materialGenreRepository.saveAll(materialGenres);
    }

    private boolean isTransitionAllowed(MaterialStatus current, MaterialStatus target) {
        return switch (current) {
            case ACTIVE -> target == MaterialStatus.HIDDEN
                            || target == MaterialStatus.ARCHIVED
                            || target == MaterialStatus.REMOVED;
            case HIDDEN -> target == MaterialStatus.ACTIVE
                            || target == MaterialStatus.ARCHIVED
                            || target == MaterialStatus.REMOVED;
            case ARCHIVED -> target == MaterialStatus.ACTIVE
                            || target == MaterialStatus.REMOVED;
            case REMOVED -> false;
        };
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

        List<MaterialAuthor> authors = saveAuthors(saved, request.authors());

        List<MaterialGenre> materialGenres = saveGenres(saved, request.genreIds());

        return mapper.toResponse(saved, authors, materialGenres);
    }

    @Override
    @Transactional(readOnly = true)
    public MaterialResponse getMaterialById(Long id) {
        Material material = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Material with id '%s' not found".formatted(id)));

        List<MaterialAuthor> authors = materialAuthorRepository.findByMaterial_IdOrderByAuthorOrderAsc(material.getId());

        List<MaterialGenre> genres = materialGenreRepository.findByMaterial_Id(material.getId());

        return mapper.toResponse(material, authors, genres);
    }

    @Override
    @Transactional(readOnly = true)
    public MaterialResponse getMaterialByIsbn(String isbn) {
        String normalizedIsbn = normalize(isbn, "Isbn");

        Material material = repository.findByIsbn(normalizedIsbn).orElseThrow(
                () -> new ResourceNotFoundException("Material with isbn '%s' not found".formatted(normalizedIsbn)));

        List<MaterialAuthor> authors = materialAuthorRepository.findByMaterial_IdOrderByAuthorOrderAsc(material.getId());

        List<MaterialGenre> genres = materialGenreRepository.findByMaterial_Id(material.getId());

        return mapper.toResponse(material, authors, genres);
    }

    @Override
    @Transactional
    public MaterialResponse update(Long id, UpdateMaterialRequest request) {
        Material material = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Material with id '%s' not found".formatted(id)));

        if (material.getStatus() == MaterialStatus.REMOVED)
            throw new BusinessRuleException("Removed material cannot be updated");

        String isbn = request.isbn() != null ? normalize(request.isbn(), "Isbn") : null;
        String title = request.title() != null ? normalize(request.title(), "Title") : null;
        String description = request.description() != null ? normalize(request.description(), "Description") : null;
        String publisher = request.publisher() != null ? normalize(request.publisher(), "Publisher") : null;
        String language = request.language() != null ? normalize(request.language(), "Language") : null;

        if (isbn != null && !isbn.equals(material.getIsbn()) && repository.existsByIsbn(isbn))
            throw new DuplicateResourceException("Material with isbn '%s' already exists".formatted(isbn));

        mapper.updateEntity(new UpdateMaterialRequest(isbn, title, description, publisher, request.publicationYear(),
                request.materialType(), language, request.authors(), request.genreIds()), material);

        if (request.description() != null)
            material.setDescription(description);

        if (request.publisher() != null)
            material.setPublisher(publisher);

        Material saved = repository.save(material);

        if (request.authors() != null) {
            materialAuthorRepository.deleteByMaterial_Id(saved.getId());
            saveAuthors(saved, request.authors());
        }

        if (request.genreIds() != null) {
            materialGenreRepository.deleteByMaterial_Id(saved.getId());
            saveGenres(saved, request.genreIds());
        }

        List<MaterialAuthor> authors = materialAuthorRepository.findByMaterial_IdOrderByAuthorOrderAsc(saved.getId());

        List<MaterialGenre> genres = materialGenreRepository.findByMaterial_Id(saved.getId());

        return mapper.toResponse(saved, authors, genres);
    }

    @Override
    @Transactional
    public MaterialResponse changeStatus(Long id, ChangeMaterialStatusRequest request) {
        Material material = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Material with id '%s' not found".formatted(id)));

        MaterialStatus status = request.status();

        List<MaterialAuthor> authors = materialAuthorRepository.findByMaterial_IdOrderByAuthorOrderAsc(material.getId());

        List<MaterialGenre> genres = materialGenreRepository.findByMaterial_Id(material.getId());

        if (material.getStatus() == status)
            mapper.toResponse(material, authors, genres);

        if (!isTransitionAllowed(material.getStatus(), status))
            throw new BusinessRuleException(
                    "Material status transition from '%s' to '%s' is not allowed".formatted(material.getStatus(), status));

        if (status == MaterialStatus.REMOVED && materialCopyRepository.existsByMaterial_IdAndStatusNot(id, CopyStatus.REMOVED))
            throw new ResourceInUseException("Material has non-removed copies");

        material.setStatus(status);

        Material saved = repository.save(material);
        return mapper.toResponse(saved, authors, genres);
    }
}
