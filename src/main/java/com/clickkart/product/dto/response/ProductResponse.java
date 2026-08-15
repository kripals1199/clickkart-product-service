// src/main/java/com/clickkart/product/dto/response/ProductResponse.java
package com.clickkart.product.dto.response;

import com.clickkart.product.entity.ProductEntity;
import com.clickkart.product.enums.ProductStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
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
                entity.getRejectionReason(),
                entity.getReviewedAt(),
                entity.getCreatedDate(),
                entity.getUpdatedDate());
    }
}
