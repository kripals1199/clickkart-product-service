// src/main/java/com/clickkart/product/entity/ProductOfferEntity.java
package com.clickkart.product.entity;

import com.clickkart.product.enums.OfferType;
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
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A promotion this listing advertises.
 *
 * <p>Section 13 of the Add Product brief. These are merchandising badges, not the coupon engine.
 * What is recorded here is that this product advertises an offer and the label a customer reads —
 * never its budget, eligibility or redemption rules, which belong to whatever service issues it.
 * Putting those here would make this table a second, unenforced copy of a coupon's terms, and the
 * two would disagree the first time a budget ran out.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "product_offers",
        uniqueConstraints = @UniqueConstraint(name = "uq_product_offers_public_id", columnNames = "public_id"),
        indexes = @Index(name = "idx_product_offers_product", columnList = "product_id, display_order"))
public class ProductOfferEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;

    @Column(name = "public_id", nullable = false, length = 40)
    private String publicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "offer_type", nullable = false, length = 20)
    private OfferType offerType;

    /** What the badge says. The one field a customer actually reads. */
    @Column(name = "label", nullable = false, length = 160)
    private String label;

    /** Null for everything but a coupon. */
    @Column(name = "code", length = 40)
    private String code;

    @Column(name = "starts_at")
    private Instant startsAt;

    /**
     * Section 13's "Ends in 04:32:12". Null means the offer does not expire, which is why the
     * countdown is driven by this column rather than by the offer type — a bank offer can be
     * time-boxed and a deal can be open-ended.
     */
    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    private ProductOfferEntity(String publicId, OfferType offerType, String label) {
        this.publicId = publicId;
        this.offerType = offerType;
        this.label = label;
    }

    public static ProductOfferEntity of(String publicId, OfferType offerType, String label) {
        return new ProductOfferEntity(publicId, offerType, label);
    }

    public void update(String label, String code, Instant startsAt, Instant endsAt, boolean active) {
        this.label = label;
        this.code = code;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.active = active;
    }

    public void placeAt(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    /**
     * Whether this offer should be shown to a customer at the given moment.
     *
     * <p>Deactivating and expiring are different things and both have to be checked: a seller turns
     * an offer off by hand, and a dated one turns itself off. Asked as a question rather than
     * stored as a flag, because a stored one is wrong the instant the clock passes {@link #endsAt}
     * and nothing runs to correct it.
     */
    public boolean isLiveAt(Instant moment) {
        if (!active) {
            return false;
        }
        if (startsAt != null && moment.isBefore(startsAt)) {
            return false;
        }
        return endsAt == null || moment.isBefore(endsAt);
    }

    void assignTo(ProductEntity product) {
        this.product = product;
    }
}
