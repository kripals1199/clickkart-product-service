// src/main/java/com/clickkart/product/dto/response/PurchasableVariantResponse.java
package com.clickkart.product.dto.response;

import com.clickkart.product.entity.ProductVariantEntity;
import java.math.BigDecimal;

/**
 * The answer Cart and Order need before putting a line item in a basket, in one call.
 *
 * <p>A verdict rather than a 404 on an unknown SKU, for the same reason Category Service returns
 * one: "no such SKU" and "exists but is not on sale" need different messages to the customer, and a
 * bare 404 collapses them.
 *
 * <p>{@code purchasable} is the single field to branch on. The price is included so the caller can
 * snapshot it at the moment of adding to cart - a cart that re-reads the live price would silently
 * change what the customer agreed to between adding and paying.
 */
public record PurchasableVariantResponse(
        String sku,
        String productPublicId,
        String productName,
        String variantName,
        String sellerPublicId,
        BigDecimal sellingPrice,
        BigDecimal mrp,
        boolean exists,
        boolean purchasable,
        String reason) {

    public static PurchasableVariantResponse notFound(String sku) {
        return new PurchasableVariantResponse(sku, null, null, null, null, null, null, false, false, "No such SKU");
    }

    public static PurchasableVariantResponse of(ProductVariantEntity variant) {
        var product = variant.getProduct();
        boolean purchasable = variant.isActive() && product.isPubliclyVisible();
        String reason;
        if (purchasable) {
            reason = null;
        } else if (!product.isPubliclyVisible()) {
            // Deliberately vague about *why* the listing is not live - a competitor should not learn
            // from this endpoint that a rival's product is sitting in review.
            reason = "This product is not currently on sale";
        } else {
            reason = "This variant is not currently on sale";
        }
        return new PurchasableVariantResponse(
                variant.getSku(),
                product.getPublicId(),
                product.getName(),
                variant.getVariantName(),
                product.getSellerPublicId(),
                variant.getSellingPrice(),
                variant.getMrp(),
                true,
                purchasable,
                reason);
    }
}
