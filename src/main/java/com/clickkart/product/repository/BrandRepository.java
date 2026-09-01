// src/main/java/com/clickkart/product/repository/BrandRepository.java
package com.clickkart.product.repository;

import com.clickkart.product.entity.BrandEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BrandRepository extends JpaRepository<BrandEntity, Long> {

    Optional<BrandEntity> findByPublicId(String publicId);

    /** The collision check. Two spellings of one brand reduce to the same key. */
    Optional<BrandEntity> findByNormalisedName(String normalisedName);

    /**
     * Active brands whose name contains the term, alphabetically.
     *
     * <p>Matched on the normalised key rather than the display name, so typing "sam sung" still
     * finds "Samsung" - which is exactly the mistake this list exists to prevent.
     */
    /**
     * The term is never null - an empty string means "everything".
     *
     * <p>It used to read {@code :term is null or ...}, which PostgreSQL cannot plan: with a null
     * bind it has no way to infer the parameter's type, infers {@code bytea}, and fails the whole
     * statement with
     *
     * <pre>operator does not exist: character varying ~~ bytea</pre>
     *
     * <p>An empty term produces {@code like '%%'}, which matches every row - so the branch was
     * not buying anything the LIKE does not already do.
     */
    @Query("""
            select b from BrandEntity b
             where b.status = 'ACTIVE'
               and b.normalisedName like concat('%', :term, '%')
             order by b.name asc
            """)
    List<BrandEntity> search(@Param("term") String term);
}
