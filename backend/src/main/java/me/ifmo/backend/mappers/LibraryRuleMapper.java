package me.ifmo.backend.mappers;

import me.ifmo.backend.dto.library.request.CreateLibraryRuleRequest;
import me.ifmo.backend.entities.Branch;
import me.ifmo.backend.entities.LibraryRule;
import org.mapstruct.*;

import java.time.LocalDateTime;

@Mapper(uses = BranchMapper.class,
        nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface LibraryRuleMapper {

    @BeanMapping(nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "branch", source = "branch")
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "validFrom", source = "validFrom")
    LibraryRule toEntity(CreateLibraryRuleRequest request, Branch branch, LocalDateTime validFrom);
}
