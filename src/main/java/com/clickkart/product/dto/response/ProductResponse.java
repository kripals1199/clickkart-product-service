// src/main/java/com/clickkart/product/dto/response/ProductResponse.java
package com.clickkart.product.dto.response;

import com.clickkart.product.enums.ProductType;
import com.clickkart.product.enums.WarrantyType;
import java.math.BigDecimal;
import com.clickkart.product.entity.ProductEntity;
import com.clickkart.product.entity.ProductPropertyValue;
import com.clickkart.product.enums.ProductStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.List;

/**
 * A listing.
 *
 * <p>Two factories, and the difference matters: {@link #forCustomer} omits the moderation fields
 * entirely, so a rejection reason written by an operator for the seller never reaches a shopper.
 * {@link #forSeller} includes them, because that is the only way the seller learns what to fix.
 *
 * <p>Building one shape with nullable fields and trusting each call site to null them out would put
 * that decision in every controller instead of here, and the failure mode is silent disclosure.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductResponse(
        String publicId,
        String name,
        String slug,
        String description,
        String brand,
        String categoryPublicId,
        String sellerPublicId,
        ProductStatus status,
        List<VariantResponse> variants,
        /** Recorded specification values, keyed by property name. Never null; empty when none. */
        Map<String, List<String>> properties,
        String rejectionReason,
        Instant reviewedAt,
        Instant createdDate,
        Instant updatedDate,

        /* ---- the Add Product workspace, sections 6 to 21 ---- */

        String shortDescription,
        ProductType productType,
        BigDecimal taxRatePercent,
        boolean priceIncludesTax,

        Integer weightGrams,
        Integer lengthMm,
        Integer widthMm,
        Integer heightMm,
        String packageType,
        String shippingClass,
        boolean freeShipping,
        java.util.List<com.clickkart.product.enums.DeliveryOption> deliveryOptions,

        Integer returnWindowDays,
        WarrantyType warrantyType,
        Integer warrantyMonths,

        String seoTitle,
        String metaDescription,
        List<String> keywords,

        /** Sections 8 to 10, in the order the seller arranged them. */
        List<MediaResponse> media,

        /** Section 13. A customer sees only the live ones; a seller sees all of them. */
        List<OfferResponse> offers,

        /** Sections 26 and 27. Null until the listing has been saved at least once. */
        Instant lastEditedAt) {

    /** Public catalog view - no moderation detail, and only variants that are on sale. */
    public static ProductResponse forCustomer(ProductEntity entity) {
        return new ProductResponse(
                entity.getPublicId(),
                entity.getName(),
                entity.getSlug(),
                entity.getDescription(),
                entity.getBrand(),
                entity.getCategoryPublicId(),
                entity.getSellerPublicId(),
                entity.getStatus(),
                entity.getVariants().stream()
                        .filter(variant -> variant.isActive())
                        .map(VariantResponse::from)
                        .toList(),
                propertiesOf(entity),
                null,
                null,
                entity.getCreatedDate(),
                entity.getUpdatedDate(),
                entity.getShortDescription(),
                entity.getProductType(),
                entity.getTaxRatePercent(),
                entity.isPriceIncludesTax(),
                entity.getWeightGrams(),
                entity.getLengthMm(),
                entity.getWidthMm(),
                entity.getHeightMm(),
                entity.getPackageType(),
                entity.getShippingClass(),
                entity.isFreeShipping(),
                List.copyOf(entity.getDeliveryOptions()),
                entity.getReturnWindowDays(),
                entity.getWarrantyType(),
                entity.getWarrantyMonths(),
                entity.getSeoTitle(),
                entity.getMetaDescription(),
                List.copyOf(entity.getKeywords()),
                entity.getMedia().stream().map(MediaResponse::from).toList(),
                // An offer whose window has closed is not shown at all - a customer reading a
                // badge for a deal that ended is worse than seeing no badge.
                entity.getOffers().stream()
                        .filter(offer -> offer.isLiveAt(Instant.now()))
                        .map(offer -> OfferResponse.from(offer, Instant.now()))
                        .toList(),
                null);
    }

    /** Seller and operator view - every variant, plus why it was sent back. */
    public static ProductResponse forSeller(ProductEntity entity) {
        return new ProductResponse(
                entity.getPublicId(),
                entity.getName(),
                entity.getSlug(),
                entity.getDescription(),
                entity.getBrand(),
                entity.getCategoryPublicId(),
                entity.getSellerPublicId(),
                entity.getStatus(),
                entity.getVariants().stream().map(VariantResponse::forSeller).toList(),
                propertiesOf(entity),
                entity.getRejectionReason(),
                entity.getReviewedAt(),
                entity.getCreatedDate(),
                entity.getUpdatedDate(),
                entity.getShortDescription(),
                entity.getProductType(),
                entity.getTaxRatePercent(),
                entity.isPriceIncludesTax(),
                entity.getWeightGrams(),
                entity.getLengthMm(),
                entity.getWidthMm(),
                entity.getHeightMm(),
                entity.getPackageType(),
                entity.getShippingClass(),
                entity.isFreeShipping(),
                List.copyOf(entity.getDeliveryOptions()),
                entity.getReturnWindowDays(),
                entity.getWarrantyType(),
                entity.getWarrantyMonths(),
                entity.getSeoTitle(),
                entity.getMetaDescription(),
                List.copyOf(entity.getKeywords()),
                entity.getMedia().stream().map(MediaResponse::from).toList(),
                // Every offer, live or not: the seller is editing them and has to see the ones
                // that have not started and the ones that have finished.
                entity.getOffers().stream()
                        .map(offer -> OfferResponse.from(offer, Instant.now()))
                        .toList(),
                entity.getLastEditedAt());
    }
    /**
     * Regroups the flat value rows back into one entry per property.
     *
     * <p>Sorted by the stored order so a multi-valued answer comes back in the order the seller
     * chose rather than in whatever order the rows happened to be read.
     */
    private static Map<String, List<String>> propertiesOf(ProductEntity entity) {
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        entity.getProperties().stream()
                .sorted(java.util.Comparator.comparingInt(ProductPropertyValue::getValueOrder))
                .forEach(value -> grouped
                        .computeIfAbsent(value.getPropertyName(), key -> new java.util.ArrayList<>())
                        .add(value.getPropertyValue()));
        return grouped;
    }
}
