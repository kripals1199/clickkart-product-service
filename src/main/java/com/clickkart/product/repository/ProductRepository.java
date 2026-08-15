// src/main/java/com/clickkart/product/repository/ProductRepository.java
package com.clickkart.product.repository;

import com.clickkart.product.entity.ProductEntity;
import com.clickkart.product.enums.ProductStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Finders come in pairs: a public one constrained to {@link ProductStatus#ACTIVE}, and an
 * owner/operator one that is not. The status filter is in the query rather than left to callers,
 * because forgetting it on a public path leaks unreviewed and rejected listings into the shop front.
 */
public interface ProductRepository extends JpaRepository<ProductEntity, Long>, JpaSpecificationExecutor<ProductEntity> {

    Optional<ProductEntity> findByPublicId(String publicId);

    Optional<ProductEntity> findByPublicIdAndStatus(String publicId, ProductStatus status);

    Optional<ProductEntity> findBySlugAndStatus(String slug, ProductStatus status);

    boolean existsBySlug(String slug);

    /** A seller's own listings, in every state - this is their dashboard. */
    Page<ProductEntity> findBySellerPublicId(String sellerPublicId, Pageable pageable);

    Page<ProductEntity> findBySellerPublicIdAndStatus(String sellerPublicId, ProductStatus status, Pageable pageable);

    /** The operator moderation queue. */
    Page<ProductEntity> findByStatus(ProductStatus status, Pageable pageable);

    long countBySellerPublicIdAndStatus(String sellerPublicId, ProductStatus status);
}
