package me.ifmo.backend.user.persistence;

import me.ifmo.backend.user.domain.UserWarning;
import me.ifmo.backend.user.domain.enums.UserWarningStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UserWarningRepository extends JpaRepository<UserWarning, Long>, JpaSpecificationExecutor<UserWarning> {

    Page<UserWarning> findByUser_Id(Long userId, Pageable pageable);

    Page<UserWarning> findByUser_IdAndStatus(Long userId, UserWarningStatus status, Pageable pageable);

    Page<UserWarning> findByStatus(UserWarningStatus status, Pageable pageable);

}
