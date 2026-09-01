// src/main/java/com/clickkart/product/entity/ProductVariantEntity.java
package com.clickkart.product.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The thing a customer actually buys - "Blue / Medium" rather than "T-shirt".
 *
 * <p>Every product has at least one, even when it has no real options, because the alternative is
 * for Cart, Order and Inventory each to handle "sometimes the product is the purchasable unit and
 * sometimes a variant is". One always-present level costs a row and removes that branch from three
 * downstream services.
 *
 * <p><strong>Money is {@link BigDecimal} with an explicit scale, never a floating-point type.</strong>
 * A double cannot represent 0.10 exactly, so totals drift by fractions of a paisa and eventually
 * fail to reconcile against a payment provider - a class of bug that only shows up in aggregate,
 * long after it is cheap to fix. The column is {@code numeric(12,2)}: twelve digits carries an
 * invoice-sized amount in paise-precision rupees.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "product_variants",
        uniqueConstraints = @UniqueConstraint(name = "uk_product_variants_sku", columnNames = "sku"),
        indexes = {
            @Index(name = "idx_product_variants_product_id", columnList = "product_id"),
            @Index(name = "idx_product_variants_sku", columnList = "sku")
        })
public class ProductVariantEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;

    /**
     * Globally unique stock-keeping unit. Inventory Service keys availability on this, and a
     * warehouse operator reads it off a label - so it is uppercased and constrained to characters
     * that survive being printed, scanned and re-typed.
     */
    @Column(name = "sku", nullable = false, length = 64)
    private String sku;

    /** Human-readable option summary, e.g. "Blue / M". Derived from {@link #attributes} by the caller. */
    @Column(name = "variant_name", nullable = false, length = 150)
    private String variantName;

    /** Maximum retail price - what the listing is discounted *from*. */
    @Column(name = "mrp", nullable = false, precision = 12, scale = 2)
    private BigDecimal mrp;

    @Column(name = "selling_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal sellingPrice;

    /**
     * Section 11. What this SKU costs the seller, for their own margin - never shown to a customer.
     *
     * <p>Per variant rather than per product because that is where it differs: the 256GB model
     * costs more to source than the 128GB and shares nothing with it but a product row.
     */
    @Column(name = "cost_price", precision = 12, scale = 2)
    private BigDecimal costPrice;

    /**
     * The options this variant represents, e.g. {@code {colour: Blue, size: M}}.
     *
     * <p>A key/value table rather than columns, because the meaningful options differ per category -
     * shoes have a size, a phone has storage, a book has neither - and modelling them as columns
     * would mean a schema change per category and a table of mostly-null fields.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "product_variant_attributes",
            joinColumns = @JoinColumn(name = "variant_id"),
            indexes = @Index(name = "idx_variant_attributes_variant_id", columnList = "variant_id"))
    @MapKeyColumn(name = "attribute_key", length = 60)
    @Column(name = "attribute_value", length = 200)
    private Map<String, String> attributes = new LinkedHashMap<>();

    /** A variant can be taken off sale without removing it, since orders reference its SKU. */
    @Column(name = "active", nullable = false)
    private boolean active;

    private ProductVariantEntity(String sku) {
        this.sku = sku;
        this.active = true;
    }

    public static ProductVariantEntity createWithSku(String sku) {
        return new ProductVariantEntity(sku);
    }

    void assignTo(ProductEntity product) {
        this.product = product;
    }

    /** Cost unchanged. The variant matrix edits price far more often than it edits margin. */
    public void update(String variantName, BigDecimal mrp, BigDecimal sellingPrice, Map<String, String> attributes) {
        this.variantName = variantName;
        // Normalised to the column's scale on the way in. Accepting 199.999 and letting the database
        // round it would mean the price the seller sees back differs from the one they submitted.
        this.mrp = mrp.setScale(2, java.math.RoundingMode.HALF_UP);
        this.sellingPrice = sellingPrice.setScale(2, java.math.RoundingMode.HALF_UP);
        this.attributes = attributes == null ? new LinkedHashMap<>() : new LinkedHashMap<>(attributes);
    }

    /** As above, and records what this SKU cost the seller. */
    public void update(
            String variantName, BigDecimal mrp, BigDecimal sellingPrice,
            Map<String, String> attributes, BigDecimal costPrice) {
        update(variantName, mrp, sellingPrice, attributes);
        this.costPrice = costPrice == null ? null : costPrice.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    public void activate(boolean active) {
        this.active = active;
    }

    /** Percentage off, for display. Zero when there is no discount, never negative. */
    public int discountPercentage() {
        if (mrp == null || sellingPrice == null || mrp.signum() <= 0) {
            return 0;
        }
        BigDecimal saved = mrp.subtract(sellingPrice);
        if (saved.signum() <= 0) {
            return 0;
        }
        return saved.multiply(BigDecimal.valueOf(100))
                .divide(mrp, 0, java.math.RoundingMode.DOWN)
                .intValue();
    }
}
