// src/test/java/com/clickkart/product/serviceImpl/ReviewServiceImplTest.java
package com.clickkart.product.serviceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.clickkart.product.feign.PurchaseCheckApiResponse;
import com.clickkart.product.feign.UserProfileApiResponse;
import com.clickkart.product.feign.UserServiceClient;
import com.clickkart.product.repository.ProductRepository;
import com.clickkart.product.repository.ProductReviewRepository;
import com.clickkart.product.service.AuditTrailService;
import com.clickkart.product.web.RequestMetadata;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewServiceImplTest {

    private static final String PRODUCT = "PRD-1";
    private static final String AUTHOR = "USR-author";
    private static final String OTHER = "USR-other";
    private static final String CORRELATION = "corr-1";
    private static final RequestMetadata METADATA = new RequestMetadata("203.0.113.7", "junit");

    @Mock private ProductReviewRepository reviewRepository;
    @Mock private ProductRepository productRepository;
    @Mock private OrderServiceClient orderServiceClient;
    @Mock private UserServiceClient userServiceClient;
    @Mock private AuditTrailService auditTrailService;

    private ProductEntity product;
    private ReviewServiceImpl service;

    /** A stand-in for the aggregate query, so a test can say what the table would report. */
    private record Summary(Double average, long total) implements ProductReviewRepository.RatingSummary {
        @Override
        public Double getAverage() {
            return average;
        }

        @Override
        public long getTotal() {
            return total;
        }
    }

    @BeforeEach
    void setUp() {
        ProductProperties properties = new ProductProperties();
        properties.setUserServiceApiKey("k");
        properties.setOrderServiceApiKey("k");

        product = ProductEntity.createFor(PRODUCT, "USR-seller");
        service = new ReviewServiceImpl(
                reviewRepository, productRepository, orderServiceClient, userServiceClient,
                auditTrailService, properties);

        when(productRepository.findByPublicId(PRODUCT)).thenReturn(Optional.of(product));
        when(userServiceClient.getProfile(anyString(), anyString(), anyString()))
                .thenReturn(new UserProfileApiResponse(true, new UserProfileApiResponse.Data(AUTHOR, "Asha")));
        when(orderServiceClient.hasPurchased(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new PurchaseCheckApiResponse(true, Boolean.FALSE));
        when(reviewRepository.ratingBreakdown(anyString())).thenReturn(List.of());
        when(reviewRepository.summarise(anyString())).thenReturn(new Summary(null, 0L));
    }

    private ReviewRequest request(short rating) {
        return new ReviewRequest(rating, "Good", "Does the job.");
    }

    /* ---- the aggregate, which is the part that goes quietly wrong ---- */

    @Test
    void theProductsRatingIsRecomputedFromTheTableRatherThanAdjusted() {
        when(reviewRepository.summarise(PRODUCT)).thenReturn(new Summary(4.3333333, 3L));

        service.submitOwn(PRODUCT, AUTHOR, request((short) 5), CORRELATION, METADATA);

        // Read back off the product, not off the request: an incremented running total drifts on
        // every edit and never corrects itself.
        assertThat(product.getRatingAverage()).isEqualByComparingTo("4.33");
        assertThat(product.getRatingCount()).isEqualTo(3);
    }

    @Test
    void aProductNobodyHasReviewedHasNoRatingRatherThanZero() {
        // The last review is deleted, so the table now reports nothing at all.
        ProductReviewEntity last = ProductReviewEntity.createFor("REV-1", product, AUTHOR, false);
        when(reviewRepository.findByProductPublicIdAndAuthorPublicId(PRODUCT, AUTHOR))
                .thenReturn(Optional.of(last));
        when(reviewRepository.summarise(PRODUCT)).thenReturn(new Summary(null, 0L));

        service.deleteOwn(PRODUCT, AUTHOR, CORRELATION, METADATA);

        // Zero would sort an unrated product below a genuinely bad one and render as no stars.
        assertThat(product.getRatingAverage()).isNull();
        assertThat(product.getRatingCount()).isZero();
    }

    @Test
    void hidingAReviewStopsItCounting() {
        ProductReviewEntity review = ProductReviewEntity.createFor("REV-1", product, AUTHOR, false);
        when(reviewRepository.findByPublicId("REV-1")).thenReturn(Optional.of(review));
        when(reviewRepository.summarise(PRODUCT)).thenReturn(new Summary(5.0, 1L));

        service.hide("REV-1", "Abusive", "USR-admin", CORRELATION, METADATA);

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.HIDDEN);
        // The aggregate is refreshed on hide as well as on write - missing that is exactly how a
        // removed review keeps affecting the score.
        assertThat(product.getRatingCount()).isEqualTo(1);
        verify(auditTrailService)
                .record(eq(CORRELATION), eq("USR-admin"), eq(ProductAuditAction.REVIEW_HIDDEN), eq(METADATA),
                        anyString());
    }

    @Test
    void restoringClearsTheReasonSoNoTraceIsLeft() {
        ProductReviewEntity review = ProductReviewEntity.createFor("REV-1", product, AUTHOR, false);
        review.hide("Abusive");
        when(reviewRepository.findByPublicId("REV-1")).thenReturn(Optional.of(review));

        service.restore("REV-1", "USR-admin", CORRELATION, METADATA);

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.PUBLISHED);
        assertThat(review.getHiddenReason()).isNull();
    }

    /* ---- one review per person per product ---- */

    @Test
    void submittingTwiceEditsTheFirstRatherThanAddingASecond() {
        ProductReviewEntity existing = ProductReviewEntity.createFor("REV-1", product, AUTHOR, true);
        existing.update((short) 2, "Meh", "Not great", "Asha");
        when(reviewRepository.findByProductPublicIdAndAuthorPublicId(PRODUCT, AUTHOR))
                .thenReturn(Optional.of(existing));

        service.submitOwn(PRODUCT, AUTHOR, request((short) 5), CORRELATION, METADATA);

        assertThat(existing.getRating()).isEqualTo((short) 5);
        // The verified badge survives the edit: it was earned by an order, not by the words.
        assertThat(existing.isVerifiedPurchase()).isTrue();
        // And no second row: one voice must not weight a rating as heavily as it likes.
        verify(orderServiceClient, never()).hasPurchased(anyString(), anyString(), anyString(), anyString());
    }

    /* ---- the verified badge ---- */

    @Test
    void theBadgeIsAskedOfOrderServiceOnceOnFirstWrite() {
        when(orderServiceClient.hasPurchased(eq(PRODUCT), eq(AUTHOR), anyString(), anyString()))
                .thenReturn(new PurchaseCheckApiResponse(true, Boolean.TRUE));

        ReviewResponse saved = service.submitOwn(PRODUCT, AUTHOR, request((short) 4), CORRELATION, METADATA);

        assertThat(saved.verifiedPurchase()).isTrue();
    }

    @Test
    void aReviewIsStillAcceptedWhenTheOrderCheckIsUnavailable() {
        // The fallback answers false rather than throwing. Losing what someone wrote for the sake
        // of a badge would be the wrong trade.
        when(orderServiceClient.hasPurchased(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new PurchaseCheckApiResponse(false, Boolean.FALSE));

        ReviewResponse saved = service.submitOwn(PRODUCT, AUTHOR, request((short) 4), CORRELATION, METADATA);

        assertThat(saved.verifiedPurchase()).isFalse();
        assertThat(saved.rating()).isEqualTo((short) 4);
    }

    /* ---- the byline ---- */

    @Test
    void theBylineComesFromUserServiceNotFromTheCaller() {
        ReviewResponse saved = service.submitOwn(PRODUCT, AUTHOR, request((short) 4), CORRELATION, METADATA);

        // A client that supplies its own name can supply someone else's.
        assertThat(saved.authorDisplayName()).isEqualTo("Asha");
    }

    @Test
    void anUnknownNameReadsAsACustomerRatherThanAsABlank() {
        when(userServiceClient.getProfile(anyString(), anyString(), anyString()))
                .thenReturn(new UserProfileApiResponse(false, null));

        ReviewResponse saved = service.submitOwn(PRODUCT, AUTHOR, request((short) 4), CORRELATION, METADATA);

        assertThat(saved.authorDisplayName()).isEqualTo("A customer");
    }

    /* ---- what a reader is allowed to see ---- */

    @Test
    void theHiddenReasonIsShownToItsAuthorAndNobodyElse() {
        ProductReviewEntity review = ProductReviewEntity.createFor("REV-1", product, AUTHOR, false);
        review.update((short) 1, "Bad", "Words", "Asha");
        review.hide("Names a competitor");

        // The author needs to know, or they write it again not knowing it was removed.
        assertThat(ReviewResponse.forReader(review, AUTHOR).hiddenReason()).isEqualTo("Names a competitor");
        // To everyone else it is simply absent; the reason is an operator's note, not public copy.
        assertThat(ReviewResponse.forReader(review, OTHER).hiddenReason()).isNull();
        assertThat(ReviewResponse.forReader(review, null).hiddenReason()).isNull();
    }

    @Test
    void mineIsFalseForAnAnonymousReader() {
        ProductReviewEntity review = ProductReviewEntity.createFor("REV-1", product, AUTHOR, false);

        assertThat(ReviewResponse.forReader(review, null).mine()).isFalse();
        assertThat(ReviewResponse.forReader(review, AUTHOR).mine()).isTrue();
    }

    /* ---- the summary a product page renders ---- */

    @Test
    void everyStarIsPresentEvenWhenNobodyGaveIt() {
        when(reviewRepository.summarise(PRODUCT)).thenReturn(new Summary(4.5, 2L));
        when(reviewRepository.ratingBreakdown(PRODUCT))
                .thenReturn(List.of(new Object[] {(short) 5, 1L}, new Object[] {(short) 4, 1L}));

        RatingSummaryResponse summary = service.summaryFor(PRODUCT);

        // A client rendering five bars should not have to fill the gaps itself.
        assertThat(summary.breakdown()).containsOnlyKeys((short) 1, (short) 2, (short) 3, (short) 4, (short) 5);
        assertThat(summary.breakdown().get((short) 5)).isEqualTo(1L);
        assertThat(summary.breakdown().get((short) 3)).isZero();
        assertThat(summary.average()).isEqualByComparingTo(new BigDecimal("4.50"));
        assertThat(summary.total()).isEqualTo(2);
    }

    /* ---- the unhappy paths ---- */

    @Test
    void reviewingSomethingThatDoesNotExistIs404() {
        when(productRepository.findByPublicId("PRD-nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submitOwn("PRD-nope", AUTHOR, request((short) 4), CORRELATION, METADATA))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void deletingAReviewYouNeverWroteIs404() {
        when(reviewRepository.findByProductPublicIdAndAuthorPublicId(PRODUCT, AUTHOR))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteOwn(PRODUCT, AUTHOR, CORRELATION, METADATA))
                .isInstanceOf(ReviewNotFoundException.class);
        verify(reviewRepository, never()).delete(any());
    }
}
