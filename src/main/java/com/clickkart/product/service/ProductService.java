// src/main/java/com/clickkart/product/service/ProductService.java
package com.clickkart.product.service;

import java.util.List;
import java.util.Map;
import com.clickkart.product.dto.request.ProductRequest;
import com.clickkart.product.dto.request.ReviewDecisionRequest;
import com.clickkart.product.dto.response.ProductResponse;
import com.clickkart.product.dto.response.PurchasableVariantResponse;
import com.clickkart.product.enums.ProductStatus;
import com.clickkart.product.web.RequestMetadata;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Every seller-facing method takes the caller's own {@code sellerPublicId} and scopes its work to
 * it. There is no variant that operates on an arbitrary seller, so "edit a competitor's listing" is
 * not a request this interface can express.
 */
public interface ProductService {

    // --- public catalog -------------------------------------------------

    /** Only ACTIVE listings, and only their active variants. */
    ProductResponse getPublicProduct(String publicId);

    ProductResponse getPublicProductBySlug(String slug);

    /** Free-text plus optional category, brand and price filters. ACTIVE only. */
    Page<ProductResponse> search(
            String query, String categoryPublicId, String brand,
            java.math.BigDecimal minPrice, java.math.BigDecimal maxPrice, Map<String, List<String>> properties, Pageable pageable);

    // --- seller ---------------------------------------------------------

    ProductResponse createDraft(
            String sellerPublicId, ProductRequest request, String correlationId, RequestMetadata metadata);

    ProductResponse updateOwnProduct(
            String sellerPublicId, String publicId, ProductRequest request, String correlationId, RequestMetadata metadata);

    /**
     * Moves a draft into the review queue, after checking the two things only other services know:
     * that the seller's business is verified, and that the category is a real, active leaf.
     */
    ProductResponse submitForReview(
            String sellerPublicId, String publicId, String correlationId, RequestMetadata metadata);

    ProductResponse archiveOwnProduct(
            String sellerPublicId, String publicId, String correlationId, RequestMetadata metadata);

    ProductResponse getOwnProduct(String sellerPublicId, String publicId);

    Page<ProductResponse> listOwnProducts(String sellerPublicId, ProductStatus status, Pageable pageable);

    // --- operator -------------------------------------------------------

    Page<ProductResponse> reviewQueue(Pageable pageable);

    ProductResponse decideReview(
            String publicId, ReviewDecisionRequest request, String reviewerPublicId, String correlationId,
            RequestMetadata metadata);

    // --- service-to-service ---------------------------------------------

    /** Cart/Order: is this SKU purchasable, and at what price. Never throws for an unknown SKU. */
    PurchasableVariantResponse resolvePurchasableVariant(String sku);

    /** Order: the full listing regardless of status, so a past order can still render what was bought. */
    ProductResponse getForInternalCaller(String publicId);
}
