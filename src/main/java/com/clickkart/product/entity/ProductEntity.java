// src/main/java/com/clickkart/product/entity/ProductEntity.java
package com.clickkart.product.entity;

import com.clickkart.product.enums.DeliveryOption;
import com.clickkart.product.enums.MediaType;
import com.clickkart.product.enums.ProductType;
import com.clickkart.product.enums.WarrantyType;
import jakarta.persistence.OrderBy;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Optional;
import com.clickkart.product.enums.ProductStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.JoinColumn;
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
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
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

    /** Section 7. What a listing card can afford to render, written rather than truncated. */
    @Column(name = "short_description", length = 300)
    private String shortDescription;

    /** Section 6. Decides whether the form asks for weight, dimensions and delivery at all. */
    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 20)
    private ProductType productType = ProductType.PHYSICAL;

    /** Section 11. Tax belongs to what is sold; the prices it applies to sit on the variant. */
    @Column(name = "tax_rate_percent", precision = 5, scale = 2)
    private BigDecimal taxRatePercent;

    @Column(name = "price_includes_tax", nullable = false)
    private boolean priceIncludesTax = true;

    /*
     * Section 18: shipping. Integers in grams and millimetres - carrier rate cards band on whole
     * units, and a float here rounds differently in two services.
     */

    @Column(name = "weight_grams")
    private Integer weightGrams;

    @Column(name = "length_mm")
    private Integer lengthMm;

    @Column(name = "width_mm")
    private Integer widthMm;

    @Column(name = "height_mm")
    private Integer heightMm;

    @Column(name = "package_type", length = 40)
    private String packageType;

    @Column(name = "shipping_class", length = 40)
    private String shippingClass;

    @Column(name = "free_shipping", nullable = false)
    private boolean freeShipping;

    /**
     * Section 20. Zero is a real answer meaning no returns; null means the seller has not reached
     * that section yet. The publish checklist has to tell those apart, so this stays a boxed
     * Integer rather than an int defaulting to nought.
     */
    @Column(name = "return_window_days")
    private Integer returnWindowDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "warranty_type", length = 30)
    private WarrantyType warrantyType;

    @Column(name = "warranty_months")
    private Integer warrantyMonths;

    /**
     * Section 21. Kept apart from name and description: an SEO title is written for a search
     * result and is routinely not the product name, so deriving one from the other throws away a
     * deliberate choice the seller made.
     */
    @Column(name = "seo_title", length = 200)
    private String seoTitle;

    @Column(name = "meta_description", length = 320)
    private String metaDescription;

    /** Sections 26 and 27. What Saved 12 seconds ago and Last edited 2 minutes ago read from. */
    @Column(name = "last_edited_at")
    private Instant lastEditedAt;

    /**
     * Section 18. The delivery speeds this listing supports.
     *
     * <p>A set: a product can offer both, and one that is bulky may offer only standard.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "product_delivery_options",
            joinColumns = @JoinColumn(name = "product_id"),
            indexes = @Index(name = "idx_product_delivery_options_product", columnList = "product_id"))
    @Column(name = "delivery_option", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Set<DeliveryOption> deliveryOptions = new LinkedHashSet<>();

    /** Section 21. A row per keyword, so a comma inside one is not a separator. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "product_keywords",
            joinColumns = @JoinColumn(name = "product_id"),
            indexes = @Index(name = "idx_product_keywords_product", columnList = "product_id"))
    @Column(name = "keyword", nullable = false, length = 60)
    @OrderBy("keyword")
    private Set<String> keywords = new LinkedHashSet<>();

    /**
     * Sections 8 to 10. Ordered by the position the seller dragged them into, which is the order
     * a customer sees - so the ordering is data, not a rendering decision made downstream.
     */
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder asc, id asc")
    private List<ProductMediaEntity> media = new ArrayList<>();

    /** Section 13. Badges this listing advertises; the terms behind them are not ours to keep. */
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder asc, id asc")
    private List<ProductOfferEntity> offers = new ArrayList<>();

    /**
     * Variants are owned by the product and meaningless without it, so the lifecycle cascades and
     * orphans are removed. This is the one place in this platform where a cascade is right: the
     * child has no independent identity, unlike an address or a category.
     */
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ProductVariantEntity> variants = new ArrayList<>();

    /**
     * The seller's answers to the properties Category Service says apply to this product's category.
     *
     * <p>Which properties those are is not this service's business, and deliberately so: the
     * structure is catalogue governance, owned next door, while the values belong to the product.
     * The only thing crossing that boundary is the property name, which Category Service guarantees
     * is stable. Nothing here validates against the master data — that check happens where the
     * definitions live.
     *
     * <p>A collection rather than a map, unlike the variant attributes beside it, because a
     * multi-select property is several answers to one question. See {@link ProductPropertyValue}.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "product_properties",
            joinColumns = @JoinColumn(name = "product_id"),
            indexes = @Index(name = "idx_product_properties_product", columnList = "product_id"))
    private Set<ProductPropertyValue> properties = new LinkedHashSet<>();

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

    /** Sections 7 and 6. Copy and form shape, which travel together on every save. */
    public void updatePresentation(String shortDescription, ProductType productType) {
        this.shortDescription = shortDescription;
        this.productType = productType == null ? ProductType.PHYSICAL : productType;
    }

    /** Section 11. */
    public void updateTax(BigDecimal taxRatePercent, boolean priceIncludesTax) {
        this.taxRatePercent = taxRatePercent;
        this.priceIncludesTax = priceIncludesTax;
    }

    /**
     * Section 18.
     *
     * <p>A digital product keeps none of this. Cleared rather than refused, because the seller who
     * switches a listing to digital is not making an error - they are saying these fields stopped
     * applying, and stale dimensions left behind would quote a delivery date for something that is
     * never posted.
     */
    public void updateShipping(
            Integer weightGrams, Integer lengthMm, Integer widthMm, Integer heightMm,
            String packageType, String shippingClass, boolean freeShipping,
            Collection<DeliveryOption> deliveryOptions) {
        if (productType == ProductType.DIGITAL) {
            this.weightGrams = null;
            this.lengthMm = null;
            this.widthMm = null;
            this.heightMm = null;
            this.packageType = null;
            this.shippingClass = null;
            this.freeShipping = false;
            this.deliveryOptions.clear();
            return;
        }
        this.weightGrams = weightGrams;
        this.lengthMm = lengthMm;
        this.widthMm = widthMm;
        this.heightMm = heightMm;
        this.packageType = packageType;
        this.shippingClass = shippingClass;
        this.freeShipping = freeShipping;
        this.deliveryOptions.clear();
        if (deliveryOptions != null) {
            this.deliveryOptions.addAll(deliveryOptions);
        }
    }

    /** Section 20. */
    public void updateAftersales(
            Integer returnWindowDays, WarrantyType warrantyType, Integer warrantyMonths) {
        this.returnWindowDays = returnWindowDays;
        this.warrantyType = warrantyType;
        // Months are meaningless without someone to honour them.
        this.warrantyMonths =
                warrantyType == null || warrantyType == WarrantyType.NONE ? null : warrantyMonths;
    }

    /** Section 21. */
    public void updateSeo(String seoTitle, String metaDescription, Collection<String> keywords) {
        this.seoTitle = seoTitle;
        this.metaDescription = metaDescription;
        this.keywords.clear();
        if (keywords != null) {
            this.keywords.addAll(keywords);
        }
    }

    /** Sections 26 and 27. Stamped on every write so the header can say when, not just that. */
    public void touchEdited(Instant at) {
        this.lastEditedAt = at;
    }

    public void addMedia(ProductMediaEntity asset) {
        media.add(asset);
        asset.assignTo(this);
    }

    public void removeMedia(ProductMediaEntity asset) {
        media.remove(asset);
        asset.assignTo(null);
    }

    /**
     * Promotes one asset and demotes the rest.
     *
     * <p>One call rather than a flag set on the winner, because two primaries is a state the
     * gallery cannot render and the partial unique index will not accept - and that failure would
     * surface as a constraint violation on save, long after the click that caused it.
     */
    public void makePrimary(ProductMediaEntity chosen) {
        for (ProductMediaEntity asset : media) {
            asset.markPrimary(asset == chosen);
        }
    }

    /**
     * The asset a listing card should show.
     *
     * <p>Falls back to the first image when nothing is marked, so a draft the seller has not
     * finished still previews rather than rendering an empty frame.
     */
    public Optional<ProductMediaEntity> primaryImage() {
        return media.stream()
                .filter(asset -> asset.getMediaType() == MediaType.IMAGE)
                .min(Comparator.comparing(ProductMediaEntity::isPrimary).reversed()
                        .thenComparingInt(ProductMediaEntity::getDisplayOrder));
    }

    public void addOffer(ProductOfferEntity offer) {
        offers.add(offer);
        offer.assignTo(this);
    }

    public void removeOffer(ProductOfferEntity offer) {
        offers.remove(offer);
        offer.assignTo(null);
    }

    /**
     * Replaces every recorded property value.
     *
     * <p>Wholesale rather than a merge, because a save carries the complete set of answers: a
     * property the seller cleared is absent from the incoming set, and merging would silently keep
     * the old value. Clearing in place rather than reassigning the field is what lets the
     * persistence provider diff the collection instead of deleting and reinserting every row.
     */
    public void replaceProperties(Collection<ProductPropertyValue> incoming) {
        properties.clear();
        if (incoming != null) {
            properties.addAll(incoming);
        }
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
