package me.ifmo.backend.catalog.web;

import me.ifmo.backend.catalog.application.MaterialCoverService;
import me.ifmo.backend.catalog.application.MaterialService;
import me.ifmo.backend.catalog.application.cover.MaterialCoverContent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MaterialCoverCacheTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void immutableCachingRequiresAnExplicitVersion(boolean versioned) throws Exception {
        var covers = mock(MaterialCoverService.class);
        var controller = new MaterialController(mock(MaterialService.class), covers);
        UUID version = versioned ? UUID.randomUUID() : null;
        var content = new ByteArrayInputStream(new byte[]{1, 2, 3});
        when(covers.getPublic(1L, version)).thenReturn(
                new MaterialCoverContent(content, 3, "image/jpeg", "etag", Instant.now()));

        var response = controller.getCover(1L, version);

        assertThat(response.getHeaders().getCacheControl()).isEqualTo(
                versioned ? "max-age=31536000, public, immutable" : "no-cache");
        content.close();
    }
}
