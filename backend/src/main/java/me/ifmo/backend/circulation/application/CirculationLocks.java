package me.ifmo.backend.circulation.application;

import lombok.RequiredArgsConstructor;
import jakarta.persistence.EntityManager;
import me.ifmo.backend.catalog.persistence.MaterialCopyRepository;
import me.ifmo.backend.circulation.domain.Loan;
import me.ifmo.backend.circulation.domain.Reservation;
import me.ifmo.backend.circulation.persistence.LoanRepository;
import me.ifmo.backend.circulation.persistence.ReservationRepository;
import me.ifmo.backend.shared.exceptions.ResourceNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class CirculationLocks {

    private final MaterialCopyRepository copies;
    private final LoanRepository loans;
    private final ReservationRepository reservations;
    private final EntityManager entityManager;

    public Loan loan(Long id) {
        Long copyId = loans.findCopyIdById(id).orElseThrow(
                () -> new ResourceNotFoundException("Loan with id '%s' not found".formatted(id)));
        lockCopy(copyId);

        Loan loan = loans.findByIdForUpdate(id).orElseThrow(
                () -> new ResourceNotFoundException("Loan with id '%s' not found".formatted(id)));
        entityManager.refresh(loan);
        return loan;
    }

    public Reservation reservation(Long id) {
        Long copyId = reservations.findCopyIdById(id).orElseThrow(
                () -> new ResourceNotFoundException("Reservation with id '%s' not found".formatted(id)));
        lockCopy(copyId);

        Reservation reservation = reservations.findByIdForUpdate(id).orElseThrow(
                () -> new ResourceNotFoundException("Reservation with id '%s' not found".formatted(id)));
        entityManager.refresh(reservation);
        return reservation;
    }

    private void lockCopy(Long copyId) {
        var copy = copies.findByIdForUpdate(copyId).orElseThrow(
                () -> new ResourceNotFoundException("Material copy with id '%s' not found".formatted(copyId)));

        entityManager.refresh(copy);
    }
}
