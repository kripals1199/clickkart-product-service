// src/main/java/com/clickkart/product/dto/response/ReviewResponse.java
package com.clickkart.product.dto.response;

import com.clickkart.product.entity.ProductReviewEntity;
import com.clickkart.product.enums.ReviewStatus;
import java.time.Instant;

/**
 * A review as a reader sees it.
 *
 * <p>{@code authorPublicId} is not here. It identifies a real person across every review they have
 * written, and publishing it would let anyone assemble one customer's whole purchase history from
 * public pages. The display name is what a reader needs, and {@code mine} answers the only question
 * the id was doing on the client - "may I edit this one?".
 */
public record ReviewResponse(
        String publicId,
        short rating,
        String title,
        String body,
        String authorDisplayName,
        boolean verifiedPurchase,
        ReviewStatus status,
        String hiddenReason,
        boolean mine,
        Instant createdDate,
        Instant updatedDate) {

    /** For a signed-out reader, or someone else's review. */
    public static ReviewResponse forReader(ProductReviewEntity entity, String viewerPublicId) {
        boolean mine = viewerPublicId != null && viewerPublicId.equals(entity.getAuthorPublicId());
        return new ReviewResponse(
                entity.getPublicId(),
                entity.getRating(),
                entity.getTitle(),
                entity.getBody(),
                displayName(entity),
                entity.isVerifiedPurchase(),
                entity.getStatus(),
                // Only the author is told why theirs was hidden; to everyone else a hidden review
                // is simply absent, and the reason is an operator's note, not public copy.
                mine ? entity.getHiddenReason() : null,
                mine,
                entity.getCreatedDate(),
                entity.getUpdatedDate());
    }

    /**
     * Falls back rather than leaving a blank byline. "A customer" is honest about what is known and
     * reads better than an empty space where a name should be.
     */
    private static String displayName(ProductReviewEntity entity) {
        String name = entity.getAuthorDisplayName();
        return name == null || name.isBlank() ? "A customer" : name;
    }
}
