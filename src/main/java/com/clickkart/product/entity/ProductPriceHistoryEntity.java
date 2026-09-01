// src/main/java/com/clickkart/product/entity/ProductPriceHistoryEntity.java
package com.clickkart.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One observed price for one SKU, at one moment.
 *
 * <p><strong>Keyed by SKU, not by a link to the variant.</strong> {@code updateOwnProduct} replaces
 * a product's variants wholesale, and orphanRemoval deletes the old rows - so a foreign key to
 * {@link ProductVariantEntity} would destroy the price history the first time a seller edited a
 * title. The SKU is what survives an edit, because it is what the seller keeps.
 *
 * <p><strong>Written on change, not on save.</strong> A seller who edits a listing ten times
 * without touching the price leaves one row. Otherwise "the lowest this has been" would answer a
 * question about how often somebody opened the form.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "product_price_history",
        indexes = @Index(name = "idx_product_price_history_sku_time", columnList = "product_id, sku, recorded_at"))
public class ProductPriceHistoryEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private ProductEntity product;

    @Column(name = "sku", nullable = false, updatable = false, length = 60)
    private String sku;

    @Column(name = "selling_price", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal sellingPrice;

    /**
     * When the price was observed, set by the caller rather than by auditing.
     *
     * <p>{@code createdDate} would nearly do, but it means "when this row was written", and the two
     * would part company the moment a backfill or an import existed.
     */
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    public static ProductPriceHistoryEntity of(
            ProductEntity product, String sku, BigDecimal sellingPrice, Instant recordedAt) {
        ProductPriceHistoryEntity entry = new ProductPriceHistoryEntity();
        entry.product = product;
        entry.sku = sku;
        entry.sellingPrice = sellingPrice;
        entry.recordedAt = recordedAt;
        return entry;
    }
}
