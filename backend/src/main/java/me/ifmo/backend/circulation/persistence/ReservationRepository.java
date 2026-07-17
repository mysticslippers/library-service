package me.ifmo.backend.circulation.persistence;

import me.ifmo.backend.circulation.domain.Reservation;
import me.ifmo.backend.circulation.domain.enums.ReservationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long>, JpaSpecificationExecutor<Reservation> {

    Page<Reservation> findByUser_Id(Long userId, Pageable pageable);

    Page<Reservation> findByUser_IdAndStatusIn(Long userId, Collection<ReservationStatus> statuses, Pageable pageable);

    boolean existsByUser_IdAndMaterial_IdAndStatusIn(Long userId, Long materialId, Collection<ReservationStatus> statuses);

    boolean existsByMaterial_IdAndStatusIn(Long materialId, Collection<ReservationStatus> statuses);

    boolean existsByBranch_IdAndStatusIn(Long branchId, Collection<ReservationStatus> statuses);

    boolean existsByBranch_Library_IdAndStatusIn(Long libraryId, Collection<ReservationStatus> statuses);

    Optional<Reservation> findByCopy_IdAndStatusIn(Long copyId, Collection<ReservationStatus> statuses);

    Long countByUser_IdAndStatusIn(Long userId, Collection<ReservationStatus> statuses);

}
