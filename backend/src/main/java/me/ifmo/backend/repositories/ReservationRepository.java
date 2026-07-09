package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.Reservation;
import me.ifmo.backend.entities.enums.ReservationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    Page<Reservation> findByUser_Id(Long userId, Pageable pageable);

    Page<Reservation> findByUser_IdAndStatus(Long userId, ReservationStatus status, Pageable pageable);

    Page<Reservation> findByUser_IdAndStatusIn(Long userId, Collection<ReservationStatus> statuses, Pageable pageable);

    boolean existsByUser_IdAndMaterial_IdAndStatusIn(Long userId, Long materialId, Collection<ReservationStatus> statuses);

    boolean existsByMaterial_IdAndStatusIn(Long materialId, Collection<ReservationStatus> statuses);

    Page<Reservation> findByMaterial_IdAndStatus(Long materialId, ReservationStatus status, Pageable pageable);

    Page<Reservation> findByBranch_IdAndStatus(Long branchId, ReservationStatus status, Pageable pageable);

    boolean existsByBranch_IdAndStatusIn(Long branchId, Collection<ReservationStatus> statuses);

    boolean existsByBranch_Library_IdAndStatusIn(Long libraryId, Collection<ReservationStatus> statuses);

    Optional<Reservation> findByCopy_IdAndStatusIn(Long copyId, Collection<ReservationStatus> statuses);

    List<Reservation> findByStatusAndExpiresAtBefore(ReservationStatus status, LocalDateTime expiresAt);

    Long countByUser_IdAndStatusIn(Long userId, Collection<ReservationStatus> statuses);

    @Query("""
       SELECT reservation FROM Reservation reservation
           WHERE (:userId IS NULL OR reservation.user.id = :userId)
             AND (:materialId IS NULL OR reservation.material.id = :materialId)
             AND (:copyId IS NULL OR reservation.copy.id = :copyId)
             AND (:branchId IS NULL OR reservation.branch.id = :branchId)
             AND (:status IS NULL OR reservation.status = :status)
             AND (:createdFrom IS NULL OR reservation.createdAt >= :createdFrom)
             AND (:createdTo IS NULL OR reservation.createdAt <= :createdTo)
             AND (:expiresBefore IS NULL OR reservation.expiresAt <= :expiresBefore)
             AND (:query IS NULL OR :query = ''
                  OR lower(reservation.material.title) LIKE lower(concat('%', :query, '%'))
                  OR lower(reservation.user.email) LIKE lower(concat('%', :query, '%'))
                  OR lower(reservation.user.firstName) LIKE lower(concat('%', :query, '%'))
                  OR lower(reservation.user.lastName) LIKE lower(concat('%', :query, '%'))
                  OR reservation.copy.inventoryNumber LIKE concat('%', :query, '%'))
    """)
    Page<Reservation> search(@Param("userId") Long userId,
                             @Param("materialId") Long materialId,
                             @Param("copyId") Long copyId,
                             @Param("branchId") Long branchId,
                             @Param("status") ReservationStatus status,
                             @Param("createdFrom") LocalDateTime createdFrom,
                             @Param("createdTo") LocalDateTime createdTo,
                             @Param("expiresBefore") LocalDateTime expiresBefore,
                             @Param("query") String query,
                             Pageable pageable);
}
