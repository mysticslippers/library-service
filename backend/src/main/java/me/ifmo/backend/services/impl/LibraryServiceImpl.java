package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.library.request.CreateLibraryRequest;
import me.ifmo.backend.dto.library.response.LibraryResponse;
import me.ifmo.backend.entities.Library;
import me.ifmo.backend.entities.enums.LoanStatus;
import me.ifmo.backend.entities.enums.ReservationStatus;
import me.ifmo.backend.exceptions.domain.BusinessRuleException;
import me.ifmo.backend.exceptions.domain.DuplicateResourceException;
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
}
