// src/main/java/com/clickkart/product/dto/response/BrandResponse.java
package com.clickkart.product.dto.response;

import com.clickkart.product.entity.BrandEntity;
import com.fasterxml.jackson.annotation.JsonInclude;

/** One brand as the Add Product form sees it. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BrandResponse(String publicId, String name, boolean sellerCreated) {

    public static BrandResponse from(BrandEntity entity) {
        return new BrandResponse(entity.getPublicId(), entity.getName(), entity.isSellerCreated());
    }
}
