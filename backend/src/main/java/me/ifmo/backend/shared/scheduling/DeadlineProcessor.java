package me.ifmo.backend.shared.scheduling;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.ifmo.backend.circulation.application.impl.LoanServiceImpl;
import me.ifmo.backend.circulation.application.impl.ReservationServiceImpl;
import me.ifmo.backend.circulation.domain.enums.LoanStatus;
import me.ifmo.backend.circulation.domain.enums.ReservationStatus;
import me.ifmo.backend.circulation.persistence.LoanRepository;
import me.ifmo.backend.circulation.persistence.ReservationRepository;
import me.ifmo.backend.user.application.UserBlockExpiration;
import me.ifmo.backend.user.application.impl.UserWarningServiceImpl;
import me.ifmo.backend.user.domain.enums.UserBlockStatus;
import me.ifmo.backend.user.domain.enums.UserWarningStatus;
import me.ifmo.backend.user.persistence.UserBlockRepository;
import me.ifmo.backend.user.persistence.UserWarningRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeadlineProcessor {

    private final ReservationRepository reservations;
    private final LoanRepository loans;
    private final UserBlockRepository blocks;
    private final UserWarningRepository warnings;
    private final ReservationServiceImpl reservationService;
    private final LoanServiceImpl loanService;
    private final UserBlockExpiration blockExpiration;
    private final UserWarningServiceImpl warningService;
    private final DeadlineProperties properties;
    private final Clock clock;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int processBatch() {
        LocalDateTime now = LocalDateTime.now(clock);
        PageRequest page = PageRequest.of(0, properties.batchSize());
        int changed = process("reservation", () -> reservations.findDueIds(
                Set.of(ReservationStatus.ACTIVE, ReservationStatus.READY_FOR_PICKUP), now, page),
                reservationService::expireDue);

        changed += process("loan", () -> loans.findDueIds(LoanStatus.ACTIVE, now, page), loanService::markOverdueIfDue);
        changed += process("user block", () -> blocks.findDueIds(UserBlockStatus.ACTIVE, now, page), blockExpiration::expireDue);
        changed += process("user warning", () -> warnings.findDueIds(UserWarningStatus.ACTIVE, now, page), warningService::expireDue);
        return changed;
    }

    private int process(String type, Supplier<List<Long>> candidates, Predicate<Long> transition) {
        List<Long> ids;
        try {
            ids = candidates.get();
        } catch (RuntimeException error) {
            log.warn("Cannot select {} deadlines: {}", type, error.getClass().getSimpleName());
            return 0;
        }

        int changed = 0;
        for (Long id : ids) {
            try {
                if (transition.test(id))
                    changed++;
            } catch (RuntimeException error) {
                log.warn("Cannot process {} deadline for id {}: {}", type, id, error.getClass().getSimpleName());
            }
        }
        return changed;
    }
}
