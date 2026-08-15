// src/main/java/com/clickkart/product/controller/SellerProductController.java
package com.clickkart.product.controller;

import com.clickkart.product.constant.ApiPaths;
import com.clickkart.product.constant.MdcKeys;
import com.clickkart.product.dto.ApiResponse;
import com.clickkart.product.dto.PageResponse;
import com.clickkart.product.dto.request.ProductRequest;
import com.clickkart.product.dto.response.ProductResponse;
import com.clickkart.product.enums.ProductStatus;
import com.clickkart.product.security.AuthenticatedPrincipal;
import com.clickkart.product.service.ProductService;
import com.clickkart.product.web.ClientIpResolver;
import com.clickkart.product.web.RequestMetadata;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A seller's own listings. Requires {@code ROLE_SELLER}, which only Auth Service grants.
 *
 * <p>No route carries a seller id - the subject is always the token's own. As with User Service's
 * {@code /me}, that means "manage a competitor's listing" is not a request this API can express,
 * rather than a request it checks and refuses.
 *
 * <p>The role alone is not enough to publish: submission additionally requires a VERIFIED business
 * profile, which only User Service knows. That check is in the service layer, not here.
 */
@Tag(name = "Seller", description = "A seller's own listings (ROLE_SELLER)")
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('SELLER')")
public class SellerProductController {

    private final ProductService productService;
    private final ClientIpResolver clientIpResolver;

    @Operation(summary = "List the seller's own products, optionally filtered by status")
    @GetMapping(ApiPaths.SELLER_BASE)
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> listMine(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @RequestParam(required = false) ProductStatus status,
            Pageable pageable,
            HttpServletRequest request) {
        return envelope(HttpStatus.OK.value(),
                PageResponse.from(productService.listOwnProducts(principal.userId(), status, pageable)), request);
    }

    @Operation(summary = "Fetch one of the seller's own products, in any state")
    @GetMapping(ApiPaths.SELLER_PRODUCT)
    public ResponseEntity<ApiResponse<ProductResponse>> getMine(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable String publicId,
            HttpServletRequest request) {
        return envelope(HttpStatus.OK.value(), productService.getOwnProduct(principal.userId(), publicId), request);
    }

    /** 201 Created, always as a DRAFT - a seller cannot create something already on sale. */
    @Operation(summary = "Create a draft listing")
    @PostMapping(ApiPaths.SELLER_BASE)
    public ResponseEntity<ApiResponse<ProductResponse>> create(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @Valid @RequestBody ProductRequest body,
            HttpServletRequest request) {
        ProductResponse created = productService.createDraft(
                principal.userId(), body, principal.correlationId(), metadataOf(request));
        return envelope(HttpStatus.CREATED.value(), created, request);
    }

    /** Full replacement, variants included. Refused with 409 unless the listing is a DRAFT. */
    @Operation(summary = "Update a draft listing")
    @PutMapping(ApiPaths.SELLER_PRODUCT)
    public ResponseEntity<ApiResponse<ProductResponse>> update(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable String publicId,
            @Valid @RequestBody ProductRequest body,
            HttpServletRequest request) {
        ProductResponse updated = productService.updateOwnProduct(
                principal.userId(), publicId, body, principal.correlationId(), metadataOf(request));
        return envelope(HttpStatus.OK.value(), updated, request);
    }

    /**
     * Moves the listing into the review queue. This is where the seller's verification status and
     * the category are checked, so a 409 or 503 here is about those rather than the listing itself.
     */
    @Operation(summary = "Submit a draft for operator review")
    @PutMapping(ApiPaths.SELLER_SUBMIT)
    public ResponseEntity<ApiResponse<ProductResponse>> submit(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable String publicId,
            HttpServletRequest request) {
        ProductResponse submitted = productService.submitForReview(
                principal.userId(), publicId, principal.correlationId(), metadataOf(request));
        return envelope(HttpStatus.OK.value(), submitted, request);
    }

    @Operation(summary = "Withdraw a listing from sale")
    @PutMapping(ApiPaths.SELLER_ARCHIVE)
    public ResponseEntity<ApiResponse<ProductResponse>> archive(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable String publicId,
            HttpServletRequest request) {
        ProductResponse archived = productService.archiveOwnProduct(
                principal.userId(), publicId, principal.correlationId(), metadataOf(request));
        return envelope(HttpStatus.OK.value(), archived, request);
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
