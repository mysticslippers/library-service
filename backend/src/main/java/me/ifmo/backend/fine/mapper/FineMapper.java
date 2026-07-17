package me.ifmo.backend.fine.mapper;

import me.ifmo.backend.fine.domain.Fine;
import me.ifmo.backend.fine.domain.FineTariff;

import me.ifmo.backend.circulation.domain.Loan;

import me.ifmo.backend.catalog.domain.MaterialCopy;
import me.ifmo.backend.catalog.mapper.MaterialCopyMapper;

import me.ifmo.backend.user.domain.User;
import me.ifmo.backend.user.mapper.UserMapper;

import me.ifmo.backend.fine.web.response.FineResponse;
import me.ifmo.backend.fine.domain.enums.ViolationType;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueMappingStrategy;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

@Mapper(uses = {UserMapper.class, MaterialCopyMapper.class},
        nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface FineMapper {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "user", source = "user")
    @Mapping(target = "loan", source = "loan")
    @Mapping(target = "copy", source = "copy")
    @Mapping(target = "tariff", source = "tariff")
    @Mapping(target = "reason", source = "reason")
    @Mapping(target = "amount", source = "amount")
    Fine toEntity(User user, Loan loan, MaterialCopy copy, FineTariff tariff, ViolationType reason, BigDecimal amount);

    @Mapping(target = "loanId", source = "loan.id")
    @Mapping(target = "tariffId", source = "tariff.id")
    FineResponse toResponse(Fine fine);

    List<FineResponse> toResponseList(Collection<Fine> fines);
}
