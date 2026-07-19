package me.ifmo.backend.shared.cache;

import me.ifmo.backend.catalog.domain.enums.MaterialStatus;
import me.ifmo.backend.catalog.domain.enums.MaterialType;
import me.ifmo.backend.catalog.web.response.MaterialResponse;
import me.ifmo.backend.shared.web.response.PageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Redis catalog value serialization")
class RedisCatalogValueSerializationTest {

    @Test
    @DisplayName("round-trips a material page as typed JSON")
    void roundTripsMaterialPageAsTypedJson() {
        var serializer = new GenericJackson2JsonRedisSerializer();
        var material = new MaterialResponse(
                1L,
                "9780132350884",
                "Clean Code",
                null,
                "Prentice Hall",
                2008,
                MaterialType.BOOK,
                "EN",
                MaterialStatus.ACTIVE,
                new ArrayList<>(),
                new ArrayList<>(),
                1,
                1,
                new ArrayList<>()
        );
        var page = new PageResponse<MaterialResponse>(
                new ArrayList<>(List.of(material)),
                0,
                20,
                1,
                1,
                true,
                true,
                false
        );

        Object restored = serializer.deserialize(serializer.serialize(page));

        assertThat(restored).isInstanceOf(PageResponse.class);
        var restoredPage = (PageResponse<?>) restored;
        assertThat(restoredPage.content())
                .singleElement()
                .isInstanceOfSatisfying(MaterialResponse.class, restoredMaterial ->
                        assertThat(restoredMaterial.title()).isEqualTo("Clean Code")
                );
    }
}
