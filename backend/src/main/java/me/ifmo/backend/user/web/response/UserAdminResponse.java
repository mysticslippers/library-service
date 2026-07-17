package me.ifmo.backend.user.web.response;

import me.ifmo.backend.circulation.web.response.LoanResponse;
import me.ifmo.backend.circulation.web.response.ReservationResponse;
import me.ifmo.backend.fine.web.response.FineResponse;
import me.ifmo.backend.user.domain.enums.UserStatus;

import java.time.LocalDateTime;
import java.util.List;

public record UserAdminResponse(
        Long id,
        String email,
        String phone,
        String firstName,
        String lastName,
        String middleName,
        UserStatus status,
        Long homeBranchId,
        String homeBranchName,
        LocalDateTime registeredAt,
        LocalDateTime activatedAt,
        LocalDateTime lastLoginAt,
        LocalDateTime lockedUntil,
        List<RoleResponse> roles,
        UserBlockResponse activeBlock,
        List<UserWarningResponse> activeWarnings,
        List<LoanResponse> activeLoans,
        List<ReservationResponse> activeReservations,
        List<FineResponse> activeFines
) {
}
