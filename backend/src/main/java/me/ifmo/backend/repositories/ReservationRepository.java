package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.Reservation;
import me.ifmo.backend.entities.enums.ReservationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    Page<Reservation> findByUser_Id(Long userId, Pageable pageable);

    Page<Reservation> findByUser_IdAndStatus(Long userId, ReservationStatus status, Pageable pageable);

    Page<Reservation> findByUser_IdAndStatusIn(Long userId, Collection<ReservationStatus> statuses, Pageable pageable);

    Page<Reservation> findByMaterial_IdAndStatus(Long materialId, ReservationStatus status, Pageable pageable);

    Page<Reservation> findByBranch_IdAndStatus(Long branchId, ReservationStatus status, Pageable pageable);
}
