package me.ifmo.backend.mappers;

import me.ifmo.backend.dto.fine.response.FineResponse;
import me.ifmo.backend.entities.*;
import me.ifmo.backend.entities.enums.ViolationType;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueMappingStrategy;

import java.math.BigDecimal;

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
}
