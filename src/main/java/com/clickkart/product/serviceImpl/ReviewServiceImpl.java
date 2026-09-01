// src/main/java/com/clickkart/product/serviceImpl/ReviewServiceImpl.java
package com.clickkart.product.serviceImpl;

import com.clickkart.product.config.ProductProperties;
import com.clickkart.product.dto.request.ReviewRequest;
import com.clickkart.product.dto.response.RatingSummaryResponse;
import com.clickkart.product.dto.response.ReviewResponse;
import com.clickkart.product.entity.ProductEntity;
import com.clickkart.product.entity.ProductReviewEntity;
import com.clickkart.product.enums.ProductAuditAction;
import com.clickkart.product.enums.ReviewStatus;
import com.clickkart.product.exception.ProductNotFoundException;
import com.clickkart.product.exception.ReviewNotFoundException;
import com.clickkart.product.feign.OrderServiceClient;
import com.clickkart.product.feign.UserServiceClient;
import com.clickkart.product.repository.ProductRepository;
import com.clickkart.product.repository.ProductReviewRepository;
import com.clickkart.product.service.AuditTrailService;
import com.clickkart.product.service.ReviewService;
import com.clickkart.product.web.RequestMetadata;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customer reviews.
 *
 * <p><strong>The aggregate is recomputed, never adjusted.</strong> Every write ends by asking the
 * reviews table for the average and count and storing the answer on the product. Incrementing a
 * running total is cheaper and wrong in a way nobody notices for months: an edited rating, a hidden
 * review, a rolled-back transaction each leave the number a little off, and there is no point at
 * which it corrects itself. A recompute costs one aggregate query against an indexed column, on a
 * write that already touches the database.
 *
 * <p><strong>Reviews are not gated on purchase - only the badge is.</strong> Requiring an order
 * silences everyone who bought the same product elsewhere, and makes the review section a function
 * of this platform's order history rather than of the product itself. A shopper can weigh a
 * verified review against an unverified one; they cannot weigh reviews that were never written.
 *
 * <p><strong>Hidden reviews are kept.</strong> Hiding stops a review counting and stops others
 * seeing it, but its author still sees it with the reason - otherwise they write it again, not
 * knowing, and nobody learns anything.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private static final String PUBLIC_ID_PREFIX = "REV-";

    private final ProductReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final OrderServiceClient orderServiceClient;
    private final UserServiceClient userServiceClient;
    private final AuditTrailService auditTrailService;
    private final ProductProperties productProperties;

    @Override
    @Transactional(readOnly = true)
    public Page<ReviewResponse> listForProduct(
            String productPublicId, String viewerPublicId, Pageable pageable) {
        return reviewRepository
                .findByProductPublicIdAndStatusOrderByCreatedDateDesc(
                        productPublicId, ReviewStatus.PUBLISHED, pageable)
                .map(review -> ReviewResponse.forReader(review, viewerPublicId));
    }

    @Override
    @Transactional(readOnly = true)
    public RatingSummaryResponse summaryFor(String productPublicId) {
        var summary = reviewRepository.summarise(productPublicId);

        // Every star present, including the ones nobody gave: a client rendering five bars should
        // not have to fill the gaps, and a missing key reads as a bug rather than as a zero.
        Map<Short, Long> breakdown = new LinkedHashMap<>();
        for (short star = 5; star >= 1; star--) {
            breakdown.put(star, 0L);
        }
        for (Object[] row : reviewRepository.ratingBreakdown(productPublicId)) {
            breakdown.put(((Number) row[0]).shortValue(), ((Number) row[1]).longValue());
        }

        return new RatingSummaryResponse(roundedAverage(summary), (int) summary.getTotal(), breakdown);
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewResponse ownReview(String productPublicId, String authorPublicId) {
        return reviewRepository
                .findByProductPublicIdAndAuthorPublicId(productPublicId, authorPublicId)
                .map(review -> ReviewResponse.forReader(review, authorPublicId))
                .orElse(null);
    }

    @Override
    @Transactional
    public ReviewResponse submitOwn(
            String productPublicId,
            String authorPublicId,
            ReviewRequest request,
            String correlationId,
            RequestMetadata metadata) {

        ProductEntity product = productRepository
                .findByPublicId(productPublicId)
                .orElseThrow(() -> new ProductNotFoundException(productPublicId));

        ProductReviewEntity review = reviewRepository
                .findByProductPublicIdAndAuthorPublicId(productPublicId, authorPublicId)
                .orElseGet(() -> ProductReviewEntity.createFor(
                        PUBLIC_ID_PREFIX + UUID.randomUUID(),
                        product,
                        authorPublicId,
                        // Asked once, on first write. Recomputing per read would put a
                        // cross-service call in the path of every product page.
                        verifiedPurchase(productPublicId, authorPublicId, correlationId)));

        // The byline is read from User Service rather than taken from the caller: a client that
        // supplies its own name can supply anyone else's.
        review.update(
                request.rating(), trimToNull(request.title()), trimToNull(request.body()),
                displayNameOf(authorPublicId, correlationId));
        reviewRepository.saveAndFlush(review);

        // After the flush, so the aggregate sees this review's own rating.
        refreshRatingSummary(product);

        auditTrailService.record(correlationId, authorPublicId, ProductAuditAction.REVIEW_SUBMITTED, metadata,
                "productPublicId=" + productPublicId + " rating=" + request.rating());
        return ReviewResponse.forReader(review, authorPublicId);
    }

    @Override
    @Transactional
    public void deleteOwn(
            String productPublicId, String authorPublicId, String correlationId, RequestMetadata metadata) {
        ProductReviewEntity review = reviewRepository
                .findByProductPublicIdAndAuthorPublicId(productPublicId, authorPublicId)
                .orElseThrow(() -> new ReviewNotFoundException(productPublicId));

        ProductEntity product = review.getProduct();
        reviewRepository.delete(review);
        reviewRepository.flush();
        refreshRatingSummary(product);

        auditTrailService.record(correlationId, authorPublicId, ProductAuditAction.REVIEW_DELETED, metadata,
                "productPublicId=" + productPublicId);
    }

    @Override
    @Transactional
    public ReviewResponse hide(
            String reviewPublicId, String reason, String actorPublicId, String correlationId,
            RequestMetadata metadata) {
        ProductReviewEntity review = requireReview(reviewPublicId);
        review.hide(trimToNull(reason));
        reviewRepository.flush();
        refreshRatingSummary(review.getProduct());

        auditTrailService.record(correlationId, actorPublicId, ProductAuditAction.REVIEW_HIDDEN, metadata,
                "reviewPublicId=" + reviewPublicId + " reason=" + reason);
        return ReviewResponse.forReader(review, actorPublicId);
    }

    @Override
    @Transactional
    public ReviewResponse restore(
            String reviewPublicId, String actorPublicId, String correlationId, RequestMetadata metadata) {
        ProductReviewEntity review = requireReview(reviewPublicId);
        review.publish();
        reviewRepository.flush();
        refreshRatingSummary(review.getProduct());

        auditTrailService.record(correlationId, actorPublicId, ProductAuditAction.REVIEW_RESTORED, metadata,
                "reviewPublicId=" + reviewPublicId);
        return ReviewResponse.forReader(review, actorPublicId);
    }

    /* ---- internals ---- */

    private ProductReviewEntity requireReview(String reviewPublicId) {
        return reviewRepository
                .findByPublicId(reviewPublicId)
                .orElseThrow(() -> new ReviewNotFoundException(reviewPublicId));
    }

    /**
     * Reads the aggregate back out of the reviews table and stores it on the product.
     *
     * <p>Called after every write that could move it - a new review, an edit, a delete, a hide, a
     * restore. Missing one of those is the whole failure mode this design exists to prevent, which
     * is why it is one method called from five places rather than arithmetic inlined at each.
     */
    private void refreshRatingSummary(ProductEntity product) {
        var summary = reviewRepository.summarise(product.getPublicId());
        product.applyRatingSummary(roundedAverage(summary), (int) summary.getTotal());
        // flush(), not save(): the product is managed here, and save() would merge - which
        // re-snapshots its element collections and drops pending changes. See updateOwnProduct.
        productRepository.flush();
    }

    /**
     * Null when nothing is published, rather than zero.
     *
     * <p>"Not rated yet" and "rated zero" are different claims about someone's product, and only
     * one of them is ever true of a product nobody has reviewed. HALF_UP to two places, which is
     * what the column stores and what a client renders.
     */
    private static BigDecimal roundedAverage(ProductReviewRepository.RatingSummary summary) {
        Double average = summary == null ? null : summary.getAverage();
        return average == null ? null : BigDecimal.valueOf(average).setScale(2, RoundingMode.HALF_UP);
    }

    private String displayNameOf(String authorPublicId, String correlationId) {
        return userServiceClient
                .getProfile(authorPublicId, correlationId, productProperties.getUserServiceApiKey())
                .displayNameOrNull();
    }

    private boolean verifiedPurchase(String productPublicId, String authorPublicId, String correlationId) {
        return orderServiceClient
                .hasPurchased(
                        productPublicId, authorPublicId, correlationId,
                        productProperties.getOrderServiceApiKey())
                .purchased();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
