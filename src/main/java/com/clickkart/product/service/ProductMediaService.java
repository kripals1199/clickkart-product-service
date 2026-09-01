// src/main/java/com/clickkart/product/service/ProductMediaService.java
package com.clickkart.product.service;

import com.clickkart.product.dto.request.MediaRequest;
import com.clickkart.product.dto.request.OfferRequest;
import com.clickkart.product.dto.response.MediaResponse;
import com.clickkart.product.dto.response.OfferResponse;
import com.clickkart.product.web.RequestMetadata;
import java.util.List;

/**
 * Media and offers on a listing the seller owns.
 *
 * <p>Kept apart from {@code ProductService} rather than folded into it because these have a
 * different write shape: the product is saved wholesale on every autosave, while an image is added,
 * reordered or deleted one at a time. Putting them together would mean either sending every image
 * on every keystroke, or a product save that partially merges — and the wholesale rule is the one
 * thing that makes autosave predictable.
 *
 * <p>Every method takes the seller's id and checks ownership. A seller editing another's gallery is
 * the same breach as editing their prices.
 */
public interface ProductMediaService {

    /**
     * Attaches an already-stored asset to a listing.
     *
     * <p>The first image attached becomes primary automatically. A gallery whose lead image is
     * whichever row the database returned first is not a decision anyone made, and the seller who
     * uploads exactly one image should not have to also click "set as primary".
     */
    MediaResponse attach(
            String sellerPublicId, String productPublicId, MediaRequest request,
            String correlationId, RequestMetadata metadata);

    List<MediaResponse> list(String sellerPublicId, String productPublicId);

    /**
     * Section 8. Every image this seller has already uploaded, across all their listings.
     *
     * <p>So a seller reusing one photograph on three listings uploads it once. The same asset
     * URL is attached to each - the row is per product, the bytes are shared.
     */
    List<MediaResponse> library(String sellerPublicId, int limit);

    /** Alt text and dimensions only. The file itself is swapped by {@link #replaceFile}. */
    MediaResponse updateMetadata(
            String sellerPublicId, String productPublicId, String mediaPublicId, MediaRequest request);

    /**
     * Swaps the file behind an asset, keeping its place in the gallery.
     *
     * <p>For crop and rotate. Doing this as detach-then-attach would work, but the asset would
     * lose its position, its primary flag and its public id - so a seller who rotated the lead
     * image would find it demoted and moved to the end of the gallery, which is not what they
     * asked for.
     *
     * <p>The old file is deleted only after the row points at the new one. The other order leaves
     * a listing referencing a file that no longer exists if the write fails.
     */
    MediaResponse replaceFile(
            String sellerPublicId, String productPublicId, String mediaPublicId, MediaRequest request);

    /**
     * Reorders the gallery to exactly the sequence given.
     *
     * <p>Takes the whole ordering rather than a moved pair, for the same reason the property
     * mapping console does: two sellers — or two tabs — reordering at once cannot interleave into a
     * sequence neither of them chose.
     */
    List<MediaResponse> reorder(String sellerPublicId, String productPublicId, List<String> mediaPublicIds);

    /** Promotes one image and demotes the rest, in a single write. */
    List<MediaResponse> makePrimary(String sellerPublicId, String productPublicId, String mediaPublicId);

    /**
     * Detaches an asset and removes the stored file.
     *
     * <p>If the primary was removed, the next image takes its place — a listing with images but no
     * primary renders an empty frame on every card that shows it.
     */
    void detach(
            String sellerPublicId, String productPublicId, String mediaPublicId,
            String correlationId, RequestMetadata metadata);

    /* ---- section 13 ---- */

    OfferResponse addOffer(String sellerPublicId, String productPublicId, OfferRequest request);

    List<OfferResponse> listOffers(String sellerPublicId, String productPublicId);

    OfferResponse updateOffer(
            String sellerPublicId, String productPublicId, String offerPublicId, OfferRequest request);

    void removeOffer(String sellerPublicId, String productPublicId, String offerPublicId);
}
