package me.ifmo.backend.circulation.application.impl;

import io.micrometer.observation.annotation.Observed;
import me.ifmo.backend.fine.domain.enums.FineStatus;
import me.ifmo.backend.fine.persistence.FineRepository;

import me.ifmo.backend.circulation.domain.enums.LoanStatus;
import me.ifmo.backend.circulation.domain.enums.ReservationStatus;
import me.ifmo.backend.circulation.domain.Reservation;
import me.ifmo.backend.circulation.persistence.LoanRepository;
import me.ifmo.backend.circulation.persistence.ReservationRepository;

import me.ifmo.backend.catalog.domain.enums.CopyStatus;
import me.ifmo.backend.catalog.domain.enums.MaterialStatus;
import me.ifmo.backend.catalog.domain.Material;
import me.ifmo.backend.catalog.domain.MaterialAuthor;
import me.ifmo.backend.catalog.domain.MaterialCopy;
import me.ifmo.backend.catalog.domain.MaterialGenre;
import me.ifmo.backend.catalog.persistence.MaterialAuthorRepository;
import me.ifmo.backend.catalog.persistence.MaterialCopyRepository;
import me.ifmo.backend.catalog.persistence.MaterialGenreRepository;
import me.ifmo.backend.catalog.persistence.MaterialRepository;

