package me.ifmo.backend.mappers;

import me.ifmo.backend.dto.fine.request.CreateFineTariffRequest;
import me.ifmo.backend.entities.FineTariff;
import org.mapstruct.*;

import java.time.LocalDateTime;

@Mapper(nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface FineTariffMapper {

    @BeanMapping(nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "validFrom", source = "validFrom")
    FineTariff toEntity(CreateFineTariffRequest request, LocalDateTime validFrom);
}
