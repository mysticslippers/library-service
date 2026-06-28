package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.Reservation;
import me.ifmo.backend.entities.enums.ReservationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    Page<Reservation> findByUser_Id(Long userId, Pageable pageable);

    Page<Reservation> findByUser_IdAndStatus(Long userId, ReservationStatus status, Pageable pageable);

    Page<Reservation> findByUser_IdAndStatusIn(Long userId, Collection<ReservationStatus> statuses, Pageable pageable);

    boolean existsByUser_IdAndMaterial_IdAndStatusIn(Long userId, Long materialId, Collection<ReservationStatus> statuses);

    Page<Reservation> findByMaterial_IdAndStatus(Long materialId, ReservationStatus status, Pageable pageable);

    Page<Reservation> findByBranch_IdAndStatus(Long branchId, ReservationStatus status, Pageable pageable);

    boolean existsByBranch_IdAndStatusIn(Long branchId, Collection<ReservationStatus> statuses);

    boolean existsByBranch_Library_IdAndStatusIn(Long libraryId, Collection<ReservationStatus> statuses);

    Optional<Reservation> findByCopy_IdAndStatusIn(Long copyId, Collection<ReservationStatus> statuses);

    List<Reservation> findByStatusAndExpiresAtBefore(ReservationStatus status, LocalDateTime expiresAt);

    Long countByUser_IdAndStatusIn(Long userId, Collection<ReservationStatus> statuses);
}
