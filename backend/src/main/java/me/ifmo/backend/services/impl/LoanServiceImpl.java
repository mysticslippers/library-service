package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.services.LoanService;
import org.springframework.stereotype.Service;

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
}
