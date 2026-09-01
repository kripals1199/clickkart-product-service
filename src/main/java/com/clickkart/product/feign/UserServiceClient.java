// src/main/java/com/clickkart/product/feign/UserServiceClient.java
package com.clickkart.product.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * User Service's internal API, for the seller's business profile.
 *
 * <p>The {@code ROLE_SELLER} claim in the token is not sufficient on its own: it says the platform
 * granted someone the seller role, not that an operator has verified their GSTIN. Only User Service
 * knows the verification status, and only a VERIFIED seller may put goods on sale.
 *
 * <p>Uses its own API key, distinct from Category Service's - this service holds both, which is
 * exactly why they are keyed separately.
 */
@FeignClient(name = UserServiceClient.SERVICE_NAME, fallbackFactory = UserServiceClientFallbackFactory.class)
public interface UserServiceClient {

    String SERVICE_NAME = "clickkart-user-service";
    String SELLER_PATH = "/internal/v1/users/{userPublicId}/seller";
    String PROFILE_PATH = "/internal/v1/users/{userPublicId}";
    String CORRELATION_ID_HEADER = "X-Correlation-Id";
    String API_KEY_HEADER = "X-Internal-Api-Key";

    @GetMapping(SELLER_PATH)
    SellerProfileApiResponse getSellerProfile(
            @PathVariable("userPublicId") String userPublicId,
            @RequestHeader(CORRELATION_ID_HEADER) String correlationId,
            @RequestHeader(API_KEY_HEADER) String apiKey);

    /**
     * The reviewer's display name, so a review carries a byline.
     *
     * <p>Read at write time and copied onto the review rather than resolved per read: a product
     * page shows many reviews, and one call per row would make the page cost a fan-out. The
     * consequence is that renaming yourself does not rename old reviews, which is the right
     * behaviour anyway - the name is part of what was said, at the time it was said.
     */
    @GetMapping(PROFILE_PATH)
    UserProfileApiResponse getProfile(
            @PathVariable("userPublicId") String userPublicId,
            @RequestHeader(CORRELATION_ID_HEADER) String correlationId,
            @RequestHeader(API_KEY_HEADER) String apiKey);
}
