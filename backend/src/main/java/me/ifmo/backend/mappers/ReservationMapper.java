package me.ifmo.backend.mappers;

import me.ifmo.backend.entities.*;
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
}
