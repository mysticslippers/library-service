package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.library.request.CreateBranchRequest;
import me.ifmo.backend.dto.library.response.BranchResponse;
import me.ifmo.backend.entities.Branch;
import me.ifmo.backend.entities.Library;
import me.ifmo.backend.entities.enums.LibraryStatus;
import me.ifmo.backend.entities.enums.LoanStatus;
import me.ifmo.backend.entities.enums.ReservationStatus;
import me.ifmo.backend.exceptions.domain.BusinessRuleException;
import me.ifmo.backend.exceptions.domain.DuplicateResourceException;
import me.ifmo.backend.exceptions.domain.ResourceNotFoundException;
import me.ifmo.backend.mappers.BranchMapper;
import me.ifmo.backend.repositories.*;
import me.ifmo.backend.services.BranchService;
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
}
