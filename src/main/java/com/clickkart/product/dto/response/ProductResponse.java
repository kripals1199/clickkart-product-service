// src/main/java/com/clickkart/product/dto/response/ProductResponse.java
package com.clickkart.product.dto.response;

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
        Instant updatedDate) {

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
                entity.getUpdatedDate());
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
                entity.getVariants().stream().map(VariantResponse::from).toList(),
                propertiesOf(entity),
                entity.getRejectionReason(),
                entity.getReviewedAt(),
                entity.getCreatedDate(),
                entity.getUpdatedDate());
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
