// src/main/java/com/clickkart/product/entity/BrandEntity.java
package com.clickkart.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One brand in the shared vocabulary sellers pick from.
 *
 * <p>Section 6 of the Add Product brief. This exists to solve a spelling problem rather than a
 * storage one: forty sellers typing "Samsung", "SAMSUNG" and "Sam sung" produce three entries in
 * the customer-facing brand filter, and nothing downstream can merge them afterwards without
 * guessing. {@link #normalisedName} makes those three the same row at the point of entry.
 *
 * <p>{@code products.brand} keeps holding the plain name. This is not referenced by a foreign key,
 * deliberately — a listing published under a brand that is later retired must keep rendering the
 * name it went live with.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "brands",
        uniqueConstraints = {
            @UniqueConstraint(name = "uq_brands_public_id", columnNames = "public_id"),
            @UniqueConstraint(name = "uq_brands_normalised", columnNames = "normalised_name")
        },
        indexes = @Index(name = "idx_brands_status_name", columnList = "status, name"))
public class BrandEntity extends BaseEntity {

    @Column(name = "public_id", nullable = false, length = 40)
    private String publicId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "normalised_name", nullable = false, length = 120)
    private String normalisedName;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "created_by_seller", length = 40)
    private String createdBySeller;

    /**
     * Whether a seller invented this or an operator curated it.
     *
     * <p>Kept because the two need different treatment when someone later has to decide whether a
     * brand is real: an operator-curated name is settled, a seller-created one is a claim.
     */
    @Column(name = "seller_created", nullable = false)
    private boolean sellerCreated = true;

    private BrandEntity(String publicId, String name, String createdBySeller, boolean sellerCreated) {
        this.publicId = publicId;
        this.name = name;
        this.normalisedName = normalise(name);
        this.createdBySeller = createdBySeller;
        this.sellerCreated = sellerCreated;
    }

    public static BrandEntity createdBy(String publicId, String name, String sellerPublicId) {
        return new BrandEntity(publicId, name.trim(), sellerPublicId, true);
    }

    public void rename(String name) {
        this.name = name.trim();
        this.normalisedName = normalise(name);
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    /**
     * The key two spellings of one brand collide on.
     *
     * <p>Case, spacing and punctuation all go: "Sam sung", "SAMSUNG" and "Samsung" all reduce to
     * {@code samsung}. Deliberately lossy — it is never displayed, only compared.
     *
     * <p>Accents are folded too, so "Loréal" and "Loreal" are one brand rather than two that look
     * identical in a filter list and match different products.
     */
    public static String normalise(String name) {
        String folded = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return folded.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
