// src/main/java/com/clickkart/product/service/MediaStorage.java
package com.clickkart.product.service;

/**
 * Where an uploaded product image or video is put.
 *
 * <p>Section 8 of the Add Product brief needs a seller to drag a file in and get a URL back. This
 * platform has no object store, so the shipped implementation writes to a local directory — but the
 * seam is here so that swapping to S3, MinIO or a CDN is one class, and nothing that reads a
 * listing changes. Everything downstream only ever sees the URL this returns.
 */
public interface MediaStorage {

    /**
     * Stores one file and returns the URL it can be fetched from.
     *
     * <p>Implementations must not trust {@code originalFilename} for anything but a hint. It is
     * attacker-controlled: it can contain path separators, {@code ..}, a null byte, or an extension
     * that disagrees with the actual bytes. The stored name is generated, never taken from it.
     *
     * @param content         the raw bytes, already size-checked by the caller
     * @param originalFilename what the browser called it; used only to log and to pick a suffix
     * @param declaredContentType the browser's claim, which is verified against the bytes
     * @return the stored asset, with the URL to record against the product
     * @throws MediaRejectedException if the bytes are not a media type this platform accepts
     */
    StoredMedia store(byte[] content, String originalFilename, String declaredContentType);

    /** Removes a previously stored asset. Missing is not an error — deletion is idempotent. */
    void delete(String url);

    /**
     * A stored asset.
     *
     * @param url        where it can now be fetched
     * @param contentType the type determined from the bytes, not the one the browser claimed
     * @param widthPx    natural width, or null when it could not be read
     * @param heightPx   natural height, or null when it could not be read
     * @param sizeBytes  what was written
     */
    record StoredMedia(String url, String contentType, Integer widthPx, Integer heightPx, long sizeBytes) {}

    /** Thrown when the bytes are not something this platform will serve back to a customer. */
    class MediaRejectedException extends RuntimeException {
        public MediaRejectedException(String message) {
            super(message);
        }
    }
}
