// src/main/java/com/clickkart/product/dto/response/OfferResponse.java
package com.clickkart.product.dto.response;

import com.clickkart.product.entity.ProductOfferEntity;
import com.clickkart.product.enums.OfferType;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * Section 13.
 *
 * <p>{@code live} is answered here rather than left to each caller: whether an offer should be
 * shown depends on the clock as well as the flag, and a storefront that only checked {@code active}
 * would keep advertising a deal that ended an hour ago.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OfferResponse(
        String publicId,
        OfferType offerType,
        String label,
        String code,
        Instant startsAt,
        Instant endsAt,
        boolean active,
        boolean live,
        int displayOrder) {

    public static OfferResponse from(ProductOfferEntity entity, Instant now) {
        return new OfferResponse(
                entity.getPublicId(),
                entity.getOfferType(),
                entity.getLabel(),
                entity.getCode(),
                entity.getStartsAt(),
                entity.getEndsAt(),
                entity.isActive(),
                entity.isLiveAt(now),
                entity.getDisplayOrder());
    }
}
