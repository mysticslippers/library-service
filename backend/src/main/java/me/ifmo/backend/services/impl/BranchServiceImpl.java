package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.common.response.PageResponse;
import me.ifmo.backend.dto.library.request.ChangeBranchStatusRequest;
import me.ifmo.backend.dto.library.request.CreateBranchRequest;
import me.ifmo.backend.dto.library.request.UpdateBranchRequest;
import me.ifmo.backend.dto.library.response.BranchResponse;
import me.ifmo.backend.entities.Branch;
import me.ifmo.backend.entities.Library;
import me.ifmo.backend.entities.enums.*;
import me.ifmo.backend.exceptions.domain.BusinessRuleException;
import me.ifmo.backend.exceptions.domain.DuplicateResourceException;
import me.ifmo.backend.exceptions.domain.ResourceInUseException;
import me.ifmo.backend.exceptions.domain.ResourceNotFoundException;
import me.ifmo.backend.mappers.BranchMapper;
import me.ifmo.backend.repositories.*;
import me.ifmo.backend.services.BranchService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class BranchServiceImpl implements BranchService {

    private static final Set<LoanStatus> BLOCKING_LOAN_STATUSES =
            Set.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE, LoanStatus.LOST);

    private static final Set<ReservationStatus> BLOCKING_RESERVATION_STATUSES =
            Set.of(ReservationStatus.ACTIVE, ReservationStatus.READY_FOR_PICKUP);

    private final BranchRepository repository;
    private final LibraryRepository libraryRepository;
    private final LoanRepository loanRepository;
    private final ReservationRepository reservationRepository;
    private final MaterialCopyRepository materialCopyRepository;
    private final BranchMapper mapper;

    private String normalize(String value, String fieldName) {
        if (value == null || value.strip().isBlank()) {
            throw new BusinessRuleException("%s must not be blank".formatted(fieldName));
        }

        return value.strip();
    }

    private boolean isTransitionAllowed(BranchStatus current, BranchStatus target) {
        return switch (current) {
            case ACTIVE ->
                    target == BranchStatus.TEMPORARILY_UNAVAILABLE || target == BranchStatus.DISABLED;
            case TEMPORARILY_UNAVAILABLE ->
                    target == BranchStatus.ACTIVE || target == BranchStatus.DISABLED;
            case DISABLED ->
                    target == BranchStatus.ACTIVE || target == BranchStatus.ARCHIVED;
            case ARCHIVED -> false;
        };
    }

    @Override
    @Transactional
    public BranchResponse create(CreateBranchRequest request){
        Library library = libraryRepository.findById(request.libraryId())
                .orElseThrow(() -> new ResourceNotFoundException("Library with id '%s' not found"
                        .formatted(request.libraryId())));

        if (library.getStatus() != LibraryStatus.ACTIVE)
            throw new BusinessRuleException("Branch can be created only for active library");

        String name = normalize(request.name(), "Branch name");
        if (repository.existsByLibrary_IdAndNameIgnoreCase(library.getId(), name))
            throw new DuplicateResourceException("Branch with name '%s' already exists in library with id '%s'"
                            .formatted(name, library.getId()));

        Branch branch = mapper.toEntity(new CreateBranchRequest(library.getId(), name, request.address()), library);

        Branch saved = repository.save(branch);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public BranchResponse getBranchById(Long id){
        Branch branch = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Branch with id '%s' not found".formatted(id))
        );

        return mapper.toResponse(branch);
    }

    @Override
    @Transactional
    public BranchResponse update(Long id, UpdateBranchRequest request) {
        Branch branch = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Branch with id '%s' not found".formatted(id)));

        if (branch.getStatus() == BranchStatus.ARCHIVED)
            throw new BusinessRuleException("Archived branch cannot be updated");

        String name = request.name() != null ? normalize(request.name(), "Branch name") : null;

        if (name != null && !name.equalsIgnoreCase(branch.getName())
                && repository.existsByLibrary_IdAndNameIgnoreCase(branch.getLibrary().getId(), name))
            throw new DuplicateResourceException("Branch with name '%s' already exists in library with id '%s'"
                            .formatted(name, branch.getLibrary().getId()));

        mapper.updateEntity(new UpdateBranchRequest(name, request.address()), branch);

        Branch saved = repository.save(branch);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public BranchResponse changeStatus(Long id, ChangeBranchStatusRequest request) {
        Branch branch = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Branch with id '%s' not found".formatted(id)));

        BranchStatus status = request.status();

        if (branch.getStatus() == status)
            return mapper.toResponse(branch);

        if (!isTransitionAllowed(branch.getStatus(), status))
            throw new BusinessRuleException("Branch status transition from '%s' to '%s' is not allowed"
                            .formatted(branch.getStatus(), status));

        if (status == BranchStatus.ACTIVE && branch.getLibrary().getStatus() != LibraryStatus.ACTIVE)
            throw new BusinessRuleException("Branch can be activated only if parent library is active");

        if (status == BranchStatus.ARCHIVED) {
            if (loanRepository.existsByBranch_IdAndStatusIn(id, BLOCKING_LOAN_STATUSES))
                throw new ResourceInUseException("Branch has active or unresolved loans");

            if (reservationRepository.existsByBranch_IdAndStatusIn(id, BLOCKING_RESERVATION_STATUSES))
                throw new ResourceInUseException("Branch has active reservations");

            if (materialCopyRepository.existsByBranch_IdAndStatusNot(id, CopyStatus.REMOVED))
                throw new ResourceInUseException("Branch has non-removed material copies");
        }

        branch.setStatus(status);

        Branch saved = repository.save(branch);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BranchResponse> search(Long libraryId, BranchStatus status, Pageable pageable) {
        Page<Branch> branches;

        if (libraryId == null && status == null)
            branches = repository.findAll(pageable);
        else if (libraryId == null)
            branches = repository.findByStatus(status, pageable);
        else if (status == null)
            branches = repository.findByLibrary_Id(libraryId, pageable);
        else
            branches = repository.findByLibrary_IdAndStatus(libraryId, status, pageable);

        Page<BranchResponse> responses = branches.map(mapper::toResponse);

        return PageResponse.from(responses);
    }
}
