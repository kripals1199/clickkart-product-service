// src/main/java/com/clickkart/product/feign/UserServiceClientFallbackFactory.java
package com.clickkart.product.feign;

import com.clickkart.product.exception.DownstreamServiceUnavailableException;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Distinguishes "no seller profile" from "User Service is down", because they need opposite
 * responses.
 *
 * <p>A 404 is a definitive answer - this seller never submitted business details, so they are not
 * eligible and never will be until they do. Translating it into a 503 would tell them to retry
 * something that cannot start working, so it is passed through as an empty profile and the caller
 * reports the real reason. Anything else is a genuine outage and fails the request.
 */
@Slf4j
@Component
public class UserServiceClientFallbackFactory implements FallbackFactory<UserServiceClient> {

    private static final String SERVICE_NAME = "User Service";

    @Override
    public UserServiceClient create(Throwable cause) {
        return (userPublicId, correlationId, apiKey) -> {
            if (cause instanceof FeignException.NotFound) {
                log.info("SELLER_PROFILE_ABSENT sellerPublicId={} correlationId={}", userPublicId, correlationId);
                return new SellerProfileApiResponse(false, null);
            }
            log.warn("SELLER_VALIDATION_UNAVAILABLE sellerPublicId={} correlationId={} cause={}",
                    userPublicId, correlationId, cause.toString());
            throw new DownstreamServiceUnavailableException(SERVICE_NAME, cause);
        };
    }
}
