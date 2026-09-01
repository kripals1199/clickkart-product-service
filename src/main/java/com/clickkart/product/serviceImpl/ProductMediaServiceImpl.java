// src/main/java/com/clickkart/product/serviceImpl/ProductMediaServiceImpl.java
package com.clickkart.product.serviceImpl;

import com.clickkart.product.dto.request.MediaRequest;
import com.clickkart.product.dto.request.OfferRequest;
import com.clickkart.product.dto.response.MediaResponse;
import com.clickkart.product.dto.response.OfferResponse;
import com.clickkart.product.entity.ProductEntity;
import com.clickkart.product.entity.ProductMediaEntity;
import com.clickkart.product.entity.ProductOfferEntity;
import com.clickkart.product.enums.MediaType;
import com.clickkart.product.enums.ProductAuditAction;
import com.clickkart.product.exception.InvalidProductStateException;
import com.clickkart.product.exception.ProductNotFoundException;
import com.clickkart.product.repository.ProductRepository;
import com.clickkart.product.service.AuditTrailService;
import com.clickkart.product.service.MediaStorage;
import com.clickkart.product.service.ProductMediaService;
import com.clickkart.product.web.RequestMetadata;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sections 8 to 10 and 13. See {@link ProductMediaService} for why this is separate. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductMediaServiceImpl implements ProductMediaService {

    private static final String MEDIA_PREFIX = "PMD-";
    private static final String OFFER_PREFIX = "POF-";

    /** Section 8: recommended 5-7, and a hard stop well above it rather than at it. */
    private static final int MAX_MEDIA = 15;
    private static final int MAX_OFFERS = 10;

    private final ProductRepository productRepository;
    private final MediaStorage mediaStorage;
    private final AuditTrailService auditTrailService;

    @Override
    @Transactional
    public MediaResponse attach(
            String sellerPublicId, String productPublicId, MediaRequest request,
            String correlationId, RequestMetadata metadata) {
        ProductEntity product = requireEditable(sellerPublicId, productPublicId);
        if (product.getMedia().size() >= MAX_MEDIA) {
            throw new InvalidProductStateException(
                    product.getStatus(), "add more than " + MAX_MEDIA + " media items to");
        }

        ProductMediaEntity asset = ProductMediaEntity.of(
                MEDIA_PREFIX + UUID.randomUUID(), request.mediaType(), request.url());
        asset.update(
                request.url(), trimToNull(request.altText()),
                request.widthPx(), request.heightPx(), request.durationSeconds());
        asset.placeAt(product.getMedia().size());
        asset.scoreQuality(scoreImage(request));
        product.addMedia(asset);

        // The first image leads the gallery without the seller having to say so. A lead image that
        // is whichever row came back first is not a decision anyone made.
        boolean noPrimaryYet = product.getMedia().stream().noneMatch(ProductMediaEntity::isPrimary);
        if (noPrimaryYet && request.mediaType() == MediaType.IMAGE) {
            product.makePrimary(asset);
        }

        product.touchEdited(Instant.now());
        productRepository.flush();
        auditTrailService.record(correlationId, sellerPublicId, ProductAuditAction.PRODUCT_UPDATED, metadata,
                "publicId=" + productPublicId + " mediaAdded=" + asset.getPublicId());
        return MediaResponse.from(asset);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MediaResponse> list(String sellerPublicId, String productPublicId) {
        return requireOwned(sellerPublicId, productPublicId).getMedia().stream()
                .map(MediaResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MediaResponse> library(String sellerPublicId, int limit) {
        // Capped whatever the caller asks for: this is a picker, and a seller with a thousand
        // images does not want them all in one response.
        int capped = Math.min(Math.max(limit, 1), 100);
        return productRepository
                .findMediaForSeller(sellerPublicId, org.springframework.data.domain.PageRequest.of(0, capped))
                .stream()
                .map(MediaResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public MediaResponse updateMetadata(
            String sellerPublicId, String productPublicId, String mediaPublicId, MediaRequest request) {
        ProductEntity product = requireEditable(sellerPublicId, productPublicId);
        ProductMediaEntity asset = findMedia(product, mediaPublicId);
        asset.update(
                asset.getUrl(), trimToNull(request.altText()),
                request.widthPx() == null ? asset.getWidthPx() : request.widthPx(),
                request.heightPx() == null ? asset.getHeightPx() : request.heightPx(),
                request.durationSeconds() == null ? asset.getDurationSeconds() : request.durationSeconds());
        asset.scoreQuality(scoreImage(request));
        product.touchEdited(Instant.now());
        productRepository.flush();
        return MediaResponse.from(asset);
    }

    @Override
    @Transactional
    public MediaResponse replaceFile(
            String sellerPublicId, String productPublicId, String mediaPublicId, MediaRequest request) {
        ProductEntity product = requireEditable(sellerPublicId, productPublicId);
        ProductMediaEntity asset = findMedia(product, mediaPublicId);

        // A crop cannot turn an image into a video, and allowing the type to change here would
        // let a video be swapped under an asset that is currently the primary image.
        if (asset.getMediaType() != request.mediaType()) {
            throw new InvalidProductStateException(
                    product.getStatus(), "change the type of an existing asset on");
        }

        String previousUrl = asset.getUrl();
        if (previousUrl.equals(request.url())) {
            return MediaResponse.from(asset);
        }

        asset.update(
                request.url(),
                // Alt text survives an edit: a crop does not change what the picture is of.
                asset.getAltText(),
                request.widthPx(), request.heightPx(), request.durationSeconds());
        // Rescored, because cropping changes the two things the score is actually measured on.
        asset.scoreQuality(scoreImage(new MediaRequest(
                request.mediaType(), request.url(), asset.getAltText(),
                request.widthPx(), request.heightPx(), request.durationSeconds())));

        product.touchEdited(Instant.now());
        productRepository.flush();

        // Only once the row points at the new file.
        mediaStorage.delete(previousUrl);
        log.info("MEDIA_FILE_REPLACED product={} media={}", productPublicId, mediaPublicId);
        return MediaResponse.from(asset);
    }

    @Override
    @Transactional
    public List<MediaResponse> reorder(
            String sellerPublicId, String productPublicId, List<String> mediaPublicIds) {
        ProductEntity product = requireEditable(sellerPublicId, productPublicId);

        Map<String, ProductMediaEntity> byId = new LinkedHashMap<>();
        product.getMedia().forEach(asset -> byId.put(asset.getPublicId(), asset));

        // The whole ordering, or none of it. A list that has lost or gained an id is not a reorder -
        // it is a stale client, and applying it would silently drop whatever it forgot to mention.
        if (mediaPublicIds == null || mediaPublicIds.size() != byId.size()
                || !byId.keySet().containsAll(mediaPublicIds)) {
            throw new InvalidProductStateException(product.getStatus(), "reorder media with a partial list on");
        }

        int position = 0;
        for (String id : mediaPublicIds) {
            byId.get(id).placeAt(position++);
        }
        product.touchEdited(Instant.now());
        productRepository.flush();
        return sortedMedia(product);
    }

    @Override
    @Transactional
    public List<MediaResponse> makePrimary(
            String sellerPublicId, String productPublicId, String mediaPublicId) {
        ProductEntity product = requireEditable(sellerPublicId, productPublicId);
        ProductMediaEntity chosen = findMedia(product, mediaPublicId);
        if (chosen.getMediaType() != MediaType.IMAGE) {
            // A video renders as a black frame anywhere a still is expected, and none of those
            // surfaces can play it.
            throw new InvalidProductStateException(product.getStatus(), "use a video as the primary image of");
        }
        product.makePrimary(chosen);
        product.touchEdited(Instant.now());
        productRepository.flush();
        return sortedMedia(product);
    }

    @Override
    @Transactional
    public void detach(
            String sellerPublicId, String productPublicId, String mediaPublicId,
            String correlationId, RequestMetadata metadata) {
        ProductEntity product = requireEditable(sellerPublicId, productPublicId);
        ProductMediaEntity asset = findMedia(product, mediaPublicId);
        boolean wasPrimary = asset.isPrimary();
        String url = asset.getUrl();

        product.removeMedia(asset);

        // Close the gap, so display_order stays 0..n-1 and a later reorder is not asked to place
        // items into positions that no longer exist.
        int position = 0;
        for (ProductMediaEntity remaining : sortedEntities(product)) {
            remaining.placeAt(position++);
        }

        // A listing with images but no primary renders an empty frame on every card that shows it.
        if (wasPrimary) {
            sortedEntities(product).stream()
                    .filter(candidate -> candidate.getMediaType() == MediaType.IMAGE)
                    .findFirst()
                    .ifPresent(product::makePrimary);
        }

        product.touchEdited(Instant.now());
        productRepository.flush();

        // The stored file goes only after the row is gone. The other order leaves a listing
        // pointing at a file that no longer exists if the write fails.
        mediaStorage.delete(url);
        auditTrailService.record(correlationId, sellerPublicId, ProductAuditAction.PRODUCT_UPDATED, metadata,
                "publicId=" + productPublicId + " mediaRemoved=" + mediaPublicId);
    }

    /* ---- section 13 ---- */

    @Override
    @Transactional
    public OfferResponse addOffer(String sellerPublicId, String productPublicId, OfferRequest request) {
        ProductEntity product = requireEditable(sellerPublicId, productPublicId);
        if (product.getOffers().size() >= MAX_OFFERS) {
            throw new InvalidProductStateException(
                    product.getStatus(), "add more than " + MAX_OFFERS + " offers to");
        }
        ProductOfferEntity offer = ProductOfferEntity.of(
                OFFER_PREFIX + UUID.randomUUID(), request.offerType(), request.label().trim());
        offer.update(
                request.label().trim(), trimToNull(request.code()),
                request.startsAt(), request.endsAt(), request.active());
        offer.placeAt(product.getOffers().size());
        product.addOffer(offer);
        product.touchEdited(Instant.now());
        productRepository.flush();
        return OfferResponse.from(offer, Instant.now());
    }

    @Override
    @Transactional(readOnly = true)
    public List<OfferResponse> listOffers(String sellerPublicId, String productPublicId) {
        Instant now = Instant.now();
        return requireOwned(sellerPublicId, productPublicId).getOffers().stream()
                .map(offer -> OfferResponse.from(offer, now))
                .toList();
    }

    @Override
    @Transactional
    public OfferResponse updateOffer(
            String sellerPublicId, String productPublicId, String offerPublicId, OfferRequest request) {
        ProductEntity product = requireEditable(sellerPublicId, productPublicId);
        ProductOfferEntity offer = product.getOffers().stream()
                .filter(candidate -> candidate.getPublicId().equals(offerPublicId))
                .findFirst()
                .orElseThrow(() -> new ProductNotFoundException(offerPublicId));
        offer.update(
                request.label().trim(), trimToNull(request.code()),
                request.startsAt(), request.endsAt(), request.active());
        product.touchEdited(Instant.now());
        productRepository.flush();
        return OfferResponse.from(offer, Instant.now());
    }

    @Override
    @Transactional
    public void removeOffer(String sellerPublicId, String productPublicId, String offerPublicId) {
        ProductEntity product = requireEditable(sellerPublicId, productPublicId);
        ProductOfferEntity offer = product.getOffers().stream()
                .filter(candidate -> candidate.getPublicId().equals(offerPublicId))
                .findFirst()
                .orElseThrow(() -> new ProductNotFoundException(offerPublicId));
        product.removeOffer(offer);
        product.touchEdited(Instant.now());
        productRepository.flush();
    }

    /* ---- internals ---- */

    /**
     * Section 9's per-image score.
     *
     * <p>Deliberately simple and honest about what it can see: resolution and aspect ratio are
     * measurable from the header, and "good lighting" or "background clean" are not — claiming them
     * would be a number the seller cannot act on and cannot trust. Null for a video, which has no
     * comparable notion.
     */
    private static Integer scoreImage(MediaRequest request) {
        if (request.mediaType() != MediaType.IMAGE
                || request.widthPx() == null || request.heightPx() == null) {
            return null;
        }
        int width = request.widthPx();
        int height = request.heightPx();
        int score = 0;

        // Resolution: a listing image is zoomed on a product page, so short edge is what matters.
        int shortEdge = Math.min(width, height);
        if (shortEdge >= 1600) {
            score += 50;
        } else if (shortEdge >= 1000) {
            score += 40;
        } else if (shortEdge >= 600) {
            score += 25;
        } else {
            score += 10;
        }

        // Section 8 recommends square: a grid of mixed ratios letterboxes every card in it.
        double ratio = (double) Math.max(width, height) / Math.min(width, height);
        if (ratio <= 1.05) {
            score += 30;
        } else if (ratio <= 1.35) {
            score += 20;
        } else {
            score += 5;
        }

        // Alt text is part of whether the image does its job, not a separate checkbox.
        score += trimToNull(request.altText()) != null ? 20 : 0;
        return Math.min(score, 100);
    }

    private List<MediaResponse> sortedMedia(ProductEntity product) {
        return sortedEntities(product).stream().map(MediaResponse::from).toList();
    }

    private List<ProductMediaEntity> sortedEntities(ProductEntity product) {
        List<ProductMediaEntity> assets = new ArrayList<>(product.getMedia());
        assets.sort(Comparator.comparingInt(ProductMediaEntity::getDisplayOrder));
        return assets;
    }

    private static ProductMediaEntity findMedia(ProductEntity product, String mediaPublicId) {
        return product.getMedia().stream()
                .filter(asset -> asset.getPublicId().equals(mediaPublicId))
                .findFirst()
                .orElseThrow(() -> new ProductNotFoundException(mediaPublicId));
    }

    /** A listing under review or on sale must not have its gallery changed underneath it. */
    private ProductEntity requireEditable(String sellerPublicId, String productPublicId) {
        ProductEntity product = requireOwned(sellerPublicId, productPublicId);
        if (!product.isEditableBySeller()) {
            throw new InvalidProductStateException(product.getStatus(), "edit the media of");
        }
        return product;
    }

    /**
     * Not found rather than forbidden when another seller asks.
     *
     * <p>The same choice the product service makes: telling a seller that a listing exists but is
     * not theirs confirms a competitor's product id for them.
     */
    private ProductEntity requireOwned(String sellerPublicId, String productPublicId) {
        ProductEntity product = productRepository.findByPublicId(productPublicId)
                .orElseThrow(() -> new ProductNotFoundException(productPublicId));
        if (!product.isOwnedBy(sellerPublicId)) {
            log.warn("PRODUCT_MEDIA_ACCESS_DENIED publicId={} requestedBy={}", productPublicId, sellerPublicId);
            throw new ProductNotFoundException(productPublicId);
        }
        return product;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
