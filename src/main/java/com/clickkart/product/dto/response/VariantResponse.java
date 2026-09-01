// src/main/java/com/clickkart/product/dto/response/VariantResponse.java
package com.clickkart.product.dto.response;

import com.clickkart.product.entity.ProductVariantEntity;
import java.math.BigDecimal;
import java.util.Map;

/**
 * One purchasable variant.
 *
 * <p>{@code discountPercentage} is computed rather than stored: a stored copy is a second source of
 * truth that silently goes stale the moment a price changes, and it is derivable in a subtraction.
 */
public record VariantResponse(
        String sku,
        String variantName,
        BigDecimal mrp,
        BigDecimal sellingPrice,
        int discountPercentage,
        Map<String, String> attributes,
        boolean active,
        /**
         * Section 11. Populated only for the seller who owns the listing.
         *
         * <p>Null on every customer-facing response, and by omission rather than by a filter
         * downstream: what a seller paid is competitive information, and a field that is only
         * sometimes stripped is one refactor away from being always sent.
         */
        java.math.BigDecimal costPrice) {

    public static VariantResponse from(ProductVariantEntity entity) {
        return new VariantResponse(
                entity.getSku(),
                entity.getVariantName(),
                entity.getMrp(),
                entity.getSellingPrice(),
                entity.discountPercentage(),
                // Copied, not passed through. attributes is a LAZY @ElementCollection, so handing
                // the proxy to the caller defers loading until Jackson serializes it - by which
                // point the transaction has closed and it throws LazyInitializationException.
                // Copying here forces the load while a session is still open. (open-in-view is
                // deliberately false, so there is no session at serialization time.)
                Map.copyOf(entity.getAttributes()),
                entity.isActive(),
                null);
    }

    /** As {@link #from}, and includes what the SKU cost. Never used on a public route. */
    public static VariantResponse forSeller(com.clickkart.product.entity.ProductVariantEntity entity) {
        VariantResponse base = from(entity);
        return new VariantResponse(
                base.sku(), base.variantName(), base.mrp(), base.sellingPrice(),
                base.discountPercentage(), base.attributes(), base.active(),
                entity.getCostPrice());
    }
}