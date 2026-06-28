package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.catalog.response.MaterialShortResponse;
import me.ifmo.backend.dto.circulation.request.CancelReservationRequest;
import me.ifmo.backend.dto.circulation.request.CreateReservationRequest;
import me.ifmo.backend.dto.circulation.response.ReservationResponse;
import me.ifmo.backend.entities.*;
import me.ifmo.backend.entities.enums.*;
import me.ifmo.backend.exceptions.domain.BusinessRuleException;
import me.ifmo.backend.exceptions.domain.ResourceInUseException;
import me.ifmo.backend.exceptions.domain.ResourceNotFoundException;
import me.ifmo.backend.mappers.MaterialMapper;
import me.ifmo.backend.mappers.ReservationMapper;
import me.ifmo.backend.repositories.*;
import me.ifmo.backend.services.ReservationService;
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
    private final ReservationMapper mapper;
    private final MaterialMapper materialMapper;

    private String normalize(String value, String fieldName) {
        if (value == null || value.strip().isBlank())
            throw new BusinessRuleException("%s must not be blank".formatted(fieldName));

        return value.strip();
    }

    private MaterialShortResponse toMaterialShortResponse(Material material) {
        List<MaterialAuthor> authors = materialAuthorRepository.findByMaterial_IdOrderByAuthorOrderAsc(material.getId());

        List<MaterialGenre> genres = materialGenreRepository.findByMaterial_Id(material.getId());

        return materialMapper.toShortResponse(material, authors, genres);
    }

    private ReservationResponse toResponse(Reservation reservation) {
        return mapper.toResponse(reservation, toMaterialShortResponse(reservation.getMaterial()));
    }

    private LibraryRule getActualRule(Long branchId) {
        return libraryRuleRepository.findActualByBranchIdAndStatus(branchId, LibraryRuleStatus.ACTIVE, LocalDateTime.now()).orElseThrow(
                () -> new ResourceNotFoundException("Actual library rule for branch with id '%s' not found".formatted(branchId)));
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
    public ReservationResponse create(CreateReservationRequest request) {
        User user = userRepository.findById(request.userId()).orElseThrow(
                () -> new ResourceNotFoundException("User with id '%s' not found".formatted(request.userId())));

        if (user.getStatus() != UserStatus.ACTIVE)
            throw new BusinessRuleException("Reservation can be created only for active user");

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

        Reservation reservation = mapper.toEntity(user, material, copy, branch, expiresAt);
        reservation.setStatus(ReservationStatus.READY_FOR_PICKUP);
        reservation.setReadyAt(now);

        Reservation saved = repository.save(reservation);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ReservationResponse getReservationById(Long id) {
        Reservation reservation = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Reservation with id '%s' not found".formatted(id)));
        return toResponse(reservation);
    }

    @Override
    @Transactional
    public ReservationResponse cancelByUser(Long id, CancelReservationRequest request) {
        Reservation reservation = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Reservation with id '%s' not found".formatted(id)));

        if (!ACTIVE_RESERVATION_STATUSES.contains(reservation.getStatus()))
            throw new BusinessRuleException("Only active reservation can be cancelled");

        reservation.setStatus(ReservationStatus.CANCELLED_BY_USER);
        reservation.setCancelledAt(LocalDateTime.now());
        reservation.setCancellationReason(normalize(request.reason(), "Cancellation reason"));

        if (reservation.getCopy().getStatus() == CopyStatus.RESERVED)
            reservation.getCopy().setStatus(CopyStatus.AVAILABLE);

        Reservation saved = repository.save(reservation);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public ReservationResponse cancelByLibrarian(Long id, CancelReservationRequest request) {
        Reservation reservation = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Reservation with id '%s' not found".formatted(id)));

        if (!ACTIVE_RESERVATION_STATUSES.contains(reservation.getStatus()))
            throw new BusinessRuleException("Only active reservation can be cancelled");

        reservation.setStatus(ReservationStatus.CANCELLED_BY_LIBRARIAN);
        reservation.setCancelledAt(LocalDateTime.now());
        reservation.setCancellationReason(normalize(request.reason(), "Cancellation reason"));

        if (reservation.getCopy().getStatus() == CopyStatus.RESERVED)
            reservation.getCopy().setStatus(CopyStatus.AVAILABLE);

        Reservation saved = repository.save(reservation);
        return toResponse(saved);
    }
}
