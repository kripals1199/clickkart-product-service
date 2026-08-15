// src/main/java/com/clickkart/product/feign/CategoryServiceClientFallbackFactory.java
package com.clickkart.product.feign;

import com.clickkart.product.exception.DownstreamServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Category Service is a required dependency for publishing, not a best-effort one.
 *
 * <p>Degrading to "assume it is fine" would let a listing go on sale against a category nobody
 * confirmed exists, and the failure would stay invisible until a customer hit a broken catalog
 * page. A 503 the seller can retry is the better outcome.
 *
 * <p>Note the blast radius is bounded: only submission needs this. A seller can still create and
 * edit drafts while Category Service is down.
 */
@Slf4j
@Component
public class CategoryServiceClientFallbackFactory implements FallbackFactory<CategoryServiceClient> {

    private static final String SERVICE_NAME = "Category Service";

    @Override
    public CategoryServiceClient create(Throwable cause) {
        return (publicId, correlationId, apiKey) -> {
            log.warn("CATEGORY_VALIDATION_UNAVAILABLE categoryPublicId={} correlationId={} cause={}",
                    publicId, correlationId, cause.toString());
            throw new DownstreamServiceUnavailableException(SERVICE_NAME, cause);
        };
    }
}
