// src/main/java/com/clickkart/product/controller/BrandController.java
package com.clickkart.product.controller;

import com.clickkart.product.constant.ApiPaths;
import com.clickkart.product.constant.MdcKeys;
import com.clickkart.product.dto.ApiResponse;
import com.clickkart.product.dto.request.BrandRequest;
import com.clickkart.product.dto.response.BrandResponse;
import com.clickkart.product.security.AuthenticatedPrincipal;
import com.clickkart.product.service.BrandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Section 6. The brand vocabulary.
 *
 * <p>Reading is public - the customer-facing brand filter needs the same list, and a signed-out
 * shopper browsing by brand is the ordinary case. Adding requires a seller, because a brand
 * anybody could create is a spam surface rather than a vocabulary.
 */
@Tag(name = "Brands", description = "The shared brand vocabulary")
@RestController
@RequiredArgsConstructor
public class BrandController {

    private final BrandService brandService;

    @Operation(summary = "Active brands, optionally filtered")
    @GetMapping(ApiPaths.BRANDS)
    public ResponseEntity<ApiResponse<List<BrandResponse>>> search(
            @RequestParam(required = false) String q, HttpServletRequest request) {
        return envelope(HttpStatus.OK.value(), brandService.search(q), request);
    }

    /**
     * Adds a brand, or hands back the one it collides with.
     *
     * <p>201 either way. The seller asked for a brand to exist and it does; distinguishing "created"
     * from "was already there" would give the form two paths for one outcome.
     */
    @Operation(summary = "Add a brand to the shared list")
    @PostMapping(ApiPaths.BRANDS)
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<BrandResponse>> add(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @Valid @RequestBody BrandRequest body,
            HttpServletRequest request) {
        BrandResponse brand = brandService.addOrGet(principal.userId(), body);
        return envelope(HttpStatus.CREATED.value(), brand, request);
    }

    private <T> ResponseEntity<ApiResponse<T>> envelope(int status, T data, HttpServletRequest request) {
        String correlationId = MDC.get(MdcKeys.CORRELATION_ID);
        return ResponseEntity.status(status)
                .body(ApiResponse.success(status, data, request.getRequestURI(), correlationId));
    }
}
