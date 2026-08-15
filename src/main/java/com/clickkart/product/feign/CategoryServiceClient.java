// src/main/java/com/clickkart/product/feign/CategoryServiceClient.java
package com.clickkart.product.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Category Service's internal API - the surface built for exactly this question.
 *
 * <p>Deliberately not the public catalog API, which cannot see an inactive category. A listing
 * attached to a section an operator has temporarily hidden is still a valid listing; going through
 * the public API would make this service lose sight of it the moment the section was hidden.
 */
@FeignClient(name = CategoryServiceClient.SERVICE_NAME, fallbackFactory = CategoryServiceClientFallbackFactory.class)
public interface CategoryServiceClient {

    String SERVICE_NAME = "clickkart-category-service";
    String VALIDATE_PATH = "/internal/v1/categories/{publicId}/validate";
    String CORRELATION_ID_HEADER = "X-Correlation-Id";
    String API_KEY_HEADER = "X-Internal-Api-Key";

    @GetMapping(VALIDATE_PATH)
    CategoryValidationApiResponse validate(
            @PathVariable("publicId") String publicId,
            @RequestHeader(CORRELATION_ID_HEADER) String correlationId,
            @RequestHeader(API_KEY_HEADER) String apiKey);
}
