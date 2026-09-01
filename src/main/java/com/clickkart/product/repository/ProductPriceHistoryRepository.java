// src/main/java/com/clickkart/product/repository/ProductPriceHistoryRepository.java
package com.clickkart.product.repository;

import com.clickkart.product.entity.ProductPriceHistoryEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductPriceHistoryRepository extends JpaRepository<ProductPriceHistoryEntity, Long> {

    /** The price this SKU was last recorded at, which is what a new price is compared against. */
    Optional<ProductPriceHistoryEntity> findTopByProductIdAndSkuOrderByRecordedAtDesc(Long productId, String sku);

    /**
     * The highest this product has been priced since a cutoff, across all its SKUs.
     *
     * <p>Across SKUs rather than per SKU because the storefront shows one figure for a product,
     * and the price it shows is the cheapest variant's. Comparing that against the highest
     * anything has been would overstate the drop, so callers pass the same SKU they are pricing.
     *
     * <p>Null when nothing was recorded in the window, which is the honest answer for a listing
     * that has never changed price - not a drop of zero.
     */
    @Query("""
            select max(h.sellingPrice)
              from ProductPriceHistoryEntity h
             where h.product.publicId = :productPublicId
               and h.sku = :sku
               and h.recordedAt >= :since
            """)
    BigDecimal highestSince(
            @Param("productPublicId") String productPublicId,
            @Param("sku") String sku,
            @Param("since") Instant since);
}