import me.ifmo.backend.user.domain.enums.RoleCode;
import me.ifmo.backend.user.domain.enums.UserBlockStatus;
import me.ifmo.backend.user.domain.enums.UserStatus;
import me.ifmo.backend.user.domain.User;
import me.ifmo.backend.user.persistence.UserBlockRepository;
import me.ifmo.backend.user.persistence.UserRepository;
import me.ifmo.backend.user.persistence.UserRoleRepository;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.catalog.web.response.MaterialShortResponse;
import me.ifmo.backend.circulation.web.request.CancelReservationRequest;
import me.ifmo.backend.circulation.web.request.CreateReservationRequest;
import me.ifmo.backend.circulation.web.request.ReservationSearchRequest;
import me.ifmo.backend.circulation.web.response.ReservationResponse;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.library.domain.Branch;
import me.ifmo.backend.library.domain.LibraryRule;
import me.ifmo.backend.library.domain.enums.BranchStatus;
import me.ifmo.backend.library.domain.enums.LibraryRuleStatus;
import me.ifmo.backend.shared.error.BusinessRuleException;
import me.ifmo.backend.shared.error.ResourceInUseException;
import me.ifmo.backend.shared.error.ResourceNotFoundException;
import me.ifmo.backend.shared.cache.InvalidateCatalogSearch;
import me.ifmo.backend.shared.observability.LoggableOperation;
import me.ifmo.backend.catalog.mapper.MaterialMapper;
import me.ifmo.backend.circulation.mapper.ReservationMapper;
import me.ifmo.backend.library.persistence.BranchRepository;
import me.ifmo.backend.library.persistence.LibraryRuleRepository;
import me.ifmo.backend.circulation.application.ReservationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ReservationServiceImpl implements ReservationService {

    private static final Set<ReservationStatus> ACTIVE_RESERVATION_STATUSES =
            Set.of(ReservationStatus.ACTIVE, ReservationStatus.READY_FOR_PICKUP);

    private final ReservationRepository repository;
    private final UserRepository userRepository;
    private final MaterialRepository materialRepository;
    private final BranchRepository branchRepository;
    private final MaterialCopyRepository materialCopyRepository;
    private final LibraryRuleRepository libraryRuleRepository;
    private final MaterialAuthorRepository materialAuthorRepository;
    private final MaterialGenreRepository materialGenreRepository;
    private final UserBlockRepository userBlockRepository;
    private final FineRepository fineRepository;
    private final LoanRepository loanRepository;
    private final UserRoleRepository userRoleRepository;
    private final ReservationMapper reservationMapper;
    private final MaterialMapper materialMapper;

    private String normalize(String value) {
        if (value == null || value.strip().isBlank())
            throw new BusinessRuleException("%s must not be blank".formatted("Cancellation reason"));

        return value.strip();
    }

    private boolean isStaff(Long actorUserId) {
        return userRoleRepository.findRoleCodesByUser_Id(actorUserId).stream()
                .anyMatch(role -> role == RoleCode.LIBRARIAN || role == RoleCode.ADMIN);
    }

    private void validateOwnerOrStaff(Reservation reservation, Long actorUserId) {
        if (!reservation.getUser().getId().equals(actorUserId) && !isStaff(actorUserId))
            throw new AccessDeniedException("Access is denied");
    }

    private void validateReservationOwner(Reservation reservation, Long actorUserId) {
        if (!reservation.getUser().getId().equals(actorUserId))
            throw new AccessDeniedException("Access is denied");
    }

    private void validateStaff(Long actorUserId) {
        if (!isStaff(actorUserId))
            throw new AccessDeniedException("Access is denied");
    }

    private MaterialShortResponse toMaterialShortResponse(Material material) {
        List<MaterialAuthor> authors = materialAuthorRepository.findByMaterial_IdOrderByAuthorOrderAsc(material.getId());

        List<MaterialGenre> genres = materialGenreRepository.findByMaterial_Id(material.getId());

        return materialMapper.toShortResponse(material, authors, genres);
    }

    private ReservationResponse toResponse(Reservation reservation) {
        return reservationMapper.toResponse(reservation, toMaterialShortResponse(reservation.getMaterial()));
    }

    private LibraryRule getActualRule(Long branchId) {
        return libraryRuleRepository.findActualByBranchIdAndStatus(branchId, LibraryRuleStatus.ACTIVE, LocalDateTime.now()).orElseThrow(
                () -> new ResourceNotFoundException("Actual library rule for branch with id '%s' not found".formatted(branchId)));
    }

    private void validateUserCanReserve(User user) {
        if (userBlockRepository.existsByUser_IdAndStatus(user.getId(), UserBlockStatus.ACTIVE))
            throw new BusinessRuleException("User has active block");

        if (fineRepository.countByUser_IdAndStatus(user.getId(), FineStatus.ACTIVE) > 0)
            throw new BusinessRuleException("User has unpaid fines");

        if (loanRepository.countByUser_IdAndStatusIn(user.getId(), Set.of(LoanStatus.OVERDUE, LoanStatus.LOST)) > 0)
            throw new BusinessRuleException("User has overdue or lost loans");
    }

    private void releaseReservedCopy(Reservation reservation) {
        if (reservation.getCopy().getStatus() == CopyStatus.RESERVED)
            reservation.getCopy().setStatus(CopyStatus.AVAILABLE);
    }

    private MaterialCopy resolveCopy(CreateReservationRequest request) {
        if (request.copyId() != null) {
            MaterialCopy copy = materialCopyRepository.findById(request.copyId()).orElseThrow(
                    () -> new ResourceNotFoundException("Material copy with id '%s' not found".formatted(request.copyId())));

            if (!copy.getMaterial().getId().equals(request.materialId()))
                throw new BusinessRuleException("Material copy does not belong to requested material");

            if (!copy.getBranch().getId().equals(request.branchId()))
                throw new BusinessRuleException("Material copy does not belong to requested branch");

            if (copy.getStatus() != CopyStatus.AVAILABLE)
                throw new ResourceInUseException("Material copy is not available for reservation");

            return copy;
        }

        return materialCopyRepository.findFirstByMaterial_IdAndBranch_IdAndStatusOrderByCreatedAtAsc(
                        request.materialId(), request.branchId(), CopyStatus.AVAILABLE).orElseThrow(
                        () -> new ResourceNotFoundException("Available material copy for material id '%s' in branch id '%s' not found"
                                .formatted(request.materialId(), request.branchId())));
    }

    @Override
    @Transactional
    @InvalidateCatalogSearch
    @LoggableOperation("reservation.create")
    @Observed(
            name = "library.operation",
            contextualName = "reservation.create",
            lowCardinalityKeyValues = {"domain", "circulation", "operation", "reservation.create"}
    )
    public ReservationResponse create(Long actorUserId, CreateReservationRequest request) {
        if (!request.userId().equals(actorUserId) && !isStaff(actorUserId))
            throw new AccessDeniedException("Access is denied");

        User user = userRepository.findById(request.userId()).orElseThrow(
                () -> new ResourceNotFoundException("User with id '%s' not found".formatted(request.userId())));

        if (user.getStatus() != UserStatus.ACTIVE)
            throw new BusinessRuleException("Reservation can be created only for active user");

        validateUserCanReserve(user);

        Material material = materialRepository.findById(request.materialId()).orElseThrow(
                () -> new ResourceNotFoundException("Material with id '%s' not found".formatted(request.materialId())));

        if (material.getStatus() != MaterialStatus.ACTIVE)
            throw new BusinessRuleException("Reservation can be created only for active material");

        Branch branch = branchRepository.findById(request.branchId()).orElseThrow(
                () -> new ResourceNotFoundException("Branch with id '%s' not found".formatted(request.branchId())));

        if (branch.getStatus() != BranchStatus.ACTIVE)
            throw new BusinessRuleException("Reservation can be created only for active branch");

        LibraryRule rule = getActualRule(branch.getId());

        if (Boolean.FALSE.equals(rule.getReservationAllowed()))
            throw new BusinessRuleException("Reservations are not allowed for this branch");

        Long activeReservationCount = repository.countByUser_IdAndStatusIn(user.getId(), ACTIVE_RESERVATION_STATUSES);

        if (activeReservationCount >= rule.getMaxActiveReservations())
            throw new BusinessRuleException("User has reached active reservation limit");

        if (repository.existsByUser_IdAndMaterial_IdAndStatusIn(user.getId(), material.getId(), ACTIVE_RESERVATION_STATUSES))
            throw new BusinessRuleException("User already has active reservation for this material");

        MaterialCopy copy = resolveCopy(request);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusDays(rule.getReservationTtlDays());

        copy.setStatus(CopyStatus.RESERVED);

        Reservation reservation = reservationMapper.toEntity(user, material, copy, branch, expiresAt);
        reservation.setStatus(ReservationStatus.ACTIVE);

        Reservation saved = repository.save(reservation);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ReservationResponse getReservationById(Long actorUserId, Long id) {
        Reservation reservation = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Reservation with id '%s' not found".formatted(id)));
        validateOwnerOrStaff(reservation, actorUserId);
        return toResponse(reservation);
    }

    @Override
    @Transactional
    @InvalidateCatalogSearch
    @LoggableOperation("reservation.cancel-by-user")
    @Observed(
            name = "library.operation",
            contextualName = "reservation.cancel-by-user",
            lowCardinalityKeyValues = {"domain", "circulation", "operation", "reservation.cancel-by-user"}
    )
    public ReservationResponse cancelByUser(Long actorUserId, Long id, CancelReservationRequest request) {
        Reservation reservation = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Reservation with id '%s' not found".formatted(id)));
        validateReservationOwner(reservation, actorUserId);

        if (!ACTIVE_RESERVATION_STATUSES.contains(reservation.getStatus()))
            throw new BusinessRuleException("Only active reservation can be cancelled");

        reservation.setStatus(ReservationStatus.CANCELLED_BY_USER);
        reservation.setCancelledAt(LocalDateTime.now());
        reservation.setCancellationReason(normalize(request.reason()));

        releaseReservedCopy(reservation);

        Reservation saved = repository.save(reservation);
        return toResponse(saved);
    }

    @Override
    @Transactional
    @InvalidateCatalogSearch
    @LoggableOperation("reservation.cancel-by-librarian")
    @Observed(
            name = "library.operation",
            contextualName = "reservation.cancel-by-librarian",
            lowCardinalityKeyValues = {"domain", "circulation", "operation", "reservation.cancel-by-librarian"}
    )
    public ReservationResponse cancelByLibrarian(Long actorUserId, Long id, CancelReservationRequest request) {
        validateStaff(actorUserId);

        Reservation reservation = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Reservation with id '%s' not found".formatted(id)));

        if (!ACTIVE_RESERVATION_STATUSES.contains(reservation.getStatus()))
            throw new BusinessRuleException("Only active reservation can be cancelled");

        reservation.setStatus(ReservationStatus.CANCELLED_BY_LIBRARIAN);
        reservation.setCancelledAt(LocalDateTime.now());
        reservation.setCancellationReason(normalize(request.reason()));

        releaseReservedCopy(reservation);

        Reservation saved = repository.save(reservation);
        return toResponse(saved);
    }

    @Override
    @Transactional
    @InvalidateCatalogSearch
    @LoggableOperation("reservation.expire")
    @Observed(
            name = "library.operation",
            contextualName = "reservation.expire",
            lowCardinalityKeyValues = {"domain", "circulation", "operation", "reservation.expire"}
    )
    public ReservationResponse expire(Long actorUserId, Long id) {
        validateStaff(actorUserId);

        Reservation reservation = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Reservation with id '%s' not found".formatted(id)));

        if (!ACTIVE_RESERVATION_STATUSES.contains(reservation.getStatus()))
            throw new BusinessRuleException("Only active reservation can be expired");

        if (reservation.getExpiresAt().isAfter(LocalDateTime.now()))
            throw new BusinessRuleException("Reservation has not expired yet");

        reservation.setStatus(ReservationStatus.EXPIRED);

        releaseReservedCopy(reservation);

        Reservation saved = repository.save(reservation);
        return toResponse(saved);
    }

    @Override
    @Transactional
    @LoggableOperation("reservation.mark-ready-for-pickup")
    @Observed(
            name = "library.operation",
            contextualName = "reservation.mark-ready-for-pickup",
            lowCardinalityKeyValues = {"domain", "circulation", "operation", "reservation.mark-ready-for-pickup"}
    )
    public ReservationResponse markReadyForPickup(Long actorUserId, Long id) {
        validateStaff(actorUserId);

        Reservation reservation = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Reservation with id '%s' not found".formatted(id)));

        if (reservation.getStatus() != ReservationStatus.ACTIVE)
            throw new BusinessRuleException("Only active reservation can be marked as ready for pickup");

        if (reservation.getExpiresAt().isBefore(LocalDateTime.now()))
            throw new BusinessRuleException("Expired reservation cannot be marked as ready for pickup");

        if (reservation.getCopy().getStatus() != CopyStatus.RESERVED)
            throw new BusinessRuleException("Reserved material copy has invalid status");

        reservation.setStatus(ReservationStatus.READY_FOR_PICKUP);
        reservation.setReadyAt(LocalDateTime.now());

        Reservation saved = repository.save(reservation);
        return toResponse(saved);
    }

    @Override
    @Transactional
    @LoggableOperation("reservation.mark-used")
    @Observed(
            name = "library.operation",
            contextualName = "reservation.mark-used",
            lowCardinalityKeyValues = {"domain", "circulation", "operation", "reservation.mark-used"}
    )
    public ReservationResponse markUsed(Long actorUserId, Long id) {
        validateStaff(actorUserId);

        Reservation reservation = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Reservation with id '%s' not found".formatted(id)));

        if (reservation.getStatus() != ReservationStatus.READY_FOR_PICKUP)
            throw new BusinessRuleException("Only ready for pickup reservation can be marked as used");

        if (reservation.getExpiresAt().isBefore(LocalDateTime.now()))
            throw new BusinessRuleException("Expired reservation cannot be marked as used");

        if (reservation.getCopy().getStatus() != CopyStatus.RESERVED)
            throw new BusinessRuleException("Reserved material copy has invalid status");

        reservation.setStatus(ReservationStatus.USED);

        Reservation saved = repository.save(reservation);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReservationResponse> search(Long actorUserId, ReservationSearchRequest request, Pageable pageable) {
        boolean staff = isStaff(actorUserId);
        Long userId = request.userId();

        if (!staff) {
            if (userId != null && !userId.equals(actorUserId))
                throw new AccessDeniedException("Access is denied");
            userId = actorUserId;
        }

        Long filterUserId = userId;
        Specification<Reservation> specification = (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();

        if (filterUserId != null)
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("user").get("id"), filterUserId));
        if (request.materialId() != null)
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("material").get("id"), request.materialId()));
        if (request.copyId() != null)
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("copy").get("id"), request.copyId()));
        if (request.branchId() != null)
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("branch").get("id"), request.branchId()));
        if (request.status() != null)
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("status"), request.status()));
        if (request.createdFrom() != null)
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), request.createdFrom()));
        if (request.createdTo() != null)
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), request.createdTo()));
        if (request.expiresBefore() != null)
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.lessThanOrEqualTo(root.get("expiresAt"), request.expiresBefore()));

        String normalizedQuery = request.query() != null ? request.query().strip() : "";
        if (!normalizedQuery.isEmpty()) {
            String lowerPattern = "%" + normalizedQuery.toLowerCase(Locale.ROOT) + "%";
            String inventoryPattern = "%" + normalizedQuery + "%";
            specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("material").get("title")), lowerPattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("user").get("email")), lowerPattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("user").get("firstName")), lowerPattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("user").get("lastName")), lowerPattern),
                    criteriaBuilder.like(root.get("copy").get("inventoryNumber"), inventoryPattern)
            ));
        }

        Page<Reservation> reservations = repository.findAll(specification, pageable);

        Page<ReservationResponse> responses = reservations.map(this::toResponse);

        return PageResponse.from(responses);
    }
}
