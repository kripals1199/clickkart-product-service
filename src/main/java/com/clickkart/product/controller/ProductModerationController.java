// src/main/java/com/clickkart/product/controller/ProductModerationController.java
package com.clickkart.product.controller;

import com.clickkart.product.constant.ApiPaths;
import com.clickkart.product.constant.MdcKeys;
import com.clickkart.product.dto.ApiResponse;
import com.clickkart.product.dto.PageResponse;
import com.clickkart.product.dto.request.ReviewDecisionRequest;
import com.clickkart.product.dto.response.ProductResponse;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operator moderation - the gate between a seller submitting and a customer seeing.
 *
 * <p>Deliberately narrow: an operator approves, rejects, or reads the queue. They cannot edit a
 * listing's content. An operator who could quietly fix a description before approving it would make
 * the audit trail claim the seller published something they never wrote, and would blur who is
 * accountable for what is on sale.
 */
@Tag(name = "Moderation", description = "Listing review queue and decisions (ADMIN only)")
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ProductModerationController {

    private final ProductService productService;
    private final ClientIpResolver clientIpResolver;

    @Operation(summary = "Listings awaiting review, oldest first")
    @GetMapping(ApiPaths.ADMIN_QUEUE)
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> queue(
            Pageable pageable, HttpServletRequest request) {
        return envelope(PageResponse.from(productService.reviewQueue(pageable)), request);
    }

    /**
     * Approve or reject. A rejection requires a reason and returns the listing to DRAFT so the
     * seller can fix it, rather than leaving it in a state they cannot act on.
     */
    @Operation(summary = "Approve or reject a submitted listing")
    @PutMapping(ApiPaths.ADMIN_DECISION)
    public ResponseEntity<ApiResponse<ProductResponse>> decide(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable String publicId,
            @Valid @RequestBody ReviewDecisionRequest body,
            HttpServletRequest request) {
        ProductResponse decided = productService.decideReview(
                publicId, body, principal.userId(), principal.correlationId(),
                new RequestMetadata(clientIpResolver.resolve(request), request.getHeader(HttpHeaders.USER_AGENT)));
        return envelope(decided, request);
    }

    private <T> ResponseEntity<ApiResponse<T>> envelope(T data, HttpServletRequest request) {
        String correlationId = MDC.get(MdcKeys.CORRELATION_ID);
        return ResponseEntity.ok(
                ApiResponse.success(HttpStatus.OK.value(), data, request.getRequestURI(), correlationId));
    }
}
