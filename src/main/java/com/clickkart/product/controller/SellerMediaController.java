// src/main/java/com/clickkart/product/controller/SellerMediaController.java
package com.clickkart.product.controller;

import com.clickkart.product.constant.ApiPaths;
import com.clickkart.product.constant.MdcKeys;
import com.clickkart.product.dto.request.MediaRequest;
import com.clickkart.product.dto.request.OfferRequest;
import com.clickkart.product.dto.ApiResponse;
import com.clickkart.product.dto.response.MediaResponse;
import com.clickkart.product.dto.response.OfferResponse;
import com.clickkart.product.security.AuthenticatedPrincipal;
import com.clickkart.product.service.MediaStorage;
import com.clickkart.product.service.ProductMediaService;
import com.clickkart.product.web.ClientIpResolver;
import com.clickkart.product.web.RequestMetadata;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Sections 8 to 10 and 13 — the seller's gallery and offer badges.
 *
 * <p>Upload is two steps on purpose. {@link #upload} takes the bytes, checks them and returns a
 * URL; {@link #attach} records that URL against a listing. Splitting them means the browser can
 * show a thumbnail the instant the file is stored, before the seller has decided the order or
 * written the alt text — and it means the same attach endpoint serves a file that was dragged in
 * and a URL the seller already hosts.
 */
@Tag(name = "Seller Media", description = "Product images, video and offers (SELLER only)")
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('SELLER')")
public class SellerMediaController {

    private final ProductMediaService productMediaService;
    private final MediaStorage mediaStorage;
    private final ClientIpResolver clientIpResolver;

    /**
     * Stores one file and hands back the URL to attach.
     *
     * <p>Nothing here trusts the filename or the declared content type — see
     * {@code LocalDiskMediaStorage} for what is actually checked and why.
     */
    @Operation(summary = "Upload a product image or video")
    @PostMapping(value = ApiPaths.SELLER_MEDIA_UPLOAD, consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<MediaStorage.StoredMedia>> upload(
            @RequestPart("file") MultipartFile file, HttpServletRequest request) throws IOException {
        MediaStorage.StoredMedia stored = mediaStorage.store(
                file.getBytes(), file.getOriginalFilename(), file.getContentType());
        return envelope(HttpStatus.CREATED.value(), stored, request);
    }

    @Operation(summary = "Every image this seller has uploaded, for reuse")
    @GetMapping(ApiPaths.SELLER_MEDIA_LIBRARY)
    public ResponseEntity<ApiResponse<List<MediaResponse>>> library(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @RequestParam(defaultValue = "60") int limit,
            HttpServletRequest request) {
        return envelope(HttpStatus.OK.value(), productMediaService.library(principal.userId(), limit), request);
    }

    @Operation(summary = "Attach an uploaded or hosted asset to a listing")
    @PostMapping(ApiPaths.SELLER_MEDIA)
    public ResponseEntity<ApiResponse<MediaResponse>> attach(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable String publicId,
            @Valid @RequestBody MediaRequest body,
            HttpServletRequest request) {
        MediaResponse created = productMediaService.attach(
                principal.userId(), publicId, body, MDC.get(MdcKeys.CORRELATION_ID), metadataOf(request));
        return envelope(HttpStatus.CREATED.value(), created, request);
    }

    @Operation(summary = "The gallery, in the order the seller arranged it")
    @GetMapping(ApiPaths.SELLER_MEDIA)
    public ResponseEntity<ApiResponse<List<MediaResponse>>> list(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable String publicId,
            HttpServletRequest request) {
        return envelope(HttpStatus.OK.value(), productMediaService.list(principal.userId(), publicId), request);
    }

    @Operation(summary = "Update an asset's alt text")
    @PutMapping(ApiPaths.SELLER_MEDIA_ONE)
    public ResponseEntity<ApiResponse<MediaResponse>> updateMetadata(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable String publicId,
            @PathVariable String mediaPublicId,
            @Valid @RequestBody MediaRequest body,
            HttpServletRequest request) {
        MediaResponse updated =
                productMediaService.updateMetadata(principal.userId(), publicId, mediaPublicId, body);
        return envelope(HttpStatus.OK.value(), updated, request);
    }

    /** Crop and rotate. Keeps the asset's position, primary flag and id — see the service. */
    @Operation(summary = "Replace the file behind an asset, keeping its place")
    @PutMapping(ApiPaths.SELLER_MEDIA_FILE)
    public ResponseEntity<ApiResponse<MediaResponse>> replaceFile(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable String publicId,
            @PathVariable String mediaPublicId,
            @Valid @RequestBody MediaRequest body,
            HttpServletRequest request) {
        MediaResponse updated =
                productMediaService.replaceFile(principal.userId(), publicId, mediaPublicId, body);
        return envelope(HttpStatus.OK.value(), updated, request);
    }

    /** Takes the whole ordering — see {@code ProductMediaService#reorder} for why. */
    @Operation(summary = "Reorder the gallery")
    @PutMapping(ApiPaths.SELLER_MEDIA_ORDER)
    public ResponseEntity<ApiResponse<List<MediaResponse>>> reorder(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable String publicId,
            @RequestBody List<String> mediaPublicIds,
            HttpServletRequest request) {
        List<MediaResponse> ordered = productMediaService.reorder(principal.userId(), publicId, mediaPublicIds);
        return envelope(HttpStatus.OK.value(), ordered, request);
    }

    @Operation(summary = "Promote one image to primary")
    @PutMapping(ApiPaths.SELLER_MEDIA_PRIMARY)
    public ResponseEntity<ApiResponse<List<MediaResponse>>> makePrimary(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable String publicId,
            @PathVariable String mediaPublicId,
            HttpServletRequest request) {
        List<MediaResponse> gallery =
                productMediaService.makePrimary(principal.userId(), publicId, mediaPublicId);
        return envelope(HttpStatus.OK.value(), gallery, request);
    }

    @Operation(summary = "Remove an asset from a listing")
    @DeleteMapping(ApiPaths.SELLER_MEDIA_ONE)
    public ResponseEntity<ApiResponse<Void>> detach(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable String publicId,
            @PathVariable String mediaPublicId,
            HttpServletRequest request) {
        productMediaService.detach(
                principal.userId(), publicId, mediaPublicId, MDC.get(MdcKeys.CORRELATION_ID), metadataOf(request));
        return envelope(HttpStatus.NO_CONTENT.value(), null, request);
    }

    /* ---- section 13 ---- */

    @Operation(summary = "Add an offer badge to a listing")
    @PostMapping(ApiPaths.SELLER_OFFERS)
    public ResponseEntity<ApiResponse<OfferResponse>> addOffer(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable String publicId,
            @Valid @RequestBody OfferRequest body,
            HttpServletRequest request) {
        return envelope(
                HttpStatus.CREATED.value(),
                productMediaService.addOffer(principal.userId(), publicId, body),
                request);
    }

    @Operation(summary = "Every offer on a listing, live or not")
    @GetMapping(ApiPaths.SELLER_OFFERS)
    public ResponseEntity<ApiResponse<List<OfferResponse>>> listOffers(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable String publicId,
            HttpServletRequest request) {
        return envelope(
                HttpStatus.OK.value(), productMediaService.listOffers(principal.userId(), publicId), request);
    }

    @Operation(summary = "Update an offer badge")
    @PutMapping(ApiPaths.SELLER_OFFER_ONE)
    public ResponseEntity<ApiResponse<OfferResponse>> updateOffer(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable String publicId,
            @PathVariable String offerPublicId,
            @Valid @RequestBody OfferRequest body,
            HttpServletRequest request) {
        return envelope(
                HttpStatus.OK.value(),
                productMediaService.updateOffer(principal.userId(), publicId, offerPublicId, body),
                request);
    }

    @Operation(summary = "Remove an offer badge")
    @DeleteMapping(ApiPaths.SELLER_OFFER_ONE)
    public ResponseEntity<ApiResponse<Void>> removeOffer(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable String publicId,
            @PathVariable String offerPublicId,
            HttpServletRequest request) {
        productMediaService.removeOffer(principal.userId(), publicId, offerPublicId);
        return envelope(HttpStatus.NO_CONTENT.value(), null, request);
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
