package me.ifmo.backend.circulation.mapper;

import me.ifmo.backend.circulation.domain.Reservation;

import me.ifmo.backend.catalog.domain.Material;
import me.ifmo.backend.catalog.domain.MaterialCopy;
import me.ifmo.backend.catalog.mapper.MaterialCopyMapper;

import me.ifmo.backend.user.domain.User;
import me.ifmo.backend.user.mapper.UserMapper;

import me.ifmo.backend.library.mapper.BranchMapper;

import me.ifmo.backend.catalog.web.response.MaterialShortResponse;
import me.ifmo.backend.circulation.web.response.ReservationResponse;
import me.ifmo.backend.library.domain.Branch;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDateTime;

@Mapper(uses = {UserMapper.class, MaterialCopyMapper.class, BranchMapper.class})
public interface ReservationMapper {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "user", source = "user")
    @Mapping(target = "material", source = "material")
    @Mapping(target = "copy", source = "copy")
    @Mapping(target = "branch", source = "branch")
    @Mapping(target = "expiresAt", source = "expiresAt")
    Reservation toEntity(User user, Material material, MaterialCopy copy, Branch branch, LocalDateTime expiresAt);

    @Mapping(target = "id", source = "reservation.id")
    @Mapping(target = "material", source = "materialResponse")
    @Mapping(target = "status", source = "reservation.status")
    ReservationResponse toResponse(Reservation reservation, MaterialShortResponse materialResponse);
}
