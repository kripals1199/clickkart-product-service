// src/main/java/com/clickkart/product/service/ReviewService.java
package com.clickkart.product.service;

import com.clickkart.product.dto.request.ReviewRequest;
import com.clickkart.product.dto.response.RatingSummaryResponse;
import com.clickkart.product.dto.response.ReviewResponse;
import com.clickkart.product.web.RequestMetadata;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Customer reviews, and the rating they add up to. */
public interface ReviewService {

    /**
     * Published reviews for a product, newest first.
     *
     * @param viewerPublicId the signed-in reader, or null for an anonymous one - decides only which
     *     review is marked {@code mine}
     */
    Page<ReviewResponse> listForProduct(String productPublicId, String viewerPublicId, Pageable pageable);

    RatingSummaryResponse summaryFor(String productPublicId);

    /** The caller's own review of a product, if they have written one. */
    ReviewResponse ownReview(String productPublicId, String authorPublicId);

    /**
     * Writes or replaces the caller's review of a product.
     *
     * <p>One review per person per product: submitting again edits what is there rather than adding
     * a second. Anything else lets one voice weight a product's rating as heavily as it likes.
     */
    ReviewResponse submitOwn(
            String productPublicId,
            String authorPublicId,
            ReviewRequest request,
            String correlationId,
            RequestMetadata metadata);

    void deleteOwn(String productPublicId, String authorPublicId, String correlationId, RequestMetadata metadata);

    /** Operator action. The row survives, because a deleted review cannot be appealed. */
    ReviewResponse hide(String reviewPublicId, String reason, String actorPublicId, String correlationId,
            RequestMetadata metadata);

    ReviewResponse restore(String reviewPublicId, String actorPublicId, String correlationId,
            RequestMetadata metadata);
}
