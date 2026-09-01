// src/main/java/com/clickkart/product/controller/ProductCatalogController.java
package com.clickkart.product.controller;

import org.springframework.util.MultiValueMap;
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
import java.util.Map;
import java.util.List;
import java.util.LinkedHashMap;
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

    /** Namespace for specification facets, so they cannot collide with query, page or sort. */
    private static final String PROPERTY_PARAM_PREFIX = "prop.";

    private final ProductService productService;

    @Operation(summary = "Search the catalog, with optional category, brand and price filters")
    @GetMapping(ApiPaths.SEARCH)
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String categoryPublicId,
            @RequestParam(required = false) String brand,
            /**
             * Specification facets, as repeated params: {@code ?prop.RAM=8&prop.RAM=12&prop.Color=Black}.
             *
             * <p>A flat map rather than a structured body because this is a GET a shopper can
             * bookmark and share - a filtered listing that cannot be linked to is half a feature.
             */
            @RequestParam(required = false) MultiValueMap<String, String> allParams,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            Pageable pageable,
            HttpServletRequest request) {
        PageResponse<ProductResponse> page = PageResponse.from(
                productService.search(
                        query, categoryPublicId, brand, minPrice, maxPrice,
                        propertyFacets(allParams), pageable));
        return envelope(page, request);
    }

    /**
     * Null data when the price has not dropped, rather than a 404 or a zero.
     *
     * <p>"This has not fallen" is a perfectly good answer to the question, and the client renders
     * nothing for it - which is also what it renders for zero, so returning zero would only invite
     * someone to draw a "0% off" badge.
     */
    @Operation(summary = "How far this listing's price has fallen recently")
    @GetMapping(ApiPaths.PRICE_DROP)
    public ResponseEntity<ApiResponse<Integer>> priceDrop(
            @PathVariable String publicId, HttpServletRequest request) {
        return envelope(productService.priceDropPercent(publicId), request);
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
    /**
     * Pulls the {@code prop.*} params out of the query string.
     *
     * <p><strong>MultiValueMap, not {@code Map<String, List<String>>}.</strong> Spring only ever
     * populates the latter with one String per key - the declared {@code List} is erased, so it
     * compiles, binds, and then throws {@code ClassCastException} on the first read. Every
     * filtered search failed with a 500 that way: text query, brand, price and facets alike,
     * because they all pass through here. Only a request with no parameters at all worked.
     *
     * <p>Prefixed rather than free-form so a property can never collide with a real parameter -
     * a catalogue with a property called "query" or "page" would otherwise silently break paging.
     */
    private static Map<String, List<String>> propertyFacets(MultiValueMap<String, String> params) {
        if (params == null || params.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> facets = new LinkedHashMap<>();
        params.forEach((key, values) -> {
            if (key != null && key.startsWith(PROPERTY_PARAM_PREFIX) && values != null && !values.isEmpty()) {
                String name = key.substring(PROPERTY_PARAM_PREFIX.length());
                if (!name.isBlank()) {
                    // Copied: the container Spring hands over is reused across the request.
                    facets.put(name, List.copyOf(values));
                }
            }
        });
        return facets;
    }
}
