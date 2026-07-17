package me.ifmo.backend.catalog.persistence;

import me.ifmo.backend.catalog.domain.Author;
import me.ifmo.backend.catalog.domain.enums.AuthorStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AuthorRepository extends JpaRepository<Author, Long> {

    @Query("""
           SELECT author FROM Author author
               WHERE lower(author.firstName) = lower(:firstName)
                   AND lower(coalesce(author.middleName, '')) = lower(coalesce(:middleName, ''))\s
                   AND lower(author.lastName) = lower(:lastName)\s
   \s""")
    Optional<Author> findByFullName(@Param("firstName") String firstName,
                                    @Param("middleName") String middleName,
                                    @Param("lastName") String lastName);

    @Query("""
           SELECT CASE WHEN count(author) > 0 THEN TRUE ELSE FALSE END\s
               FROM Author author
               WHERE lower(author.firstName) = lower(:firstName)
                   AND lower(coalesce(author.middleName, '')) = lower(coalesce(:middleName, ''))\s
                   AND lower(author.lastName) = lower(:lastName)\s
   \s""")
    boolean existsByFullName(@Param("firstName") String firstName,
                             @Param("middleName") String middleName,
                             @Param("lastName") String lastName);

    @Query("""
           SELECT author FROM Author author
               WHERE (:query IS NULL OR :query = ''
                   OR lower(author.firstName) LIKE lower(concat('%', :query, '%'))\s
                   OR lower(coalesce(author.middleName, '')) LIKE lower(concat('%', :query, '%'))\s
                   OR lower(author.lastName) LIKE lower(concat('%', :query, '%')))
                   AND author.status = :status
   \s""")
    Page<Author> search(@Param("query") String query, @Param("status") AuthorStatus status, Pageable pageable);
}
