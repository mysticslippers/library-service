package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.entities.enums.NotificationStatus;
import me.ifmo.backend.exceptions.domain.BusinessRuleException;
import me.ifmo.backend.mappers.NotificationMapper;
import me.ifmo.backend.repositories.*;
import me.ifmo.backend.services.NotificationService;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final Set<NotificationStatus> FINAL_STATUSES =
            Set.of(NotificationStatus.DELIVERED, NotificationStatus.CANCELLED);

    private final NotificationRepository repository;
    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;
    private final LoanRepository loanRepository;
    private final FineRepository fineRepository;
    private final NotificationMapper mapper;

    private String normalize(String value, String fieldName) {
        if(fieldName.equals("Subject")) {
            if (value == null || value.strip().isBlank())
                return null;
        } else {
            if (value == null || value.strip().isBlank())
                throw new BusinessRuleException("%s must not be blank".formatted(fieldName));
        }
        return value.strip();
    }

    private boolean isTransitionAllowed(NotificationStatus current, NotificationStatus target) {
        if (current == target)
            return true;

        if (FINAL_STATUSES.contains(current))
            return false;

        return switch (current) {
            case PLANNED, FAILED -> target == NotificationStatus.PENDING || target == NotificationStatus.CANCELLED;
            case PENDING -> target == NotificationStatus.SENT
                    || target == NotificationStatus.FAILED
                    || target == NotificationStatus.CANCELLED;
            case SENT -> target == NotificationStatus.DELIVERED
                    || target == NotificationStatus.FAILED;
            case DELIVERED, CANCELLED -> false;
        };
    }
}
