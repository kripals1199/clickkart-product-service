// src/main/java/com/clickkart/product/repository/ProductRepository.java
package com.clickkart.product.repository;

import java.util.List;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import com.clickkart.product.entity.ProductMediaEntity;
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

    /**
     * Every media asset belonging to one seller, newest first.
     *
     * <p>Section 8's media library. Scoped to the seller in the query rather than filtered
     * afterwards - a library that fetched everything and then removed other sellers' images
     * would have already read them, and one missed filter downstream would leak them.
     */
    @Query("""
            select m from ProductMediaEntity m
              join m.product p
             where p.sellerPublicId = :sellerPublicId
               and m.mediaType = com.clickkart.product.enums.MediaType.IMAGE
             order by m.id desc
            """)
    List<ProductMediaEntity> findMediaForSeller(
            @Param("sellerPublicId") String sellerPublicId, Pageable pageable);
}
