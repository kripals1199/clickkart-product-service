// src/main/java/com/clickkart/product/feign/UserServiceClientFallbackFactory.java
package com.clickkart.product.feign;

import com.clickkart.product.exception.DownstreamServiceUnavailableException;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Distinguishes "no such record" from "User Service is down", because they need opposite responses.
 *
 * <p>An anonymous class rather than a lambda: the client carries two calls now, and they degrade
 * differently.
 */
@Slf4j
@Component
public class UserServiceClientFallbackFactory implements FallbackFactory<UserServiceClient> {

    private static final String SERVICE_NAME = "User Service";

    @Override
    public UserServiceClient create(Throwable cause) {
        return new UserServiceClient() {

            /**
             * A 404 is a definitive answer - this seller never submitted business details, so they
             * are not eligible and never will be until they do. Translating it into a 503 would
             * tell them to retry something that cannot start working, so it is passed through as
             * an empty profile and the caller reports the real reason. Anything else is a genuine
             * outage and fails the request, because selling rights must not be granted on a guess.
             */
            @Override
            public SellerProfileApiResponse getSellerProfile(
                    String userPublicId, String correlationId, String apiKey) {
                if (cause instanceof FeignException.NotFound) {
                    log.info("SELLER_PROFILE_ABSENT sellerPublicId={} correlationId={}",
                            userPublicId, correlationId);
                    return new SellerProfileApiResponse(false, null);
                }
                log.warn("SELLER_VALIDATION_UNAVAILABLE sellerPublicId={} correlationId={} cause={}",
                        userPublicId, correlationId, cause.toString());
                throw new DownstreamServiceUnavailableException(SERVICE_NAME, cause);
            }

            /**
             * Degrades, unlike the seller check above. This name is a byline on a review; losing
             * what someone wrote because User Service is restarting would trade the thing that
             * matters for the thing that decorates it. The review is kept and reads "A customer".
             */
            @Override
            public UserProfileApiResponse getProfile(
                    String userPublicId, String correlationId, String apiKey) {
                log.warn("PROFILE_LOOKUP_UNAVAILABLE userPublicId={} correlationId={} cause={}",
                        userPublicId, correlationId, cause.toString());
                return new UserProfileApiResponse(false, null);
            }
        };
    }
}
