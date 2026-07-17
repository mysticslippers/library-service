package me.ifmo.backend.catalog.application.impl;

import me.ifmo.backend.catalog.domain.enums.CopyStatus;
import me.ifmo.backend.catalog.domain.enums.MaterialStatus;
import me.ifmo.backend.catalog.persistence.MaterialCopyRepository;
import me.ifmo.backend.catalog.persistence.MaterialRepository;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.catalog.web.request.ChangeMaterialCopyStatusRequest;
import me.ifmo.backend.catalog.web.request.CreateMaterialCopyRequest;
import me.ifmo.backend.catalog.web.request.UpdateMaterialCopyRequest;
import me.ifmo.backend.catalog.web.response.MaterialCopyResponse;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.library.domain.Branch;
import me.ifmo.backend.catalog.domain.Material;
import me.ifmo.backend.catalog.domain.MaterialCopy;
import me.ifmo.backend.entities.enums.*;
import me.ifmo.backend.library.domain.enums.BranchStatus;
import me.ifmo.backend.shared.error.BusinessRuleException;
import me.ifmo.backend.shared.error.DuplicateResourceException;
import me.ifmo.backend.shared.error.ResourceInUseException;
import me.ifmo.backend.shared.error.ResourceNotFoundException;
import me.ifmo.backend.catalog.mapper.MaterialCopyMapper;
import me.ifmo.backend.repositories.*;
import me.ifmo.backend.library.persistence.BranchRepository;
import me.ifmo.backend.catalog.application.MaterialCopyService;
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
    private final MaterialCopyMapper materialCopyMapper;
    
    private String normalize(String value, String fieldName) {
        if(fieldName.equals("Inventory number")){
            if (value == null || value.strip().isBlank())
                throw new BusinessRuleException("%s must not be blank".formatted(fieldName));

            return value.strip();
        } else {
            if (value == null)
                return null;

            String normalized = value.strip();
            return normalized.isBlank() ? null : normalized;
        }
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

    private Branch getActiveBranch(Long branchId) {
        Branch branch = branchRepository.findById(branchId).orElseThrow(
                () -> new ResourceNotFoundException("Branch with id '%s' not found".formatted(branchId)));

        if (branch.getStatus() != BranchStatus.ACTIVE)
            throw new BusinessRuleException("Material copy can be assigned only to active branch");

        return branch;
    }

    @Override
    @Transactional
    public MaterialCopyResponse create(CreateMaterialCopyRequest request) {
        Material material = materialRepository.findById(request.materialId()).orElseThrow(
                () -> new ResourceNotFoundException("Material with id '%s' not found".formatted(request.materialId())));

        if (material.getStatus() != MaterialStatus.ACTIVE)
            throw new BusinessRuleException("Material copy can be created only for active material");

        Branch branch = getActiveBranch(request.branchId());

        String inventoryNumber = normalize(request.inventoryNumber(), "Inventory number");
        String shelfLocation = normalize(request.shelfLocation(), "Shelf location");

        if (repository.existsByInventoryNumber(inventoryNumber))
            throw new DuplicateResourceException(
                    "Material copy with inventory number '%s' already exists".formatted(inventoryNumber));

        MaterialCopy copy = materialCopyMapper.toEntity(new CreateMaterialCopyRequest(material.getId(), branch.getId(), inventoryNumber, shelfLocation),
                branch, material);

        MaterialCopy saved = repository.save(copy);
        return materialCopyMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public MaterialCopyResponse getMaterialCopyById(Long id) {
        MaterialCopy copy = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Material copy with id '%s' not found".formatted(id)));

        return materialCopyMapper.toResponse(copy);
    }

    @Override
    @Transactional(readOnly = true)
    public MaterialCopyResponse getByInventoryNumber(String inventoryNumber) {
        String normalizedInventoryNumber = normalize(inventoryNumber, "Inventory number");

        MaterialCopy copy = repository.findByInventoryNumber(normalizedInventoryNumber).orElseThrow(
                () -> new ResourceNotFoundException("Material copy with inventory number '%s' not found".formatted(normalizedInventoryNumber)));

        return materialCopyMapper.toResponse(copy);
    }

    @Override
    @Transactional
    public MaterialCopyResponse update(Long id, UpdateMaterialCopyRequest request) {
        MaterialCopy copy = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Material copy with id '%s' not found".formatted(id)));

        if (copy.getStatus() == CopyStatus.REMOVED)
            throw new BusinessRuleException("Removed material copy cannot be updated");

        String inventoryNumber = request.inventoryNumber() != null
                ? normalize(request.inventoryNumber(), "Inventory number")
                : null;
        String shelfLocation = normalize(request.shelfLocation(), "Shelf location");

        if (inventoryNumber != null && !inventoryNumber.equals(copy.getInventoryNumber())) {
            if (repository.existsByInventoryNumber(inventoryNumber))
                throw new DuplicateResourceException(
                        "Material copy with inventory number '%s' already exists".formatted(inventoryNumber));

            copy.setInventoryNumber(inventoryNumber);
        }

        if (request.branchId() != null && !request.branchId().equals(copy.getBranch().getId())) {
            if (hasActiveOperations(copy.getId()))
                throw new ResourceInUseException("Material copy has active operations");

            copy.setBranch(getActiveBranch(request.branchId()));
        }

        if (request.shelfLocation() != null)
            copy.setShelfLocation(shelfLocation);

        MaterialCopy saved = repository.save(copy);
        return materialCopyMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public MaterialCopyResponse changeStatus(Long id, ChangeMaterialCopyStatusRequest request) {
        MaterialCopy copy = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Material copy with id '%s' not found".formatted(id)));

        CopyStatus status = request.status();

        if (copy.getStatus() == status)
            return materialCopyMapper.toResponse(copy);

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
        return materialCopyMapper.toResponse(saved);
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

        Page<MaterialCopyResponse> responses = copies.map(materialCopyMapper::toResponse);
        return PageResponse.from(responses);
    }
}
