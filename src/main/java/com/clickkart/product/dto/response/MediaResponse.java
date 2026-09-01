// src/main/java/com/clickkart/product/dto/response/MediaResponse.java
package com.clickkart.product.dto.response;

import com.clickkart.product.entity.ProductMediaEntity;
import com.clickkart.product.enums.MediaType;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Sections 8 to 10. One asset, in the order the seller arranged it. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MediaResponse(
        String publicId,
        MediaType mediaType,
        String url,
        String altText,
        boolean primary,
        int displayOrder,
        Integer qualityScore,
        Integer widthPx,
        Integer heightPx,
        Integer durationSeconds) {

    public static MediaResponse from(ProductMediaEntity entity) {
        return new MediaResponse(
                entity.getPublicId(),
                entity.getMediaType(),
                entity.getUrl(),
                entity.getAltText(),
                entity.isPrimary(),
                entity.getDisplayOrder(),
                entity.getQualityScore(),
                entity.getWidthPx(),
                entity.getHeightPx(),
                entity.getDurationSeconds());
    }
}
