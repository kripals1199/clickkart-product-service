// src/main/java/com/clickkart/product/entity/ProductMediaEntity.java
package com.clickkart.product.entity;

import com.clickkart.product.enums.MediaType;
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
 * One image or video attached to a listing.
 *
 * <p>Sections 8 to 10 of the Add Product brief. A row per asset rather than a JSON array on the
 * product, because ordering is a first-class edit here — the seller drags to reorder and promotes
 * one asset to primary — and a rewritten array cannot express "move item 4 to position 1" without
 * rewriting all of it.
 *
 * <p><strong>This stores a URL, never the bytes.</strong> Binary in a relational column makes every
 * query that touches the product carry megabytes it did not ask for, and the platform has no object
 * store yet. When one exists, only whatever writes {@link #url} changes; nothing that reads a
 * listing does.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "product_media",
        uniqueConstraints = @UniqueConstraint(name = "uq_product_media_public_id", columnNames = "public_id"),
        indexes = @Index(name = "idx_product_media_product", columnList = "product_id, display_order"))
public class ProductMediaEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;

    @Column(name = "public_id", nullable = false, length = 40)
    private String publicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 10)
    private MediaType mediaType;

    @Column(name = "url", nullable = false, length = 1000)
    private String url;

    /**
     * Section 37. Never defaulted to the product name: alt text repeating the title on all seven
     * images tells a screen-reader user nothing about any of them.
     */
    @Column(name = "alt_text", length = 300)
    private String altText;

    /**
     * At most one per product, enforced by a partial unique index rather than by convention — the
     * rule spans rows, which is more than a CHECK can express.
     */
    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    /**
     * Section 9's per-image score. Stored rather than derived on read so the panel shows the seller
     * the same number they saw yesterday, and so scoring can get better without silently restating
     * every historic image.
     */
    @Column(name = "quality_score")
    private Integer qualityScore;

    @Column(name = "width_px")
    private Integer widthPx;

    @Column(name = "height_px")
    private Integer heightPx;

    /** Section 10. Videos only; null on every image. */
    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    private ProductMediaEntity(String publicId, MediaType mediaType, String url) {
        this.publicId = publicId;
        this.mediaType = mediaType;
        this.url = url;
    }

    public static ProductMediaEntity of(String publicId, MediaType mediaType, String url) {
        return new ProductMediaEntity(publicId, mediaType, url);
    }

    public void update(String url, String altText, Integer widthPx, Integer heightPx, Integer durationSeconds) {
        this.url = url;
        this.altText = altText;
        this.widthPx = widthPx;
        this.heightPx = heightPx;
        this.durationSeconds = durationSeconds;
    }

    public void placeAt(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    /**
     * Only an image can lead the gallery.
     *
     * <p>A video as the primary asset renders as a black frame anywhere a still is expected — the
     * listing card, the basket line, the order confirmation — and none of those surfaces can play
     * it. Refused here rather than in the service so no caller can route around it.
     */
    public void markPrimary(boolean primary) {
        this.primary = primary && mediaType == MediaType.IMAGE;
    }

    public void scoreQuality(Integer qualityScore) {
        this.qualityScore = qualityScore;
    }

    void assignTo(ProductEntity product) {
        this.product = product;
    }
}
