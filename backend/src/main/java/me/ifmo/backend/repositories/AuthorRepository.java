package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.Author;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AuthorRepository extends JpaRepository<Author, Long> {

    Page<Author> findByLastNameContainingIgnoreCase(String lastName, Pageable pageable);

    @Query("""
           SELECT author FROM Author author
               WHERE lower(author.firstName) = lower(:firstName)
                   AND lower(coalesce(author.middleName, '')) = lower(coalesce(:middleName, '')) 
                   AND lower(author.lastName) = lower(:lastName) 
    """)
    Optional<Author> findByFullName(@Param("firstName") String firstName,
                                    @Param("middleName") String middleName,
                                    @Param("lastName") String lastName);
}
