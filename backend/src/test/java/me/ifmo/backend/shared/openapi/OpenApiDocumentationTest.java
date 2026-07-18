package me.ifmo.backend.shared.openapi;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.core.annotation.AnnotatedElementUtils.hasAnnotation;

@DisplayName("OpenAPI documentation")
class OpenApiDocumentationTest {

    @Test
    @DisplayName("documents every REST controller and endpoint")
    void documentsEveryRestControllerAndEndpoint() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        var controllers = scanner.findCandidateComponents("me.ifmo.backend").stream()
                .map(definition -> ClassUtils.resolveClassName(
                        Objects.requireNonNull(definition.getBeanClassName()),
                        ClassUtils.getDefaultClassLoader()
                ))
                .toList();

        assertThat(controllers)
                .isNotEmpty()
                .allSatisfy(controller -> {
                    assertThat(controller.getAnnotation(Tag.class))
                            .as("%s must declare @Tag", controller.getSimpleName())
                            .isNotNull();

                    assertThat(Arrays.stream(controller.getDeclaredMethods())
                            .filter(this::isEndpoint)
                            .toList())
                            .as("%s endpoints", controller.getSimpleName())
                            .isNotEmpty()
                            .allSatisfy(method -> assertThat(method.getAnnotation(Operation.class))
                                    .as("%s.%s must declare @Operation",
                                            controller.getSimpleName(),
                                            method.getName())
                                    .isNotNull());
                });
    }

    private boolean isEndpoint(Method method) {
        return hasAnnotation(method, RequestMapping.class);
    }
}
