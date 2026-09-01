// src/main/java/com/clickkart/product/dto/request/OfferRequest.java
package com.clickkart.product.dto.request;

import com.clickkart.product.enums.OfferType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Section 13. A promotion this listing advertises.
 *
 * <p>What is recorded is the badge and its window, never the offer's budget or redemption rules -
 * those belong to whatever service issues the coupon, and a second copy here would disagree with it
 * the first time a budget ran out.
 */
public record OfferRequest(
        @NotNull(message = "must be specified")
        OfferType offerType,

        @NotBlank(message = "must not be blank")
        @Size(max = 160, message = "must be at most 160 characters")
        String label,

        @Size(max = 40, message = "must be at most 40 characters")
        String code,

        Instant startsAt,

        /** Null means the offer does not expire, which is what drives whether a countdown shows. */
        Instant endsAt,

        boolean active) {
}
