// src/main/java/com/clickkart/product/serviceImpl/ProductServiceImpl.java
package com.clickkart.product.serviceImpl;

import com.clickkart.product.config.ProductProperties;
import com.clickkart.product.constant.LoggerNames;
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
import com.clickkart.product.exception.DuplicateSlugException;
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
import com.clickkart.product.repository.ProductSpecifications;
import com.clickkart.product.service.AuditTrailService;
import com.clickkart.product.service.ProductService;
import com.clickkart.product.util.SlugGenerator;
import com.clickkart.product.web.RequestMetadata;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j(topic = LoggerNames.SECURITY)
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private static final String PUBLIC_ID_PREFIX = "PRD-";

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final CategoryServiceClient categoryServiceClient;
    private final UserServiceClient userServiceClient;
    private final AuditTrailService auditTrailService;
    private final ProductProperties productProperties;

    // ---------------------------------------------------------- public catalog

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getPublicProduct(String publicId) {
        return ProductResponse.forCustomer(productRepository
                .findByPublicIdAndStatus(publicId, ProductStatus.ACTIVE)
                .orElseThrow(() -> new ProductNotFoundException(publicId)));
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getPublicProductBySlug(String slug) {
        return ProductResponse.forCustomer(productRepository
                .findBySlugAndStatus(slug, ProductStatus.ACTIVE)
                .orElseThrow(() -> new ProductNotFoundException(slug)));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> search(
            String query, String categoryPublicId, String brand, BigDecimal minPrice, BigDecimal maxPrice,
            Pageable pageable) {
        // The ACTIVE filter is baked into the specification rather than left to this call site, so a
        // future caller cannot compose a search that quietly exposes drafts.
        return productRepository
                .findAll(ProductSpecifications.publicSearch(query, categoryPublicId, brand, minPrice, maxPrice), pageable)
                .map(ProductResponse::forCustomer);
    }

    // ----------------------------------------------------------------- seller

    @Override
    @Transactional
    public ProductResponse createDraft(
            String sellerPublicId, ProductRequest request, String correlationId, RequestMetadata metadata) {
        String slug = resolveSlug(request, null);
        requireDistinctSkus(request.variants());
        requireSkusAvailable(request.variants(), Set.of());

        ProductEntity product = ProductEntity.createFor(PUBLIC_ID_PREFIX + UUID.randomUUID(), sellerPublicId);
        product.updateDetails(
                request.name().trim(), slug, trimToNull(request.description()),
                trimToNull(request.brand()), request.categoryPublicId().trim());
        request.variants().forEach(variantRequest -> product.addVariant(buildVariant(variantRequest)));
        productRepository.saveAndFlush(product);

        // The category is NOT validated here. Drafting against a category that is being reorganised
        // should not fail; what must not happen is going on sale against one. That check is at
        // submit, where it is decisive.
        auditTrailService.record(correlationId, sellerPublicId, ProductAuditAction.PRODUCT_CREATED, metadata,
                "publicId=" + product.getPublicId() + " slug=" + slug + " variants=" + request.variants().size());
        return ProductResponse.forSeller(product);
    }

    @Override
    @Transactional
    public ProductResponse updateOwnProduct(
            String sellerPublicId, String publicId, ProductRequest request, String correlationId,
            RequestMetadata metadata) {
        ProductEntity product = requireOwnedProduct(sellerPublicId, publicId);
        if (!product.isEditableBySeller()) {
            // A listing under review must not change beneath the operator reading it, and one that
            // is live must be archived and re-submitted rather than silently rewritten in place.
            throw new InvalidProductStateException(product.getStatus(), "edit");
        }

        String slug = resolveSlug(request, product);
        requireDistinctSkus(request.variants());
        Set<String> ownSkus = product.getVariants().stream()
                .map(ProductVariantEntity::getSku)
                .collect(java.util.stream.Collectors.toSet());
        requireSkusAvailable(request.variants(), ownSkus);

        product.updateDetails(
                request.name().trim(), slug, trimToNull(request.description()),
                trimToNull(request.brand()), request.categoryPublicId().trim());

        // Variants are replaced wholesale, which orphanRemoval turns into deletes for the ones that
        // went away. Safe only because a seller-editable product is a DRAFT by definition - no order
        // can reference a SKU that has never been on sale.
        List<ProductVariantEntity> existing = List.copyOf(product.getVariants());
        existing.forEach(product::removeVariant);
        request.variants().forEach(variantRequest -> product.addVariant(buildVariant(variantRequest)));
        productRepository.saveAndFlush(product);

        auditTrailService.record(correlationId, sellerPublicId, ProductAuditAction.PRODUCT_UPDATED, metadata,
                "publicId=" + publicId + " slug=" + slug + " variants=" + request.variants().size());
        return ProductResponse.forSeller(product);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Both external checks happen here and nowhere else. Doing them on every read would put two
     * network calls in the path of every catalog page for no benefit; doing them at draft time
     * would fail a seller for something they can fix before publishing. Submit is the moment the
     * answer becomes binding.
     */
    @Override
    @Transactional
    public ProductResponse submitForReview(
            String sellerPublicId, String publicId, String correlationId, RequestMetadata metadata) {
        ProductEntity product = requireOwnedProduct(sellerPublicId, publicId);
        if (product.getStatus() != ProductStatus.DRAFT) {
            throw new InvalidProductStateException(product.getStatus(), "submit");
        }

        requireVerifiedSeller(sellerPublicId, correlationId);
        requireAssignableCategory(product.getCategoryPublicId(), correlationId);

        product.submitForReview();
        auditTrailService.record(correlationId, sellerPublicId, ProductAuditAction.PRODUCT_SUBMITTED_FOR_REVIEW,
                metadata, "publicId=" + publicId + " category=" + product.getCategoryPublicId());
        return ProductResponse.forSeller(product);
    }

    @Override
    @Transactional
    public ProductResponse archiveOwnProduct(
            String sellerPublicId, String publicId, String correlationId, RequestMetadata metadata) {
        ProductEntity product = requireOwnedProduct(sellerPublicId, publicId);
        if (product.getStatus() == ProductStatus.ARCHIVED) {
            // Idempotent: a retried withdrawal is not an error, and re-running it would emit a
            // second audit entry for one action.
            return ProductResponse.forSeller(product);
        }
        product.archive();
        auditTrailService.record(correlationId, sellerPublicId, ProductAuditAction.PRODUCT_ARCHIVED, metadata,
                "publicId=" + publicId);
        return ProductResponse.forSeller(product);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getOwnProduct(String sellerPublicId, String publicId) {
        return ProductResponse.forSeller(requireOwnedProduct(sellerPublicId, publicId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> listOwnProducts(String sellerPublicId, ProductStatus status, Pageable pageable) {
        Page<ProductEntity> page = status == null
                ? productRepository.findBySellerPublicId(sellerPublicId, pageable)
                : productRepository.findBySellerPublicIdAndStatus(sellerPublicId, status, pageable);
        return page.map(ProductResponse::forSeller);
    }

    // --------------------------------------------------------------- operator

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> reviewQueue(Pageable pageable) {
        return productRepository.findByStatus(ProductStatus.PENDING_REVIEW, pageable).map(ProductResponse::forSeller);
    }

    @Override
    @Transactional
    public ProductResponse decideReview(
            String publicId, ReviewDecisionRequest request, String reviewerPublicId, String correlationId,
            RequestMetadata metadata) {
        boolean approved = Boolean.TRUE.equals(request.approved());
        if (!approved && (request.reason() == null || request.reason().isBlank())) {
            throw new IllegalArgumentException("A reason is required when rejecting a listing");
        }

        ProductEntity product =
                productRepository.findByPublicId(publicId).orElseThrow(() -> new ProductNotFoundException(publicId));
        if (product.getStatus() != ProductStatus.PENDING_REVIEW) {
            // Approving something nobody submitted would put a listing on sale that the seller never
            // asked to publish.
            throw new InvalidProductStateException(product.getStatus(), approved ? "approve" : "reject");
        }

        if (approved) {
            product.approve(reviewerPublicId);
        } else {
            product.reject(reviewerPublicId, request.reason().trim());
        }

        // Attributed to the operator, not the seller - the trail must answer who let this on sale.
        auditTrailService.record(correlationId, reviewerPublicId,
                approved ? ProductAuditAction.PRODUCT_APPROVED : ProductAuditAction.PRODUCT_REJECTED, metadata,
                "publicId=" + publicId + " seller=" + product.getSellerPublicId());
        return ProductResponse.forSeller(product);
    }

    // ------------------------------------------------------- service-to-service

    @Override
    @Transactional(readOnly = true)
    public PurchasableVariantResponse resolvePurchasableVariant(String sku) {
        return variantRepository
                .findBySkuWithProduct(sku)
                .map(PurchasableVariantResponse::of)
                .orElseGet(() -> PurchasableVariantResponse.notFound(sku));
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getForInternalCaller(String publicId) {
        return ProductResponse.forSeller(
                productRepository.findByPublicId(publicId).orElseThrow(() -> new ProductNotFoundException(publicId)));
    }

    // ---------------------------------------------------------------- helpers

    /**
     * The single ownership gate. Resolving by (publicId, seller) means another seller's listing is
     * indistinguishable from one that does not exist - both 404 - so this cannot be used to
     * enumerate a competitor's unpublished catalog.
     */
    private ProductEntity requireOwnedProduct(String sellerPublicId, String publicId) {
        ProductEntity product =
                productRepository.findByPublicId(publicId).orElseThrow(() -> new ProductNotFoundException(publicId));
        if (!product.isOwnedBy(sellerPublicId)) {
            log.warn("PRODUCT_ACCESS_DENIED publicId={} requestedBy={} owner={}",
                    publicId, sellerPublicId, product.getSellerPublicId());
            throw new ProductNotFoundException(publicId);
        }
        return product;
    }

    private void requireVerifiedSeller(String sellerPublicId, String correlationId) {
        SellerProfileApiResponse seller = userServiceClient.getSellerProfile(
                sellerPublicId, correlationId, productProperties.getUserServiceApiKey());
        if (!seller.isVerified()) {
            log.warn("SUBMISSION_REFUSED_UNVERIFIED_SELLER sellerPublicId={} status={} correlationId={}",
                    sellerPublicId, seller.verificationStatusOrUnknown(), correlationId);
            throw new SellerNotEligibleException(
                    "Your seller profile must be verified before you can list products (currently "
                            + seller.verificationStatusOrUnknown() + ")");
        }
    }

    private void requireAssignableCategory(String categoryPublicId, String correlationId) {
        CategoryValidationApiResponse verdict = categoryServiceClient.validate(
                categoryPublicId, correlationId, productProperties.getCategoryServiceApiKey());
        if (!verdict.isAssignable()) {
            log.warn("SUBMISSION_REFUSED_CATEGORY categoryPublicId={} reason={} correlationId={}",
                    categoryPublicId, verdict.reasonOrDefault(), correlationId);
            // Category Service's own wording, not a generic message - "pick a more specific
            // category" and "that category is hidden" need different actions from the seller.
            throw new CategoryNotAssignableException(verdict.reasonOrDefault());
        }
    }

    private ProductVariantEntity buildVariant(VariantRequest request) {
        // Normalised before it appears in any message, so the seller sees the SKU the system will
        // actually use rather than the casing they happened to type - every other reference to it,
        // including the duplicate-SKU error, is uppercase.
        String sku = normaliseSku(request.sku());
        if (request.sellingPrice().compareTo(request.mrp()) > 0) {
            // MRP is the price a discount is advertised from; selling above it renders a negative
            // discount and, in India, misstates a legally meaningful figure.
            throw new InvalidPriceException(
                    "Selling price " + request.sellingPrice() + " cannot exceed the MRP " + request.mrp()
                            + " for SKU " + sku);
        }
        ProductVariantEntity variant = ProductVariantEntity.createWithSku(sku);
        variant.update(request.variantName().trim(), request.mrp(), request.sellingPrice(), request.attributes());
        return variant;
    }

    /** Uppercased so two sellers cannot register SKUs differing only in case and confuse a picker. */
    private static String normaliseSku(String sku) {
        return sku.trim().toUpperCase(Locale.ROOT);
    }

    /** Caught before the database so the seller sees which SKU is duplicated, not a constraint name. */
    private void requireDistinctSkus(List<VariantRequest> variants) {
        Set<String> seen = new HashSet<>();
        for (VariantRequest variant : variants) {
            if (!seen.add(normaliseSku(variant.sku()))) {
                throw new DuplicateSkuException(normaliseSku(variant.sku()));
            }
        }
    }

    /**
     * @param ownSkus SKUs already held by the product being updated, which are not conflicts with
     *     itself - without this a seller could not save a listing twice without changing every SKU
     */
    private void requireSkusAvailable(List<VariantRequest> variants, Set<String> ownSkus) {
        for (VariantRequest variant : variants) {
            String sku = normaliseSku(variant.sku());
            if (!ownSkus.contains(sku) && variantRepository.existsBySku(sku)) {
                throw new DuplicateSkuException(sku);
            }
        }
    }

    private String resolveSlug(ProductRequest request, ProductEntity existing) {
        String candidate = request.slug() == null || request.slug().isBlank()
                ? SlugGenerator.slugify(request.name())
                : request.slug().trim();
        if (candidate.isEmpty()) {
            throw new IllegalArgumentException(
                    "A slug could not be derived from this name - please supply one explicitly");
        }
        if (existing != null && candidate.equals(existing.getSlug())) {
            return candidate;
        }
        if (productRepository.existsBySlug(candidate)) {
            throw new DuplicateSlugException(candidate);
        }
        return candidate;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
