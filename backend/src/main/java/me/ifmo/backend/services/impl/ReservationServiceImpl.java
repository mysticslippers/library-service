package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.catalog.response.MaterialShortResponse;
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
}
