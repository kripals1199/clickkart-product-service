// src/main/java/com/clickkart/product/controller/InternalProductController.java
package com.clickkart.product.controller;

import com.clickkart.product.constant.ApiPaths;
import com.clickkart.product.constant.MdcKeys;
import com.clickkart.product.dto.ApiResponse;
import com.clickkart.product.dto.response.ProductResponse;
import com.clickkart.product.dto.response.PurchasableVariantResponse;
import com.clickkart.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service reads for Cart and Order, shared-secret authenticated.
 *
 * <p>Exists separately from the public catalog for the same reason Category Service's does: these
 * callers need to see listings the public API hides. An order placed last month must still render
 * what was bought even after the seller archived it, and a public endpoint that returns only ACTIVE
 * listings cannot answer that - it would make order history decay as sellers tidy their catalogs.
 *
 * <p>Read-only, so a leaked key is a disclosure problem rather than a tampering one.
 */
@Tag(name = "Internal", description = "Service-to-service reads. Not routed through the Gateway.")
@RestController
@RequiredArgsConstructor
public class InternalProductController {

    private final ProductService productService;

    /**
     * The call Cart makes before adding a line item. Returns a verdict rather than 404-ing on an
     * unknown SKU, so the caller can distinguish "no such SKU" from "not on sale" and say which.
     * Carries the price so the caller can snapshot it - re-reading it later would silently change
     * what the customer agreed to.
     */
    @Operation(summary = "Is this SKU purchasable, and at what price?")
    @GetMapping(ApiPaths.INTERNAL_VARIANT_BY_SKU)
    public ResponseEntity<ApiResponse<PurchasableVariantResponse>> resolveVariant(
            @PathVariable String sku, HttpServletRequest request) {
        return envelope(productService.resolvePurchasableVariant(sku), request);
    }

    @Operation(summary = "Fetch a listing regardless of status, so past orders still render")
    @GetMapping(ApiPaths.INTERNAL_PRODUCT)
    public ResponseEntity<ApiResponse<ProductResponse>> getProduct(
            @PathVariable String publicId, HttpServletRequest request) {
        return envelope(productService.getForInternalCaller(publicId), request);
    }

    private <T> ResponseEntity<ApiResponse<T>> envelope(T data, HttpServletRequest request) {
        String correlationId = MDC.get(MdcKeys.CORRELATION_ID);
        return ResponseEntity.ok(
                ApiResponse.success(HttpStatus.OK.value(), data, request.getRequestURI(), correlationId));
    }
}
