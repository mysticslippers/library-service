package me.ifmo.backend.catalog.mapper;

import me.ifmo.backend.catalog.web.request.CreateMaterialRequest;
import me.ifmo.backend.catalog.web.request.UpdateMaterialRequest;
import me.ifmo.backend.catalog.web.response.MaterialResponse;
import me.ifmo.backend.catalog.web.response.MaterialShortResponse;
import me.ifmo.backend.catalog.domain.Material;
import me.ifmo.backend.catalog.domain.MaterialAuthor;
import me.ifmo.backend.catalog.domain.MaterialCopy;
import me.ifmo.backend.catalog.domain.MaterialGenre;
import org.mapstruct.*;

import java.util.Collection;
import java.util.List;

@Mapper(uses = {MaterialAuthorMapper.class, MaterialGenreMapper.class, MaterialCopyMapper.class,
        MaterialCoverUrlFactory.class})
public interface MaterialMapper {

    @BeanMapping(nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "coverObjectKey", ignore = true)
    @Mapping(target = "coverVersion", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Material toEntity(CreateMaterialRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "coverObjectKey", ignore = true)
    @Mapping(target = "coverVersion", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(UpdateMaterialRequest request, @MappingTarget Material material);

    @Mapping(target = "authors", source = "materialAuthors")
    @Mapping(target = "genres", source = "materialGenres")
    @Mapping(target = "copies", source = "materialCopies")
    @Mapping(target = "totalCopies", source = "totalCopies")
    @Mapping(target = "availableCopies", source = "availableCopies")
    @Mapping(target = "coverUrl", source = "material", qualifiedByName = "materialCoverUrl")
    MaterialResponse toResponse(Material material, Collection<MaterialAuthor> materialAuthors,
                                Collection<MaterialGenre> materialGenres,
                                Collection<MaterialCopy> materialCopies,
                                long totalCopies,
                                long availableCopies);

    default MaterialResponse toResponse(Material material, Collection<MaterialAuthor> materialAuthors,
                                        Collection<MaterialGenre> materialGenres) {
        return toResponse(material, materialAuthors, materialGenres, List.of(), 0, 0);
    }

    @Mapping(target = "authors", source = "materialAuthors")
    @Mapping(target = "genres", source = "materialGenres")
    @Mapping(target = "coverUrl", source = "material", qualifiedByName = "materialCoverUrl")
    MaterialShortResponse toShortResponse(Material material, Collection<MaterialAuthor> materialAuthors,
                                          Collection<MaterialGenre> materialGenres);
}
