package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.catalog.request.ChangeMaterialCopyStatusRequest;
import me.ifmo.backend.dto.catalog.request.CreateMaterialCopyRequest;
import me.ifmo.backend.dto.catalog.request.UpdateMaterialCopyRequest;
import me.ifmo.backend.dto.catalog.response.MaterialCopyResponse;
import me.ifmo.backend.dto.common.response.PageResponse;
import me.ifmo.backend.entities.Branch;
import me.ifmo.backend.entities.Material;
import me.ifmo.backend.entities.MaterialCopy;
import me.ifmo.backend.entities.enums.*;
import me.ifmo.backend.exceptions.domain.BusinessRuleException;
import me.ifmo.backend.exceptions.domain.DuplicateResourceException;
import me.ifmo.backend.exceptions.domain.ResourceInUseException;
import me.ifmo.backend.exceptions.domain.ResourceNotFoundException;
import me.ifmo.backend.mappers.MaterialCopyMapper;
import me.ifmo.backend.repositories.*;
import me.ifmo.backend.services.MaterialCopyService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class MaterialCopyServiceImpl implements MaterialCopyService {

    private static final Set<LoanStatus> BLOCKING_LOAN_STATUSES =
            Set.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE, LoanStatus.LOST);

    private static final Set<ReservationStatus> BLOCKING_RESERVATION_STATUSES =
            Set.of(ReservationStatus.ACTIVE, ReservationStatus.READY_FOR_PICKUP);

    private final MaterialCopyRepository repository;
    private final MaterialRepository materialRepository;
    private final BranchRepository branchRepository;
    private final LoanRepository loanRepository;
    private final ReservationRepository reservationRepository;
    private final MaterialCopyMapper mapper;

    private String normalize(String value, String fieldName) {
        if(fieldName.equals("Inventory number"))
            if (value == null || value.strip().isBlank())
                throw new BusinessRuleException("%s must not be blank".formatted(fieldName));
        else{
            return null;
        }

        return value.strip();
    }

    private boolean isTransitionAllowed(CopyStatus current, CopyStatus target) {
        return switch (current) {
            case AVAILABLE ->
                    target == CopyStatus.UNDER_REPAIR
                            || target == CopyStatus.DAMAGED
                            || target == CopyStatus.LOST
                            || target == CopyStatus.REMOVED;
            case UNDER_REPAIR ->
                    target == CopyStatus.AVAILABLE
                            || target == CopyStatus.DAMAGED
                            || target == CopyStatus.LOST
                            || target == CopyStatus.REMOVED;
            case DAMAGED ->
                    target == CopyStatus.UNDER_REPAIR
                            || target == CopyStatus.LOST
                            || target == CopyStatus.REMOVED;
            case LOST ->
                    target == CopyStatus.AVAILABLE
                            || target == CopyStatus.REMOVED;
            case RESERVED, LOANED, REMOVED -> false;
        };
    }

    private boolean hasActiveOperations(Long copyId) {
        return loanRepository.findByCopy_IdAndStatusIn(copyId, BLOCKING_LOAN_STATUSES).isPresent()
                || reservationRepository.findByCopy_IdAndStatusIn(copyId, BLOCKING_RESERVATION_STATUSES).isPresent();
    }

    @Override
    @Transactional
    public MaterialCopyResponse create(CreateMaterialCopyRequest request) {
        Material material = materialRepository.findById(request.materialId()).orElseThrow(
                () -> new ResourceNotFoundException("Material with id '%s' not found".formatted(request.materialId())));

        if (material.getStatus() != MaterialStatus.ACTIVE)
            throw new BusinessRuleException("Material copy can be created only for active material");

        Branch branch = branchRepository.findById(request.branchId()).orElseThrow(
                        () -> new ResourceNotFoundException("Branch with id '%s' not found".formatted(request.branchId())));

        if (branch.getStatus() != BranchStatus.ACTIVE)
            throw new BusinessRuleException("Material copy can be created only for active branch");

        String inventoryNumber = normalize(request.inventoryNumber(), "Inventory number");
        String shelfLocation = normalize(request.shelfLocation(), "Shelf location");

        if (repository.existsByInventoryNumber(inventoryNumber))
            throw new DuplicateResourceException(
                    "Material copy with inventory number '%s' already exists".formatted(inventoryNumber));

        MaterialCopy copy = mapper.toEntity(new CreateMaterialCopyRequest(material.getId(), branch.getId(), inventoryNumber, shelfLocation),
                branch, material);

        MaterialCopy saved = repository.save(copy);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public MaterialCopyResponse getMaterialCopyById(Long id) {
        MaterialCopy copy = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Material copy with id '%s' not found".formatted(id)));

        return mapper.toResponse(copy);
    }

    @Override
    @Transactional(readOnly = true)
    public MaterialCopyResponse getByInventoryNumber(String inventoryNumber) {
        String normalizedInventoryNumber = normalize(inventoryNumber, "Inventory number");

        MaterialCopy copy = repository.findByInventoryNumber(normalizedInventoryNumber).orElseThrow(
                () -> new ResourceNotFoundException("Material copy with inventory number '%s' not found".formatted(normalizedInventoryNumber)));

        return mapper.toResponse(copy);
    }

    @Override
    @Transactional
    public MaterialCopyResponse update(Long id, UpdateMaterialCopyRequest request) {
        MaterialCopy copy = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Material copy with id '%s' not found".formatted(id)));

        if (copy.getStatus() == CopyStatus.REMOVED)
            throw new BusinessRuleException("Removed material copy cannot be updated");

        String shelfLocation = normalize(request.shelfLocation(), "Shelf location");

        mapper.updateEntity(new UpdateMaterialCopyRequest(shelfLocation), copy);

        MaterialCopy saved = repository.save(copy);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public MaterialCopyResponse changeStatus(Long id, ChangeMaterialCopyStatusRequest request) {
        MaterialCopy copy = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Material copy with id '%s' not found".formatted(id)));

        CopyStatus status = request.status();

        if (copy.getStatus() == status)
            return mapper.toResponse(copy);

        if (!isTransitionAllowed(copy.getStatus(), status))
            throw new BusinessRuleException("Material copy status transition from '%s' to '%s' is not allowed"
                            .formatted(copy.getStatus(), status));

        if (hasActiveOperations(copy.getId()))
            throw new ResourceInUseException("Material copy has active operations");

        if (status == CopyStatus.AVAILABLE) {
            if (copy.getMaterial().getStatus() != MaterialStatus.ACTIVE)
                throw new BusinessRuleException("Material copy can be made available only if material is active");

            if (copy.getBranch().getStatus() != BranchStatus.ACTIVE)
                throw new BusinessRuleException("Material copy can be made available only if branch is active");
        }

        copy.setStatus(status);

        MaterialCopy saved = repository.save(copy);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MaterialCopyResponse> search(Long materialId, Long branchId, CopyStatus status, Pageable pageable) {
        Page<MaterialCopy> copies;

        if (materialId == null && branchId == null && status == null)
            copies = repository.findAll(pageable);
        else if (materialId != null && branchId == null && status == null)
            copies = repository.findByMaterial_Id(materialId, pageable);
        else if (materialId == null && branchId != null && status == null)
            copies = repository.findByBranch_Id(branchId, pageable);
        else if (materialId == null && branchId == null)
            copies = repository.findByStatus(status, pageable);
        else if (materialId != null && branchId != null && status == null)
            copies = repository.findByMaterial_IdAndBranch_Id(materialId, branchId, pageable);
        else if (materialId != null && branchId == null)
            copies = repository.findByMaterial_IdAndStatus(materialId, status, pageable);
        else if (materialId == null)
            copies = repository.findByBranch_IdAndStatus(branchId, status, pageable);
        else
            copies = repository.findByMaterial_IdAndBranch_IdAndStatus(materialId, branchId, status, pageable);

        Page<MaterialCopyResponse> responses = copies.map(mapper::toResponse);
        return PageResponse.from(responses);
    }
}
