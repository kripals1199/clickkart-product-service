// src/main/java/com/clickkart/product/service/BrandService.java
package com.clickkart.product.service;

import com.clickkart.product.dto.request.BrandRequest;
import com.clickkart.product.dto.response.BrandResponse;
import java.util.List;

/** Section 6. The shared brand vocabulary behind the Add Product selector. */
public interface BrandService {

    /** Active brands matching the term, or all of them when it is blank. */
    List<BrandResponse> search(String term);

    /**
     * Adds a brand, or returns the existing one it collides with.
     *
     * <p>Idempotent rather than an error, and that is the whole design. A seller typing "SAMSUNG"
     * when "Samsung" exists has not made a mistake worth a rejection - they have named a brand that
     * is already there. Refusing would teach them to type a variant that gets through, which is the
     * outcome this table exists to prevent.
     */
    BrandResponse addOrGet(String sellerPublicId, BrandRequest request);
}
