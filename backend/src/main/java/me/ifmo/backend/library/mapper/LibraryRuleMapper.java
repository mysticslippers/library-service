package me.ifmo.backend.library.mapper;

import me.ifmo.backend.library.web.request.CreateLibraryRuleRequest;
import me.ifmo.backend.library.web.request.UpdateLibraryRuleRequest;
import me.ifmo.backend.library.web.response.LibraryRuleResponse;
import me.ifmo.backend.library.domain.Branch;
import me.ifmo.backend.library.domain.LibraryRule;
import org.mapstruct.*;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Mapper(uses = BranchMapper.class,
        nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface LibraryRuleMapper {

    @BeanMapping(nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "branch", source = "branch")
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "validFrom", ignore = true)
    LibraryRule toEntity(CreateLibraryRuleRequest request, Branch branch);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "branch", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "validFrom", ignore = true)
    void updateEntity(UpdateLibraryRuleRequest request, @MappingTarget LibraryRule rule);

    LibraryRuleResponse toResponse(LibraryRule rule);

    List<LibraryRuleResponse> toResponseList(Collection<LibraryRule> rules);
}
