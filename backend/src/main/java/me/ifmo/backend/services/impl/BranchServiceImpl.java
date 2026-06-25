package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.entities.enums.LoanStatus;
import me.ifmo.backend.entities.enums.ReservationStatus;
import me.ifmo.backend.mappers.BranchMapper;
import me.ifmo.backend.repositories.*;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class BranchServiceImpl {

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
}
