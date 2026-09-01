// src/main/java/com/clickkart/product/feign/OrderServiceClient.java
package com.clickkart.product.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Order Service's internal API, asked one question: has this customer taken delivery of this
 * product?
 *
 * <p>The answer decides only whether a review carries the verified-purchase badge, never whether it
 * may be written. Gating reviews on purchase entirely sounds stricter and is worse: it silences
 * everyone who bought the same thing elsewhere, and it makes the review section a function of this
 * platform's own order history rather than of the product.
 */
@FeignClient(name = OrderServiceClient.SERVICE_NAME, fallbackFactory = OrderServiceClientFallbackFactory.class)
public interface OrderServiceClient {

    String SERVICE_NAME = "clickkart-order-service";
    String PURCHASE_PATH = "/internal/v1/orders/purchases/{productPublicId}";
    String CORRELATION_ID_HEADER = "X-Correlation-Id";
    String API_KEY_HEADER = "X-Internal-Api-Key";

    @GetMapping(PURCHASE_PATH)
    PurchaseCheckApiResponse hasPurchased(
            @PathVariable("productPublicId") String productPublicId,
            @RequestParam("userPublicId") String userPublicId,
            @RequestHeader(CORRELATION_ID_HEADER) String correlationId,
            @RequestHeader(API_KEY_HEADER) String apiKey);
}
