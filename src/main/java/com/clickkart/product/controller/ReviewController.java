// src/main/java/com/clickkart/product/controller/ReviewController.java
package com.clickkart.product.controller;

import com.clickkart.product.constant.ApiPaths;
import com.clickkart.product.constant.MdcKeys;
import com.clickkart.product.dto.ApiResponse;
import com.clickkart.product.dto.request.ReviewRequest;
import com.clickkart.product.dto.response.RatingSummaryResponse;
import com.clickkart.product.dto.response.ReviewResponse;
import com.clickkart.product.security.AuthenticatedPrincipal;
import com.clickkart.product.service.ReviewService;
import com.clickkart.product.web.ClientIpResolver;
import com.clickkart.product.web.RequestMetadata;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer reviews of a product.
 *
 * <p><strong>Reading is anonymous, writing is not.</strong> A shopper deciding whether to buy is
 * exactly who reviews exist for, and a sign-in wall in front of them would leave the star ratings on
 * the listing pages unexplainable. Writing needs a session because a review has an author, and the
 * author is taken from the token rather than the body - a client that names its own author can name
 * someone else's.
 *
 * <p>The principal is nullable on the read paths: {@code @AuthenticationPrincipal} yields null for
 * an anonymous caller, and that is what decides whether a review comes back marked {@code mine}.
 */
@Tag(name = "Reviews", description = "Customer reviews and the rating they add up to")
@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;
    private final ClientIpResolver clientIpResolver;

    @Operation(summary = "Published reviews for a product, newest first")
    @GetMapping(ApiPaths.REVIEWS)
    public ResponseEntity<ApiResponse<Page<ReviewResponse>>> listReviews(
            @PathVariable String publicId,
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            Pageable pageable,
            HttpServletRequest request) {
        return envelope(
                HttpStatus.OK.value(),
                reviewService.listForProduct(publicId, viewerOf(principal), pageable),
                request);
    }

    @Operation(summary = "The star breakdown for a product")
    @GetMapping(ApiPaths.REVIEW_SUMMARY)
    public ResponseEntity<ApiResponse<RatingSummaryResponse>> summary(
            @PathVariable String publicId, HttpServletRequest request) {
        return envelope(HttpStatus.OK.value(), reviewService.summaryFor(publicId), request);
    }

    /**
     * The caller's own review, or 200 with a null body if they have not written one.
     *
     * <p>Not a 404: "you have not reviewed this" is a normal answer to a question the form asks on
     * every product page, and an error status would make every unreviewed product log like a fault.
     */
    @Operation(summary = "My review of this product, if any")
    @GetMapping(ApiPaths.REVIEW_MINE)
    public ResponseEntity<ApiResponse<ReviewResponse>> myReview(
            @PathVariable String publicId,
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            HttpServletRequest request) {
        return envelope(HttpStatus.OK.value(), reviewService.ownReview(publicId, principal.userId()), request);
    }

    /**
     * PUT, because one person has at most one review of a product: submitting again replaces what
     * is there rather than adding a second. Anything else lets one voice weight a rating as heavily
     * as it likes.
     */
    @Operation(summary = "Write or replace my review")
    @PutMapping(ApiPaths.REVIEW_MINE)
    public ResponseEntity<ApiResponse<ReviewResponse>> submitMyReview(
            @PathVariable String publicId,
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @Valid @RequestBody ReviewRequest reviewRequest,
            HttpServletRequest request) {
        ReviewResponse saved = reviewService.submitOwn(
                publicId, principal.userId(), reviewRequest, principal.correlationId(), metadataOf(request));
        return envelope(HttpStatus.OK.value(), saved, request);
    }

    @Operation(summary = "Delete my review")
    @DeleteMapping(ApiPaths.REVIEW_MINE)
    public ResponseEntity<ApiResponse<Void>> deleteMyReview(
            @PathVariable String publicId,
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            HttpServletRequest request) {
        reviewService.deleteOwn(publicId, principal.userId(), principal.correlationId(), metadataOf(request));
        return envelope(HttpStatus.NO_CONTENT.value(), null, request);
    }

    /**
     * Hide or restore a review.
     *
     * <p>PUT on a {@code /hidden} sub-resource rather than POST /hide and POST /restore: the thing
     * being changed is one boolean, and two verbs for two directions of one flag invites them to
     * drift apart.
     */
    @Operation(summary = "Hide or restore a review")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping(ApiPaths.ADMIN_REVIEW_HIDE)
    public ResponseEntity<ApiResponse<ReviewResponse>> setHidden(
            @PathVariable String reviewPublicId,
            @RequestParam boolean hidden,
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            HttpServletRequest request) {
        ReviewResponse result = hidden
                ? reviewService.hide(
                        reviewPublicId, reason, principal.userId(), principal.correlationId(),
                        metadataOf(request))
                : reviewService.restore(
                        reviewPublicId, principal.userId(), principal.correlationId(), metadataOf(request));
        return envelope(HttpStatus.OK.value(), result, request);
    }

    /** Null for an anonymous reader, which is a normal state on every read path here. */
    private static String viewerOf(AuthenticatedPrincipal principal) {
        return principal == null ? null : principal.userId();
    }

    private RequestMetadata metadataOf(HttpServletRequest request) {
        return new RequestMetadata(clientIpResolver.resolve(request), request.getHeader(HttpHeaders.USER_AGENT));
    }

    private <T> ResponseEntity<ApiResponse<T>> envelope(int status, T data, HttpServletRequest request) {
        String correlationId = MDC.get(MdcKeys.CORRELATION_ID);
        return ResponseEntity.status(status)
                .body(ApiResponse.success(status, data, request.getRequestURI(), correlationId));
    }
}
