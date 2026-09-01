// src/main/java/com/clickkart/product/dto/request/MediaRequest.java
package com.clickkart.product.dto.request;

import com.clickkart.product.enums.MediaType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Sections 8 to 10. One asset attached to a listing.
 *
 * <p>The URL is supplied rather than the bytes. Binary arrives separately, at the upload endpoint,
 * which stores it and hands back the URL that is then sent here - so a seller pasting a link to an
 * image they already host and a seller dragging a file in reach the same code path.
 */
public record MediaRequest(
        @NotNull(message = "must be specified")
        MediaType mediaType,

        @NotBlank(message = "must not be blank")
        @Size(max = 1000, message = "must be at most 1000 characters")
        String url,

        /**
         * Section 37. Optional here because a draft is allowed to be unfinished, but the publish
         * checklist asks for it: an image with no alt text is invisible to anyone using a screen
         * reader, and that is most of what a listing communicates.
         */
        @Size(max = 300, message = "must be at most 300 characters")
        String altText,

        @Positive(message = "must be greater than zero")
        Integer widthPx,

        @Positive(message = "must be greater than zero")
        Integer heightPx,

        @Positive(message = "must be greater than zero")
        Integer durationSeconds) {
}
