// src/main/java/com/clickkart/product/entity/ProductReviewEntity.java
package com.clickkart.product.entity;

import com.clickkart.product.enums.ReviewStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One customer's review of one product.
 *
 * <p><strong>The display name is copied, the author id is referenced.</strong> That looks like the
 * duplication this codebase avoids elsewhere, and it is deliberate: a review is a statement made at
 * a moment, and a reader needs a name against it without Product Service calling User Service once
 * per row on every product page. The id remains the join for anything that must be current - and
 * for erasure, which clears the copy.
 *
 * <p><strong>Verified purchase is stored, not derived.</strong> It is asked of Order Service once,
 * when the review is written. Recomputing it per read would put a cross-service call in the path of
 * a product page, and the answer cannot change in a way that should retract the badge: an order
 * already delivered stays delivered.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "product_reviews",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_product_reviews_product_author",
                        columnNames = {"product_id", "author_public_id"}),
        indexes = @Index(name = "idx_product_reviews_product_status", columnList = "product_id, status"))
public class ProductReviewEntity extends BaseEntity {

    @Column(name = "public_id", nullable = false, updatable = false, length = 40)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private ProductEntity product;

    @Column(name = "author_public_id", nullable = false, updatable = false, length = 40)
    private String authorPublicId;

    @Column(name = "author_display_name", length = 80)
    private String authorDisplayName;

    /** 1 to 5. The database enforces the range as well, because a bad rating corrupts the average. */
    @Column(name = "rating", nullable = false)
    private short rating;

    @Column(name = "title", length = 120)
    private String title;

    @Column(name = "body", length = 4000)
    private String body;

    @Column(name = "verified_purchase", nullable = false)
    private boolean verifiedPurchase;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReviewStatus status = ReviewStatus.PUBLISHED;

    @Column(name = "hidden_reason", length = 200)
    private String hiddenReason;

    public static ProductReviewEntity createFor(
            String publicId, ProductEntity product, String authorPublicId, boolean verifiedPurchase) {
        ProductReviewEntity review = new ProductReviewEntity();
        review.publicId = publicId;
        review.product = product;
        review.authorPublicId = authorPublicId;
        review.verifiedPurchase = verifiedPurchase;
        review.status = ReviewStatus.PUBLISHED;
        return review;
    }

    public void update(short rating, String title, String body, String authorDisplayName) {
        this.rating = rating;
        this.title = title;
        this.body = body;
        this.authorDisplayName = authorDisplayName;
    }

    /** Re-publishing clears the reason, so a restored review carries no trace of the old note. */
    public void publish() {
        this.status = ReviewStatus.PUBLISHED;
        this.hiddenReason = null;
    }

    public void hide(String reason) {
        this.status = ReviewStatus.HIDDEN;
        this.hiddenReason = reason;
    }

    public boolean isVisible() {
        return status == ReviewStatus.PUBLISHED;
    }
}
