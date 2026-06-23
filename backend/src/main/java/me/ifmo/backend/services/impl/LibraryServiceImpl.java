package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.library.request.ChangeLibraryStatusRequest;
import me.ifmo.backend.dto.library.request.CreateLibraryRequest;
import me.ifmo.backend.dto.library.request.UpdateLibraryRequest;
import me.ifmo.backend.dto.library.response.LibraryResponse;
import me.ifmo.backend.entities.Library;
import me.ifmo.backend.entities.enums.BranchStatus;
import me.ifmo.backend.entities.enums.LibraryStatus;
import me.ifmo.backend.entities.enums.LoanStatus;
import me.ifmo.backend.entities.enums.ReservationStatus;
import me.ifmo.backend.exceptions.domain.BusinessRuleException;
import me.ifmo.backend.exceptions.domain.DuplicateResourceException;
import me.ifmo.backend.exceptions.domain.ResourceInUseException;
import me.ifmo.backend.exceptions.domain.ResourceNotFoundException;
import me.ifmo.backend.mappers.LibraryMapper;
import me.ifmo.backend.repositories.BranchRepository;
import me.ifmo.backend.repositories.LibraryRepository;
import me.ifmo.backend.repositories.LoanRepository;
import me.ifmo.backend.repositories.ReservationRepository;
import me.ifmo.backend.services.LibraryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LibraryServiceImpl implements LibraryService {

    private static final Set<LoanStatus> BLOCKING_LOAN_STATUSES =
            Set.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE, LoanStatus.LOST);

    private static final Set<ReservationStatus> BLOCKING_RESERVATION_STATUSES =
            Set.of(ReservationStatus.ACTIVE, ReservationStatus.READY_FOR_PICKUP);

    private final LibraryRepository repository;
    private final BranchRepository branchRepository;
    private final LoanRepository loanRepository;
    private final ReservationRepository reservationRepository;
    private final LibraryMapper mapper;

    private String normalize(String value, String fieldName) {
        if (value == null || value.strip().isBlank())
            throw new BusinessRuleException("%s must not be blank".formatted(fieldName));

        return value.strip();
    }

    private boolean isTransitionAllowed(LibraryStatus current, LibraryStatus target){
        return switch (current) {
            case ACTIVE ->
                    target == LibraryStatus.INACTIVE || target == LibraryStatus.SUSPENDED;
            case INACTIVE ->
                    target == LibraryStatus.ACTIVE || target == LibraryStatus.ARCHIVED;
            case SUSPENDED ->
                    target == LibraryStatus.ACTIVE || target == LibraryStatus.INACTIVE;
            case ARCHIVED -> false;
        };
    }

    @Override
    @Transactional
    public LibraryResponse create(CreateLibraryRequest request) {
        String name = normalize(request.name(), "Library name");
        String code = normalize(request.code(), "Library code").toUpperCase(Locale.ROOT);

        if (repository.existsByCode(code))
            throw new DuplicateResourceException("Library with code '%s' already exists".formatted(code));

        Library library = mapper.toEntity(new CreateLibraryRequest(code, name));

        Library saved = repository.save(library);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public LibraryResponse getLibraryById(Long id) {
        Library library = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Library with id '%s' not found".formatted(id)));

        return mapper.toResponse(library);
    }

    @Override
    @Transactional(readOnly = true)
    public LibraryResponse getByCode(String code) {
        String normalizedCode = normalize(code, "Library code").toUpperCase(Locale.ROOT);

        Library library = repository.findByCode(normalizedCode)
                .orElseThrow(() -> new ResourceNotFoundException("Library with code '%s' not found".formatted(normalizedCode)));

        return mapper.toResponse(library);
    }

    @Override
    @Transactional
    public LibraryResponse update(Long id, UpdateLibraryRequest request) {
        Library library = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Library with id '%s' not found".formatted(id)));

        if (library.getStatus() == LibraryStatus.ARCHIVED)
            throw new BusinessRuleException("Archived library cannot be updated");

        String name = (request.name() != null) ? normalize(request.name(), "Library name") : null;

        String code = (request.code() != null) ? normalize(request.code(), "Library code").toUpperCase(Locale.ROOT)
                : null;

        if (code != null && !code.equals(library.getCode()) && repository.existsByCode(code))
            throw new DuplicateResourceException("Library with code '%s' already exists".formatted(code));

        mapper.updateEntity(new UpdateLibraryRequest(code, name), library);

        Library saved = repository.save(library);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public LibraryResponse changeStatus(Long id, ChangeLibraryStatusRequest request) {
        Library library = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Library with id '%s' not found".formatted(id)));

        LibraryStatus status = request.status();

        if (library.getStatus() == status)
            return mapper.toResponse(library);

        if (!isTransitionAllowed(library.getStatus(), status))
            throw new BusinessRuleException(
                    "Library status transition from '%s' to '%s' is not allowed".formatted(library.getStatus(), status));

        if (status == LibraryStatus.ARCHIVED){
            if (branchRepository.existsByLibrary_IdAndStatusNot(id, BranchStatus.ARCHIVED))
                throw new ResourceInUseException("Library has non-archived branches");

            if (loanRepository.existsByBranch_Library_IdAndStatusIn(
                    id, BLOCKING_LOAN_STATUSES))
                throw new ResourceInUseException("Library has active or unresolved loans");

            if (reservationRepository.existsByBranch_Library_IdAndStatusIn(id, BLOCKING_RESERVATION_STATUSES))
                throw new ResourceInUseException("Library has active reservations");
        }

        library.setStatus(status);
        Library saved = repository.save(library);
        return mapper.toResponse(saved);
    }
}
