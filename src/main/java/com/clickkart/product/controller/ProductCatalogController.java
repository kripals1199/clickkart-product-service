// src/main/java/com/clickkart/product/controller/ProductCatalogController.java
package com.clickkart.product.controller;

import com.clickkart.product.constant.ApiPaths;
import com.clickkart.product.constant.MdcKeys;
import com.clickkart.product.dto.ApiResponse;
import com.clickkart.product.dto.PageResponse;
import com.clickkart.product.dto.response.ProductResponse;
import com.clickkart.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The public shop front. No authentication, same reasoning as Category Service - a customer browses
 * before signing in.
 *
 * <p>Every route here goes through a service method that filters to {@code ACTIVE} in the query
 * itself. There is no parameter a caller can set to widen that: seeing a draft, a listing under
 * review, or a rejected one requires either owning it or being an operator, and those live on
 * different controllers.
 */
@Tag(name = "Catalog", description = "Public product browsing and search. No authentication required.")
@RestController
@RequiredArgsConstructor
public class ProductCatalogController {

    private final ProductService productService;

    @Operation(summary = "Search the catalog, with optional category, brand and price filters")
    @GetMapping(ApiPaths.SEARCH)
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String categoryPublicId,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            Pageable pageable,
            HttpServletRequest request) {
        PageResponse<ProductResponse> page = PageResponse.from(
                productService.search(query, categoryPublicId, brand, minPrice, maxPrice, pageable));
        return envelope(page, request);
    }

    @Operation(summary = "Fetch one product by its stable public id")
    @GetMapping(ApiPaths.BY_PUBLIC_ID)
    public ResponseEntity<ApiResponse<ProductResponse>> byPublicId(
            @PathVariable String publicId, HttpServletRequest request) {
        return envelope(productService.getPublicProduct(publicId), request);
    }

    @Operation(summary = "Resolve the product behind a customer-visible URL segment")
    @GetMapping(ApiPaths.BY_SLUG)
    public ResponseEntity<ApiResponse<ProductResponse>> bySlug(
            @PathVariable String slug, HttpServletRequest request) {
        return envelope(productService.getPublicProductBySlug(slug), request);
    }

    private <T> ResponseEntity<ApiResponse<T>> envelope(T data, HttpServletRequest request) {
        String correlationId = MDC.get(MdcKeys.CORRELATION_ID);
        return ResponseEntity.ok(
                ApiResponse.success(HttpStatus.OK.value(), data, request.getRequestURI(), correlationId));
    }
}
