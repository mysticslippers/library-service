package me.ifmo.backend.mappers;

import me.ifmo.backend.dto.circulation.response.LoanResponse;
import me.ifmo.backend.entities.*;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueMappingStrategy;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Mapper(uses = {UserMapper.class, MaterialCopyMapper.class, BranchMapper.class},
        nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface LoanMapper {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "user", source = "user")
    @Mapping(target = "copy", source = "copy")
    @Mapping(target = "reservation", source = "reservation")
    @Mapping(target = "branch", source = "branch")
    @Mapping(target = "issuedByUser", source = "issuedByUser")
    @Mapping(target = "dueAt", source = "dueAt")
    Loan toEntity(User user, MaterialCopy copy, Reservation reservation, Branch branch, User issuedByUser, LocalDateTime dueAt);

    @Mapping(target = "reservationId", source = "reservation.id")
    LoanResponse toResponse(Loan loan);

    List<LoanResponse> toResponseList(Collection<Loan> loans);
}
