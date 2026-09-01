// src/main/java/com/clickkart/product/feign/OrderServiceClientFallbackFactory.java
package com.clickkart.product.feign;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Degrades rather than fails, unlike the seller check.
 *
 * <p>Seller verification decides whether goods may go on sale, so an unreachable User Service must
 * stop the request. This decides whether a badge appears next to a review. Refusing to accept
 * someone's review because Order Service is restarting would lose what they wrote for the sake of
 * an ornament, so an outage yields "not verified" and the review is kept.
 *
 * <p>The cost is a review that should have been badged and is not. The badge is stored at write
 * time, so that one stays wrong until the review is edited - worth saying plainly rather than
 * pretending the degradation is free.
 */
@Slf4j
@Component
public class OrderServiceClientFallbackFactory implements FallbackFactory<OrderServiceClient> {

    @Override
    public OrderServiceClient create(Throwable cause) {
        return (productPublicId, userPublicId, correlationId, apiKey) -> {
            log.warn("PURCHASE_CHECK_UNAVAILABLE productPublicId={} correlationId={} cause={}",
                    productPublicId, correlationId, cause.toString());
            return new PurchaseCheckApiResponse(false, Boolean.FALSE);
        };
    }
}
