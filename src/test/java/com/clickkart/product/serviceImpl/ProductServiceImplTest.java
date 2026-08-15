// src/test/java/com/clickkart/product/serviceImpl/ProductServiceImplTest.java
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
import com.clickkart.product.dto.request.ProductRequest;
import com.clickkart.product.dto.request.ReviewDecisionRequest;
import com.clickkart.product.dto.request.VariantRequest;
import com.clickkart.product.dto.response.ProductResponse;
import com.clickkart.product.dto.response.PurchasableVariantResponse;
import com.clickkart.product.entity.ProductEntity;
import com.clickkart.product.entity.ProductVariantEntity;
import com.clickkart.product.enums.ProductAuditAction;
import com.clickkart.product.enums.ProductStatus;
import com.clickkart.product.exception.CategoryNotAssignableException;
import com.clickkart.product.exception.DuplicateSkuException;
import com.clickkart.product.exception.InvalidPriceException;
import com.clickkart.product.exception.InvalidProductStateException;
import com.clickkart.product.exception.ProductNotFoundException;
import com.clickkart.product.exception.SellerNotEligibleException;
import com.clickkart.product.feign.CategoryServiceClient;
import com.clickkart.product.feign.CategoryValidationApiResponse;
import com.clickkart.product.feign.SellerProfileApiResponse;
import com.clickkart.product.feign.UserServiceClient;
import com.clickkart.product.repository.ProductRepository;
import com.clickkart.product.repository.ProductVariantRepository;
import com.clickkart.product.service.AuditTrailService;
import com.clickkart.product.web.RequestMetadata;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductServiceImplTest {

    private static final String SELLER = "USR-seller";
    private static final String OTHER_SELLER = "USR-other";
    private static final String ADMIN = "USR-admin";
    private static final String CATEGORY = "CAT-leaf";
    private static final String CORRELATION_ID = "corr-1";
    private static final RequestMetadata METADATA = new RequestMetadata("203.0.113.7", "junit");

    @Mock private ProductRepository productRepository;
    @Mock private ProductVariantRepository variantRepository;
    @Mock private CategoryServiceClient categoryServiceClient;
    @Mock private UserServiceClient userServiceClient;
    @Mock private AuditTrailService auditTrailService;

    private ProductServiceImpl service;

    @BeforeEach
    void setUp() {
        ProductProperties properties = new ProductProperties();
        properties.setCategoryServiceApiKey("cat-key");
        properties.setUserServiceApiKey("user-key");
        service = new ProductServiceImpl(
                productRepository, variantRepository, categoryServiceClient, userServiceClient,
                auditTrailService, properties);
        when(productRepository.saveAndFlush(any(ProductEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        // Default happy path for both downstreams; individual tests override.
        when(userServiceClient.getSellerProfile(anyString(), anyString(), anyString()))
                .thenReturn(verifiedSeller());
        when(categoryServiceClient.validate(anyString(), anyString(), anyString()))
                .thenReturn(assignableCategory());
    }

    private SellerProfileApiResponse verifiedSeller() {
        return new SellerProfileApiResponse(true, new SellerProfileApiResponse.Data(SELLER, "Menon Traders", "VERIFIED"));
    }

    private CategoryValidationApiResponse assignableCategory() {
        return new CategoryValidationApiResponse(
                true, new CategoryValidationApiResponse.Data(CATEGORY, true, true, true, true, null));
    }

    private VariantRequest variant(String sku, String mrp, String price) {
        return new VariantRequest(sku, "Blue / M", new BigDecimal(mrp), new BigDecimal(price), Map.of("colour", "Blue"));
    }

    private ProductRequest request(String name, VariantRequest... variants) {
        return new ProductRequest(name, null, "desc", "Acme", CATEGORY, List.of(variants));
    }

    /** A persisted-looking listing owned by SELLER, in the given state. */
    private ProductEntity existing(String publicId, ProductStatus status) {
        ProductEntity product = ProductEntity.createFor(publicId, SELLER);
        ReflectionTestUtils.setField(product, "id", 1L);
        product.updateDetails("Widget", "widget", "desc", "Acme", CATEGORY);
        ProductVariantEntity variant = ProductVariantEntity.createWithSku("SKU-1");
        variant.update("Blue / M", new BigDecimal("100.00"), new BigDecimal("90.00"), Map.of());
        product.addVariant(variant);
        ReflectionTestUtils.setField(product, "status", status);
        when(productRepository.findByPublicId(publicId)).thenReturn(Optional.of(product));
        return product;
    }

    // ------------------------------------------------------------ creation

    @Test
    void aNewListingAlwaysStartsAsADraft() {
        ProductResponse created = service.createDraft(
                SELLER, request("Blue Widget", variant("sku-1", "100.00", "90.00")), CORRELATION_ID, METADATA);

        // A seller who could create something already ACTIVE would bypass review entirely.
        assertThat(created.status()).isEqualTo(ProductStatus.DRAFT);
        assertThat(created.publicId()).startsWith("PRD-");
        assertThat(created.sellerPublicId()).isEqualTo(SELLER);
        assertThat(created.slug()).isEqualTo("blue-widget");
    }

    @Test
    void creatingADraftDoesNotCallCategoryOrUserService() {
        // Drafting against a category being reorganised, or before verification completes, is
        // reasonable. Only publishing is gated - checking here would fail sellers for something
        // they can still fix.
        service.createDraft(SELLER, request("Widget", variant("sku-1", "100.00", "90.00")), CORRELATION_ID, METADATA);

        verify(categoryServiceClient, never()).validate(anyString(), anyString(), anyString());
        verify(userServiceClient, never()).getSellerProfile(anyString(), anyString(), anyString());
    }

    @Test
    void skusAreUppercasedSoTwoSellersCannotRegisterCaseVariants() {
        ProductResponse created = service.createDraft(
                SELLER, request("Widget", variant("sku-abc", "100.00", "90.00")), CORRELATION_ID, METADATA);

        assertThat(created.variants()).singleElement().satisfies(v -> assertThat(v.sku()).isEqualTo("SKU-ABC"));
    }

    @Test
    void aSellingPriceAboveTheMrpIsRefused() {
        // MRP is the figure a discount is advertised from; selling above it renders a negative
        // discount and misstates a legally meaningful number.
        assertThatThrownBy(() -> service.createDraft(
                        SELLER, request("Widget", variant("sku-1", "100.00", "150.00")), CORRELATION_ID, METADATA))
                .isInstanceOf(InvalidPriceException.class)
                .hasMessageContaining("SKU-1");
        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    void moneyKeepsTwoDecimalPlacesRatherThanDriftingAsAFloat() {
        ProductResponse created = service.createDraft(
                SELLER, request("Widget", variant("sku-1", "1999.999", "1499.994")), CORRELATION_ID, METADATA);

        assertThat(created.variants().get(0).mrp()).isEqualTo(new BigDecimal("2000.00"));
        assertThat(created.variants().get(0).sellingPrice()).isEqualTo(new BigDecimal("1499.99"));
    }

    @Test
    void twoVariantsSharingASkuAreRefusedBeforeTheDatabaseSeesThem() {
        assertThatThrownBy(() -> service.createDraft(
                        SELLER,
                        request("Widget", variant("sku-1", "100.00", "90.00"), variant("SKU-1", "200.00", "180.00")),
                        CORRELATION_ID, METADATA))
                .isInstanceOf(DuplicateSkuException.class);
    }

    @Test
    void aSkuAlreadyUsedElsewhereIsRefused() {
        when(variantRepository.existsBySku("SKU-1")).thenReturn(true);

        assertThatThrownBy(() -> service.createDraft(
                        SELLER, request("Widget", variant("sku-1", "100.00", "90.00")), CORRELATION_ID, METADATA))
                .isInstanceOf(DuplicateSkuException.class);
    }

    @Test
    void discountPercentageIsDerivedRatherThanStored() {
        ProductResponse created = service.createDraft(
                SELLER, request("Widget", variant("sku-1", "2000.00", "1500.00")), CORRELATION_ID, METADATA);

        assertThat(created.variants().get(0).discountPercentage()).isEqualTo(25);
    }

    // ----------------------------------------------------------- ownership

    @Test
    void anotherSellersListingIsIndistinguishableFromOneThatDoesNotExist() {
        existing("PRD-1", ProductStatus.DRAFT);

        // 404 rather than 403, so this cannot be used to enumerate a competitor's unpublished catalog.
        assertThatThrownBy(() -> service.getOwnProduct(OTHER_SELLER, "PRD-1"))
                .isInstanceOf(ProductNotFoundException.class);
        assertThatThrownBy(() -> service.submitForReview(OTHER_SELLER, "PRD-1", CORRELATION_ID, METADATA))
                .isInstanceOf(ProductNotFoundException.class);
        assertThatThrownBy(() -> service.archiveOwnProduct(OTHER_SELLER, "PRD-1", CORRELATION_ID, METADATA))
                .isInstanceOf(ProductNotFoundException.class);
        verify(auditTrailService, never()).record(any(), any(), any(), any(), any());
    }

    // ------------------------------------------------------------ lifecycle

    @Test
    void aListingUnderReviewIsFrozenAgainstSellerEdits() {
        existing("PRD-1", ProductStatus.PENDING_REVIEW);

        // Otherwise a seller passes moderation with acceptable content and swaps it afterwards,
        // which is the whole failure review exists to prevent.
        assertThatThrownBy(() -> service.updateOwnProduct(
                        SELLER, "PRD-1", request("Changed", variant("sku-2", "100.00", "90.00")),
                        CORRELATION_ID, METADATA))
                .isInstanceOf(InvalidProductStateException.class)
                .hasMessageContaining("PENDING_REVIEW");
    }

    @Test
    void aLiveListingCannotBeEditedInPlaceEither() {
        existing("PRD-1", ProductStatus.ACTIVE);

        assertThatThrownBy(() -> service.updateOwnProduct(
                        SELLER, "PRD-1", request("Changed", variant("sku-2", "100.00", "90.00")),
                        CORRELATION_ID, METADATA))
                .isInstanceOf(InvalidProductStateException.class);
    }

    @Test
    void submittingMovesADraftIntoTheQueueAndClearsAnyPriorRejection() {
        ProductEntity product = existing("PRD-1", ProductStatus.DRAFT);
        product.reject(ADMIN, "Poor photos");
        assertThat(product.getRejectionReason()).isNotNull();

        ProductResponse submitted = service.submitForReview(SELLER, "PRD-1", CORRELATION_ID, METADATA);

        assertThat(submitted.status()).isEqualTo(ProductStatus.PENDING_REVIEW);
        // Showing a stale reason next to a fresh submission would read as though it were current.
        assertThat(submitted.rejectionReason()).isNull();
    }

    @Test
    void onlyADraftCanBeSubmitted() {
        existing("PRD-1", ProductStatus.ACTIVE);

        assertThatThrownBy(() -> service.submitForReview(SELLER, "PRD-1", CORRELATION_ID, METADATA))
                .isInstanceOf(InvalidProductStateException.class);
    }

    @Test
    void archivingIsIdempotentAndDoesNotRecordASecondEvent() {
        existing("PRD-1", ProductStatus.ARCHIVED);

        service.archiveOwnProduct(SELLER, "PRD-1", CORRELATION_ID, METADATA);

        verify(auditTrailService, never())
                .record(any(), any(), eq(ProductAuditAction.PRODUCT_ARCHIVED), any(), any());
    }

    // --------------------------------------------------- cross-service gates

    @Test
    void anUnverifiedSellerCannotPublish() {
        existing("PRD-1", ProductStatus.DRAFT);
        when(userServiceClient.getSellerProfile(anyString(), anyString(), anyString()))
                .thenReturn(new SellerProfileApiResponse(
                        true, new SellerProfileApiResponse.Data(SELLER, "Menon Traders", "PENDING")));

        // The ROLE_SELLER claim says the platform granted the role, not that anyone checked the
        // business - only User Service knows that.
        assertThatThrownBy(() -> service.submitForReview(SELLER, "PRD-1", CORRELATION_ID, METADATA))
                .isInstanceOf(SellerNotEligibleException.class)
                .hasMessageContaining("PENDING");
        verify(categoryServiceClient, never()).validate(anyString(), anyString(), anyString());
    }

    @Test
    void aSellerWithNoBusinessProfileAtAllCannotPublish() {
        existing("PRD-1", ProductStatus.DRAFT);
        // What the Feign fallback returns for a 404 - a definitive "not eligible", not an outage.
        when(userServiceClient.getSellerProfile(anyString(), anyString(), anyString()))
                .thenReturn(new SellerProfileApiResponse(false, null));

        assertThatThrownBy(() -> service.submitForReview(SELLER, "PRD-1", CORRELATION_ID, METADATA))
                .isInstanceOf(SellerNotEligibleException.class);
    }

    @Test
    void anInteriorCategoryIsRefusedWithCategoryServicesOwnWording() {
        existing("PRD-1", ProductStatus.DRAFT);
        when(categoryServiceClient.validate(anyString(), anyString(), anyString()))
                .thenReturn(new CategoryValidationApiResponse(
                        true,
                        new CategoryValidationApiResponse.Data(
                                CATEGORY, true, true, false, false, "Products may only be listed against a leaf category")));

        assertThatThrownBy(() -> service.submitForReview(SELLER, "PRD-1", CORRELATION_ID, METADATA))
                .isInstanceOf(CategoryNotAssignableException.class)
                .hasMessageContaining("leaf");
    }

    @Test
    void theSellerCheckRunsBeforeTheCategoryCheck() {
        // Cheaper failure first, and the seller cannot act on a category problem until they are
        // allowed to sell at all.
        existing("PRD-1", ProductStatus.DRAFT);
        when(userServiceClient.getSellerProfile(anyString(), anyString(), anyString()))
                .thenReturn(new SellerProfileApiResponse(false, null));

        assertThatThrownBy(() -> service.submitForReview(SELLER, "PRD-1", CORRELATION_ID, METADATA))
                .isInstanceOf(SellerNotEligibleException.class);
        verify(categoryServiceClient, never()).validate(anyString(), anyString(), anyString());
    }

    // ----------------------------------------------------------- moderation

    @Test
    void approvingPutsAListingOnSaleAndIsAuditedAgainstTheOperator() {
        existing("PRD-1", ProductStatus.PENDING_REVIEW);

        ProductResponse decided = service.decideReview(
                "PRD-1", new ReviewDecisionRequest(true, null), ADMIN, CORRELATION_ID, METADATA);

        assertThat(decided.status()).isEqualTo(ProductStatus.ACTIVE);
        // The trail must answer who let this on sale.
        verify(auditTrailService)
                .record(eq(CORRELATION_ID), eq(ADMIN), eq(ProductAuditAction.PRODUCT_APPROVED), any(), anyString());
    }

    @Test
    void rejectingReturnsTheListingToDraftSoTheSellerCanFixIt() {
        existing("PRD-1", ProductStatus.PENDING_REVIEW);

        ProductResponse decided = service.decideReview(
                "PRD-1", new ReviewDecisionRequest(false, "Photos are too small"), ADMIN, CORRELATION_ID, METADATA);

        // A terminal rejected state would leave the seller with nothing to do.
        assertThat(decided.status()).isEqualTo(ProductStatus.DRAFT);
        assertThat(decided.rejectionReason()).isEqualTo("Photos are too small");
    }

    @Test
    void rejectingWithoutAReasonIsRefused() {
        existing("PRD-1", ProductStatus.PENDING_REVIEW);

        assertThatThrownBy(() -> service.decideReview(
                        "PRD-1", new ReviewDecisionRequest(false, "  "), ADMIN, CORRELATION_ID, METADATA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aListingNobodySubmittedCannotBeApproved() {
        existing("PRD-1", ProductStatus.DRAFT);

        // Otherwise an operator could publish something the seller never asked to publish.
        assertThatThrownBy(() -> service.decideReview(
                        "PRD-1", new ReviewDecisionRequest(true, null), ADMIN, CORRELATION_ID, METADATA))
                .isInstanceOf(InvalidProductStateException.class);
    }

    // ------------------------------------------------------------- internal

    @Test
    void anUnknownSkuIsAVerdictRatherThanAFailure() {
        when(variantRepository.findBySkuWithProduct("SKU-NOPE")).thenReturn(Optional.empty());

        PurchasableVariantResponse verdict = service.resolvePurchasableVariant("SKU-NOPE");

        assertThat(verdict.exists()).isFalse();
        assertThat(verdict.purchasable()).isFalse();
        assertThat(verdict.reason()).isEqualTo("No such SKU");
    }

    @Test
    void onlyAnActiveVariantOnALiveListingIsPurchasable() {
        ProductEntity live = existing("PRD-1", ProductStatus.ACTIVE);
        ProductVariantEntity variant = live.getVariants().get(0);
        when(variantRepository.findBySkuWithProduct("SKU-1")).thenReturn(Optional.of(variant));

        PurchasableVariantResponse verdict = service.resolvePurchasableVariant("SKU-1");
        assertThat(verdict.purchasable()).isTrue();
        // Price is returned so the caller can snapshot it rather than re-read a moving value later.
        assertThat(verdict.sellingPrice()).isEqualTo(new BigDecimal("90.00"));

        variant.activate(false);
        assertThat(service.resolvePurchasableVariant("SKU-1").purchasable()).isFalse();
    }

    @Test
    void aDraftListingIsNotPurchasableAndDoesNotSayWhy() {
        ProductEntity draft = existing("PRD-1", ProductStatus.DRAFT);
        when(variantRepository.findBySkuWithProduct("SKU-1")).thenReturn(Optional.of(draft.getVariants().get(0)));

        PurchasableVariantResponse verdict = service.resolvePurchasableVariant("SKU-1");

        assertThat(verdict.purchasable()).isFalse();
        // A competitor should not learn from this endpoint that a rival's listing is in review.
        assertThat(verdict.reason()).doesNotContain("DRAFT").doesNotContain("review");
    }

    @Test
    void thePublicViewHidesModerationDetailFromCustomers() {
        ProductEntity product = existing("PRD-1", ProductStatus.ACTIVE);
        product.reject(ADMIN, "internal note about the seller");
        ReflectionTestUtils.setField(product, "status", ProductStatus.ACTIVE);
        when(productRepository.findByPublicIdAndStatus("PRD-1", ProductStatus.ACTIVE)).thenReturn(Optional.of(product));

        ProductResponse customerView = service.getPublicProduct("PRD-1");

        // An operator's note written for the seller must never reach a shopper.
        assertThat(customerView.rejectionReason()).isNull();
        assertThat(customerView.reviewedAt()).isNull();
    }
}
