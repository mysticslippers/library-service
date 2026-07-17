package me.ifmo.backend.services.impl;

import me.ifmo.backend.user.domain.Role;
import me.ifmo.backend.user.persistence.UserRoleRepository;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.catalog.request.*;
import me.ifmo.backend.dto.catalog.response.MaterialResponse;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.entities.*;
import me.ifmo.backend.entities.enums.CopyStatus;
import me.ifmo.backend.entities.enums.AuthorStatus;
import me.ifmo.backend.entities.enums.GenreStatus;
import me.ifmo.backend.entities.enums.LoanStatus;
import me.ifmo.backend.entities.enums.MaterialStatus;
import me.ifmo.backend.entities.enums.MaterialType;
import me.ifmo.backend.entities.enums.ReservationStatus;
import me.ifmo.backend.user.domain.enums.RoleCode;
import me.ifmo.backend.entities.id.MaterialAuthorId;
import me.ifmo.backend.entities.id.MaterialGenreId;
import me.ifmo.backend.shared.error.BusinessRuleException;
import me.ifmo.backend.shared.error.DuplicateResourceException;
import me.ifmo.backend.shared.error.ResourceInUseException;
import me.ifmo.backend.shared.error.ResourceNotFoundException;
import me.ifmo.backend.mappers.MaterialMapper;
import me.ifmo.backend.repositories.*;
import me.ifmo.backend.services.MaterialService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MaterialServiceImpl implements MaterialService {

    private static final Set<CopyStatus> ACTIVE_COPY_STATUSES = Set.of(
            CopyStatus.AVAILABLE,
            CopyStatus.RESERVED,
            CopyStatus.LOANED,
            CopyStatus.DAMAGED,
            CopyStatus.LOST,
            CopyStatus.UNDER_REPAIR
    );

    private static final Set<LoanStatus> BLOCKING_LOAN_STATUSES = Set.of(
            LoanStatus.ACTIVE,
            LoanStatus.OVERDUE,
            LoanStatus.LOST
    );

    private static final Set<ReservationStatus> BLOCKING_RESERVATION_STATUSES = Set.of(
            ReservationStatus.ACTIVE,
            ReservationStatus.READY_FOR_PICKUP
    );

    private final MaterialRepository repository;
    private final AuthorRepository authorRepository;
    private final GenreRepository genreRepository;
    private final MaterialAuthorRepository materialAuthorRepository;
    private final MaterialCopyRepository materialCopyRepository;
    private final MaterialGenreRepository materialGenreRepository;
    private final LoanRepository loanRepository;
    private final ReservationRepository reservationRepository;
    private final UserRoleRepository userRoleRepository;
    private final MaterialMapper materialMapper;

    private String normalize(String value, String fieldName) {
        if (value == null || value.strip().isBlank())
            throw new BusinessRuleException("%s must not be blank".formatted(fieldName));

        return value.strip();
    }

    private String normalizeOptional(String value) {
        if (value == null)
            return null;

        String normalized = value.strip();
        return normalized.isBlank() ? null : normalized;
    }

    private boolean isCatalogStaff(Long actorUserId) {
        return userRoleRepository.findRoleCodesByUser_Id(actorUserId).stream()
                .anyMatch(role -> role == RoleCode.LIBRARIAN || role == RoleCode.ADMIN);
    }

    private void validateVisible(Material material, boolean staff) {
        if (!staff && material.getStatus() != MaterialStatus.ACTIVE)
            throw new ResourceNotFoundException("Material with id '%s' not found".formatted(material.getId()));
    }

    private void validateRequiredCatalogData(List<MaterialAuthorRequest> authors, Set<Long> genreIds,
                                             Integer publicationYear, MaterialType materialType, String language) {
        if (publicationYear == null)
            throw new BusinessRuleException("Publication year must not be null");

        if (materialType == null)
            throw new BusinessRuleException("Material type must not be null");

        normalize(language, "Language");

        if (authors == null || authors.isEmpty())
            throw new BusinessRuleException("Material must have at least one author");

        if (genreIds == null || genreIds.isEmpty())
            throw new BusinessRuleException("Material must have at least one genre");
    }

    private Set<Long> validateAuthorRequests(List<MaterialAuthorRequest> authorRequests) {
        if (authorRequests == null || authorRequests.isEmpty())
            throw new BusinessRuleException("Material must have at least one author");

        Set<Long> authorIds = new HashSet<>();
        Set<Integer> authorOrders = new HashSet<>();

        int defaultOrder = 1;
        for (MaterialAuthorRequest authorRequest : authorRequests) {
            if (authorRequest == null || authorRequest.authorId() == null)
                throw new BusinessRuleException("Author id must not be null");

            if (!authorIds.add(authorRequest.authorId()))
                throw new BusinessRuleException("Duplicate author id '%s'".formatted(authorRequest.authorId()));

            Author author = authorRepository.findById(authorRequest.authorId()).orElseThrow(
                    () -> new ResourceNotFoundException(
                            "Author with id '%s' not found".formatted(authorRequest.authorId())));

            if (author.getStatus() == AuthorStatus.ARCHIVED)
                throw new BusinessRuleException("Archived author cannot be assigned to material");

            Integer authorOrder = (authorRequest.authorOrder() != null) ? authorRequest.authorOrder() : defaultOrder;
            if (!authorOrders.add(authorOrder))
                throw new BusinessRuleException("Duplicate author order '%s'".formatted(authorOrder));

            defaultOrder++;
        }

        return authorIds;
    }

    private void validateGenreIds(Set<Long> genreIds) {
        if (genreIds == null || genreIds.isEmpty())
            throw new BusinessRuleException("Material must have at least one genre");

        for (Long genreId : genreIds) {
            if (genreId == null)
                throw new BusinessRuleException("Genre id must not be null");

            Genre genre = genreRepository.findById(genreId).orElseThrow(
                    () -> new ResourceNotFoundException("Genre with id '%s' not found".formatted(genreId)));

            if (genre.getStatus() == GenreStatus.ARCHIVED)
                throw new BusinessRuleException("Archived genre cannot be assigned to material");
        }
    }

    private void validateNoDuplicateMaterial(Long excludedId, String title, Integer publicationYear, Set<Long> authorIds) {
        if (title == null || publicationYear == null || authorIds == null || authorIds.isEmpty())
            return;

        if (!repository.findDuplicateIds(excludedId, MaterialStatus.REMOVED, title, publicationYear,
                authorIds, authorIds.size()).isEmpty())
            throw new DuplicateResourceException("Material with the same title, authors and publication year already exists");
    }

    private Set<Long> currentAuthorIds(Long materialId) {
        return materialAuthorRepository.findByMaterial_IdOrderByAuthorOrderAsc(materialId).stream()
                .map(materialAuthor -> materialAuthor.getAuthor().getId())
                .collect(Collectors.toSet());
    }

    private void validateCurrentReferencesActive(Long materialId) {
        materialAuthorRepository.findByMaterial_IdOrderByAuthorOrderAsc(materialId).stream()
                .map(MaterialAuthor::getAuthor)
                .filter(author -> author.getStatus() == AuthorStatus.ARCHIVED)
                .findFirst()
                .ifPresent(author -> {
                    throw new BusinessRuleException("Archived author cannot be assigned to active material");
                });

        materialGenreRepository.findByMaterial_Id(materialId).stream()
                .map(MaterialGenre::getGenre)
                .filter(genre -> genre.getStatus() == GenreStatus.ARCHIVED)
                .findFirst()
                .ifPresent(genre -> {
                    throw new BusinessRuleException("Archived genre cannot be assigned to active material");
                });
    }

    private boolean hasBlockingOperations(Long materialId) {
        return loanRepository.existsByCopy_Material_IdAndStatusIn(materialId, BLOCKING_LOAN_STATUSES)
                || reservationRepository.existsByMaterial_IdAndStatusIn(materialId, BLOCKING_RESERVATION_STATUSES);
    }

    private void validateCanArchive(Long materialId) {
        if (materialCopyRepository.existsByMaterial_IdAndStatusIn(materialId, ACTIVE_COPY_STATUSES))
            throw new ResourceInUseException("Material has active copies");

        if (hasBlockingOperations(materialId))
            throw new ResourceInUseException("Material has active operations");
    }

    private void saveAuthors(Material material, List<MaterialAuthorRequest> authorRequests) {
        List<MaterialAuthor> materialAuthors = new ArrayList<>();

        if (authorRequests == null)
            return;

        int defaultOrder = 1;

        for (MaterialAuthorRequest authorRequest : authorRequests) {
            Author author = authorRepository.findById(authorRequest.authorId()).orElseThrow(
                    () -> new ResourceNotFoundException(
                            "Author with id '%s' not found".formatted(authorRequest.authorId())));

            Integer authorOrder = (authorRequest.authorOrder() != null) ? authorRequest.authorOrder() : defaultOrder;

            materialAuthors.add(MaterialAuthor.builder().id(new MaterialAuthorId(material.getId(), author.getId())).material(material)
                    .author(author).authorOrder(authorOrder).build());

            defaultOrder++;
        }

        materialAuthorRepository.saveAll(materialAuthors);
    }

    private void saveGenres(Material material, Set<Long> genreIds) {
        List<MaterialGenre> materialGenres = new ArrayList<>();

        if (genreIds == null)
            return;

        for (Long genreId : genreIds) {
            Genre genre = genreRepository.findById(genreId).orElseThrow(
                    () -> new ResourceNotFoundException("Genre with id '%s' not found".formatted(genreId)));

            materialGenres.add(MaterialGenre.builder().id(new MaterialGenreId(material.getId(), genre.getId()))
                    .material(material).genre(genre).build());
        }

        materialGenreRepository.saveAll(materialGenres);
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

    private MaterialResponse toResponse(Material material, boolean includeRemovedCopies) {
        List<MaterialAuthor> authors = materialAuthorRepository.findByMaterial_IdOrderByAuthorOrderAsc(material.getId());

        List<MaterialGenre> genres = materialGenreRepository.findByMaterial_Id(material.getId());

        List<MaterialCopy> copies = materialCopyRepository.findByMaterial_Id(material.getId()).stream()
                .filter(copy -> includeRemovedCopies || copy.getStatus() != CopyStatus.REMOVED)
                .toList();

        long availableCopies = copies.stream()
                .filter(copy -> copy.getStatus() == CopyStatus.AVAILABLE)
                .count();

        return materialMapper.toResponse(material, authors, genres, copies, copies.size(), availableCopies);
    }

    @Override
    @Transactional
    public MaterialResponse create(CreateMaterialRequest request) {
        String normalizedIsbn = normalizeOptional(request.isbn());
        String normalizedTitle = normalize(request.title(), "Title");
        String normalizedDescription = normalizeOptional(request.description());
        String normalizedPublisher = normalizeOptional(request.publisher());
        String normalizedLanguage = normalize(request.language(), "Language");

        validateRequiredCatalogData(request.authors(), request.genreIds(), request.publicationYear(),
                request.materialType(), normalizedLanguage);
        Set<Long> authorIds = validateAuthorRequests(request.authors());
        validateGenreIds(request.genreIds());
        validateNoDuplicateMaterial(null, normalizedTitle, request.publicationYear(), authorIds);

        if (normalizedIsbn != null && repository.existsByIsbn(normalizedIsbn))
            throw new DuplicateResourceException(
                    "Material with isbn '%s' already exists".formatted(normalizedIsbn));

        CreateMaterialRequest normalizedRequest = new CreateMaterialRequest(normalizedIsbn, normalizedTitle,
                normalizedDescription, normalizedPublisher, request.publicationYear(), request.materialType(),
                normalizedLanguage, request.authors(), request.genreIds()
        );

        Material material = materialMapper.toEntity(normalizedRequest);
        Material saved = repository.save(material);

        saveAuthors(saved, request.authors());
        saveGenres(saved, request.genreIds());

        return toResponse(saved, true);
    }

    @Override
    @Transactional(readOnly = true)
    public MaterialResponse getMaterialById(Long actorUserId, Long id) {
        boolean staff = isCatalogStaff(actorUserId);
        Material material = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Material with id '%s' not found".formatted(id)));
        validateVisible(material, staff);

        return toResponse(material, staff);
    }

    @Override
    @Transactional(readOnly = true)
    public MaterialResponse getMaterialByIsbn(Long actorUserId, String isbn) {
        boolean staff = isCatalogStaff(actorUserId);
        String normalizedIsbn = normalize(isbn, "Isbn");

        Material material = repository.findByIsbn(normalizedIsbn).orElseThrow(
                () -> new ResourceNotFoundException("Material with isbn '%s' not found".formatted(normalizedIsbn)));
        validateVisible(material, staff);

        return toResponse(material, staff);
    }

    @Override
    @Transactional
    public MaterialResponse update(Long id, UpdateMaterialRequest request) {
        Material material = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Material with id '%s' not found".formatted(id)));

        if (material.getStatus() == MaterialStatus.REMOVED)
            throw new BusinessRuleException("Removed material cannot be updated");

        if (material.getStatus() == MaterialStatus.ARCHIVED)
            throw new BusinessRuleException("Archived material cannot be updated");

        String isbn = request.isbn() != null ? normalizeOptional(request.isbn()) : null;
        String title = request.title() != null ? normalize(request.title(), "Title") : null;
        String description = request.description() != null ? normalizeOptional(request.description()) : null;
        String publisher = request.publisher() != null ? normalizeOptional(request.publisher()) : null;
        String language = request.language() != null ? normalize(request.language(), "Language") : null;

        if (request.genreIds() != null)
            validateGenreIds(request.genreIds());

        String effectiveTitle = title != null ? title : material.getTitle();
        Integer effectivePublicationYear = request.publicationYear() != null
                ? request.publicationYear()
                : material.getPublicationYear();
        Set<Long> effectiveAuthorIds = request.authors() != null
                ? validateAuthorRequests(request.authors())
                : currentAuthorIds(material.getId());
        validateNoDuplicateMaterial(material.getId(), effectiveTitle, effectivePublicationYear, effectiveAuthorIds);

        if (isbn != null && !isbn.equals(material.getIsbn()) && repository.existsByIsbn(isbn))
            throw new DuplicateResourceException("Material with isbn '%s' already exists".formatted(isbn));

        materialMapper.updateEntity(new UpdateMaterialRequest(isbn, title, description, publisher, request.publicationYear(),
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

        return toResponse(saved, true);
    }

    @Override
    @Transactional
    public MaterialResponse changeStatus(Long id, ChangeMaterialStatusRequest request) {
        Material material = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Material with id '%s' not found".formatted(id)));

        MaterialStatus status = request.status();

        if (material.getStatus() == status)
            return toResponse(material, true);

        if (!isTransitionAllowed(material.getStatus(), status))
            throw new BusinessRuleException(
                    "Material status transition from '%s' to '%s' is not allowed".formatted(material.getStatus(), status));

        if (status == MaterialStatus.ARCHIVED)
            validateCanArchive(id);

        if (status == MaterialStatus.REMOVED && materialCopyRepository.existsByMaterial_IdAndStatusNot(id, CopyStatus.REMOVED))
            throw new ResourceInUseException("Material has non-removed copies");

        if (status == MaterialStatus.ACTIVE)
            validateCurrentReferencesActive(id);

        material.setStatus(status);

        Material saved = repository.save(material);
        return toResponse(saved, true);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MaterialResponse> search(Long actorUserId, MaterialSearchRequest request, Pageable pageable) {
        boolean staff = isCatalogStaff(actorUserId);
        String query = request.query() != null ? request.query().strip() : "";
        MaterialStatus status = request.status();

        if (!staff) {
            if (status != null && status != MaterialStatus.ACTIVE)
                throw new AccessDeniedException("Access is denied");
            status = MaterialStatus.ACTIVE;
        }

        Page<Material> materials = repository.search(query, request.materialType(), status, request.publicationYear(),
                request.authorId(), request.genreId(), request.branchId(), CopyStatus.REMOVED, pageable);

        Page<MaterialResponse> responses = materials.map(material -> toResponse(material, staff));

        return PageResponse.from(responses);
    }
}
