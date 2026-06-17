package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.Reservation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    Page<Reservation> findByUser_Id(Long userId, Pageable pageable);
}
