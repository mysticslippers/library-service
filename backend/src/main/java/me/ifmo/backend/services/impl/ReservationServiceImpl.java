package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.catalog.response.MaterialShortResponse;
import me.ifmo.backend.dto.circulation.request.CancelReservationRequest;
import me.ifmo.backend.dto.circulation.request.CreateReservationRequest;
import me.ifmo.backend.dto.circulation.request.ReservationSearchRequest;
import me.ifmo.backend.dto.circulation.response.ReservationResponse;
import me.ifmo.backend.dto.common.response.PageResponse;
import me.ifmo.backend.entities.*;
import me.ifmo.backend.entities.enums.*;
import me.ifmo.backend.exceptions.domain.BusinessRuleException;
import me.ifmo.backend.exceptions.domain.ResourceInUseException;
import me.ifmo.backend.exceptions.domain.ResourceNotFoundException;
import me.ifmo.backend.mappers.MaterialMapper;
import me.ifmo.backend.mappers.ReservationMapper;
import me.ifmo.backend.repositories.*;
import me.ifmo.backend.services.ReservationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
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

        String query = request.query() != null ? request.query().strip() : "";

        Page<Reservation> reservations = repository.search(userId, request.materialId(), request.copyId(),
                request.branchId(), request.status(), request.createdFrom(), request.createdTo(), request.expiresBefore(),
                query, pageable);

        Page<ReservationResponse> responses = reservations.map(this::toResponse);

        return PageResponse.from(responses);
    }
}
