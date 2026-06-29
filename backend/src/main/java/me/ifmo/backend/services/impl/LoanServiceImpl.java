package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.entities.LibraryRule;
import me.ifmo.backend.entities.User;
import me.ifmo.backend.entities.enums.LibraryRuleStatus;
import me.ifmo.backend.entities.enums.LoanStatus;
import me.ifmo.backend.entities.enums.UserStatus;
import me.ifmo.backend.exceptions.domain.BusinessRuleException;
import me.ifmo.backend.exceptions.domain.ResourceNotFoundException;
import me.ifmo.backend.mappers.LoanMapper;
import me.ifmo.backend.repositories.*;
import me.ifmo.backend.services.LoanService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LoanServiceImpl implements LoanService {

    private static final Set<LoanStatus> BLOCKING_LOAN_STATUSES =
            Set.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE, LoanStatus.LOST);

    private final LoanRepository repository;
    private final UserRepository userRepository;
    private final MaterialCopyRepository materialCopyRepository;
    private final BranchRepository branchRepository;
    private final ReservationRepository reservationRepository;
    private final LibraryRuleRepository libraryRuleRepository;
    private final LoanMapper mapper;

    private LibraryRule getActualRule(Long branchId) {
        return libraryRuleRepository.findActualByBranchIdAndStatus(branchId, LibraryRuleStatus.ACTIVE, LocalDateTime.now())
                .orElseThrow(
                        () -> new ResourceNotFoundException("Actual library rule for branch with id '%s' not found".formatted(branchId)));
    }

    private User findUser(Long id, String fieldName) {
        User user = userRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("%s with id '%s' not found".formatted(fieldName, id)));

        if (user.getStatus() != UserStatus.ACTIVE)
            throw new BusinessRuleException("%s must be active".formatted(fieldName));

        return user;
    }
}
