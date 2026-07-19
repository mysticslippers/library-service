package me.ifmo.backend.shared.cache;

import org.springframework.cache.annotation.CacheEvict;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@CacheEvict(cacheNames = CacheNames.CATALOG_SEARCH, allEntries = true)
public @interface InvalidateCatalogSearch {
}
