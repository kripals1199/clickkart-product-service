// src/main/java/com/clickkart/product/entity/ProductEntity.java
package com.clickkart.product.entity;

import com.clickkart.product.enums.ProductStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A seller's listing.
 *
 * <p><strong>Cross-service references are ids, not foreign keys.</strong> {@link #sellerPublicId}
 * belongs to User Service and {@link #categoryPublicId} to Category Service - separate databases
 * reachable only by their own roles, so there is nothing to constrain against. Both are validated
 * over each service's internal API at submit time rather than on every read; a category that is
 * later deactivated does not retroactively invalidate a listing that is already on sale, which is
 * deliberate - pulling live products off the shelf because an operator hid a section would be a
 * surprising blast radius for that action.
 *
 * <p><strong>Nothing here tracks stock.</strong> Availability is Inventory Service's (#8), keyed by
 * variant SKU. Holding a quantity here as well would create two answers to "can I buy this", and
 * the wrong one would be the one the catalog renders.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "products",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_products_public_id", columnNames = "public_id"),
            @UniqueConstraint(name = "uk_products_slug", columnNames = "slug")
        },
        indexes = {
            @Index(name = "idx_products_seller", columnList = "seller_public_id"),
            @Index(name = "idx_products_category", columnList = "category_public_id"),
            // The public catalog filters on status before anything else, so it leads every index
            // that serves a browse or search.
            @Index(name = "idx_products_status_category", columnList = "status, category_public_id"),
            @Index(name = "idx_products_status_brand", columnList = "status, brand")
        })
public class ProductEntity extends BaseEntity {

    /** Stable and immutable. What Cart and Order store against a line item. */
    @Column(name = "public_id", nullable = false, updatable = false, length = 40)
    private String publicId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "slug", nullable = false, length = 220)
    private String slug;

    @Column(name = "description", length = 4000)
    private String description;

    @Column(name = "brand", length = 120)
    private String brand;

    /** User Service's seller publicId. Immutable - a listing cannot change hands. */
    @Column(name = "seller_public_id", nullable = false, updatable = false, length = 64)
    private String sellerPublicId;

    /** Category Service's publicId. Must be an active leaf at submit time. */
    @Column(name = "category_public_id", nullable = false, length = 40)
    private String categoryPublicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProductStatus status;

    /** Why an operator sent this back, so the seller knows what to fix. Cleared on resubmission. */
    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    /** The operator who last decided. Recorded here as well as in the audit trail, for the seller-facing view. */
    @Column(name = "reviewed_by", length = 64)
    private String reviewedBy;

    /**
     * Variants are owned by the product and meaningless without it, so the lifecycle cascades and
     * orphans are removed. This is the one place in this platform where a cascade is right: the
     * child has no independent identity, unlike an address or a category.
     */
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ProductVariantEntity> variants = new ArrayList<>();

    private ProductEntity(String publicId, String sellerPublicId) {
        this.publicId = publicId;
        this.sellerPublicId = sellerPublicId;
        this.status = ProductStatus.DRAFT;
    }

    /** New listings always start in DRAFT - a seller cannot create something already on sale. */
    public static ProductEntity createFor(String publicId, String sellerPublicId) {
        return new ProductEntity(publicId, sellerPublicId);
    }

    public void updateDetails(
            String name, String slug, String description, String brand, String categoryPublicId) {
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.brand = brand;
        this.categoryPublicId = categoryPublicId;
    }

    public void addVariant(ProductVariantEntity variant) {
        variants.add(variant);
        variant.assignTo(this);
    }

    public void removeVariant(ProductVariantEntity variant) {
        variants.remove(variant);
        variant.assignTo(null);
    }

    public void submitForReview() {
        this.status = ProductStatus.PENDING_REVIEW;
        // Cleared so a seller resubmitting after a rejection is not still shown the old reason as
        // though it were current.
        this.rejectionReason = null;
    }

    public void approve(String reviewerPublicId) {
        this.status = ProductStatus.ACTIVE;
        this.rejectionReason = null;
        this.reviewedBy = reviewerPublicId;
        this.reviewedAt = Instant.now();
    }

    /** Back to DRAFT rather than a terminal state, so the seller can fix what was flagged. */
    public void reject(String reviewerPublicId, String reason) {
        this.status = ProductStatus.DRAFT;
        this.rejectionReason = reason;
        this.reviewedBy = reviewerPublicId;
        this.reviewedAt = Instant.now();
    }

    public void archive() {
        this.status = ProductStatus.ARCHIVED;
    }

    public boolean isOwnedBy(String sellerPublicId) {
        return this.sellerPublicId.equals(sellerPublicId);
    }

    /**
     * A listing under review is frozen against seller edits. Without this a seller could pass
     * moderation with acceptable content and swap it afterwards, which is the entire failure the
     * review step exists to prevent - and an operator would be approving something that no longer
     * matches what they read.
     */
    public boolean isEditableBySeller() {
        return status == ProductStatus.DRAFT;
    }

    public boolean isPubliclyVisible() {
        return status == ProductStatus.ACTIVE;
    }
}
