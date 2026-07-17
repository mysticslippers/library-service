package me.ifmo.backend.library.application.impl;

import me.ifmo.backend.circulation.domain.enums.LoanStatus;
import me.ifmo.backend.circulation.domain.enums.ReservationStatus;
import me.ifmo.backend.circulation.persistence.LoanRepository;
import me.ifmo.backend.circulation.persistence.ReservationRepository;

import me.ifmo.backend.catalog.domain.enums.CopyStatus;
import me.ifmo.backend.catalog.persistence.MaterialCopyRepository;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.library.web.request.BranchAddressRequest;
import me.ifmo.backend.library.web.request.ChangeBranchStatusRequest;
import me.ifmo.backend.library.web.request.CreateBranchRequest;
import me.ifmo.backend.library.web.request.UpdateBranchRequest;
import me.ifmo.backend.library.web.response.BranchResponse;
import me.ifmo.backend.library.domain.Branch;
import me.ifmo.backend.library.domain.Library;
import me.ifmo.backend.library.domain.enums.BranchStatus;
import me.ifmo.backend.library.domain.enums.LibraryStatus;
import me.ifmo.backend.shared.error.BusinessRuleException;
import me.ifmo.backend.shared.error.DuplicateResourceException;
import me.ifmo.backend.shared.error.ResourceInUseException;
import me.ifmo.backend.shared.error.ResourceNotFoundException;
import me.ifmo.backend.library.mapper.BranchMapper;
import me.ifmo.backend.library.persistence.BranchRepository;
import me.ifmo.backend.library.persistence.LibraryRepository;
import me.ifmo.backend.library.application.BranchService;
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
    private final BranchMapper branchMapper;

    private String normalize(String value, String fieldName) {
        if (value == null || value.strip().isBlank())
            throw new BusinessRuleException("%s must not be blank".formatted(fieldName));

        return value.strip();
    }

    private BranchAddressRequest normalizeAddress(BranchAddressRequest address, boolean required) {
        if (address == null) {
            if (required)
                throw new BusinessRuleException("Branch address must not be null");

            return null;
        }

        return new BranchAddressRequest(
                normalize(address.city(), "Branch address city"),
                normalize(address.street(), "Branch address street"),
                normalize(address.building(), "Branch address building"));
    }

    private boolean isTransitionAllowed(BranchStatus current, BranchStatus target) {
        return switch (current) {
            case ACTIVE ->
                    target == BranchStatus.TEMPORARILY_UNAVAILABLE || target == BranchStatus.DISABLED
                            || target == BranchStatus.ARCHIVED;
            case TEMPORARILY_UNAVAILABLE ->
                    target == BranchStatus.ACTIVE || target == BranchStatus.DISABLED
                            || target == BranchStatus.ARCHIVED;
            case DISABLED ->
                    target == BranchStatus.ACTIVE || target == BranchStatus.TEMPORARILY_UNAVAILABLE
                            || target == BranchStatus.ARCHIVED;
            case ARCHIVED ->
                    target == BranchStatus.ACTIVE || target == BranchStatus.DISABLED;
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

        BranchAddressRequest address = normalizeAddress(request.address(), true);
        Branch branch = branchMapper.toEntity(new CreateBranchRequest(library.getId(), name, address), library);

        Branch saved = repository.save(branch);
        return branchMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public BranchResponse getBranchById(Long id){
        Branch branch = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Branch with id '%s' not found".formatted(id))
        );

        return branchMapper.toResponse(branch);
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

        BranchAddressRequest address = normalizeAddress(request.address(), false);
        branchMapper.updateEntity(new UpdateBranchRequest(name, address), branch);

        Branch saved = repository.save(branch);
        return branchMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public BranchResponse changeStatus(Long id, ChangeBranchStatusRequest request) {
        Branch branch = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Branch with id '%s' not found".formatted(id)));

        BranchStatus status = request.status();

        if (branch.getStatus() == status)
            return branchMapper.toResponse(branch);

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
        return branchMapper.toResponse(saved);
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

        Page<BranchResponse> responses = branches.map(branchMapper::toResponse);

        return PageResponse.from(responses);
    }
}
