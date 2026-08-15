// src/main/java/com/clickkart/product/repository/ProductVariantRepository.java
package com.clickkart.product.repository;

import com.clickkart.product.entity.ProductVariantEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductVariantRepository extends JpaRepository<ProductVariantEntity, Long> {

    boolean existsBySku(String sku);

    Optional<ProductVariantEntity> findBySku(String sku);

    /**
     * Resolves a variant by SKU together with its product, for the internal API Cart and Order use.
     * A join fetch rather than a lazy walk: the caller always needs both, and letting it lazy-load
     * would issue a second query per line item on a basket.
     */
    @org.springframework.data.jpa.repository.Query(
            "select v from ProductVariantEntity v join fetch v.product where v.sku = :sku")
    Optional<ProductVariantEntity> findBySkuWithProduct(@org.springframework.data.repository.query.Param("sku") String sku);
}
