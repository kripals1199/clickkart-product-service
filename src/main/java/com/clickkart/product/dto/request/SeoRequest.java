// src/main/java/com/clickkart/product/dto/request/SeoRequest.java
package com.clickkart.product.dto.request;

import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Section 21. How this listing should appear in a search result.
 *
 * <p>Kept apart from name and description rather than derived from them: an SEO title is written
 * for a result page and is routinely not the product's name, so generating one from the other
 * throws away a deliberate choice.
 *
 * <p>The length caps are the ones search engines actually truncate at, which is why the form shows
 * a character count against them rather than an arbitrary database limit.
 */
public record SeoRequest(
        @Size(max = 200, message = "must be at most 200 characters")
        String seoTitle,

        @Size(max = 320, message = "must be at most 320 characters")
        String metaDescription,

        @Size(max = 25, message = "at most 25 keywords")
        List<@Size(max = 60, message = "must be at most 60 characters") String> keywords) {
}
