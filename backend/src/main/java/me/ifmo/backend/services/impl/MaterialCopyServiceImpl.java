package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.catalog.request.CreateMaterialCopyRequest;
import me.ifmo.backend.dto.catalog.response.MaterialCopyResponse;
import me.ifmo.backend.entities.Branch;
import me.ifmo.backend.entities.Material;
import me.ifmo.backend.entities.MaterialCopy;
import me.ifmo.backend.entities.enums.BranchStatus;
import me.ifmo.backend.entities.enums.LoanStatus;
import me.ifmo.backend.entities.enums.MaterialStatus;
import me.ifmo.backend.entities.enums.ReservationStatus;
import me.ifmo.backend.exceptions.domain.BusinessRuleException;
import me.ifmo.backend.exceptions.domain.DuplicateResourceException;
import me.ifmo.backend.exceptions.domain.ResourceNotFoundException;
import me.ifmo.backend.mappers.MaterialCopyMapper;
import me.ifmo.backend.repositories.*;
import me.ifmo.backend.services.MaterialCopyService;
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
}
