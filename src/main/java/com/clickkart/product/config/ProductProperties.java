// src/main/java/com/clickkart/product/config/ProductProperties.java
package com.clickkart.product.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized settings, bound from {@code clickkart-product-service.properties} in whichever
 * config-repository branch matches the active profile.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "product")
public class ProductProperties {

    /** Shared HMAC secret. Must match Auth Service's signing key and the Gateway's. */
    private String jwtSecret;

    /** Must match Auth Service's {@code auth.revocation-key-prefix}. */
    private String revocationKeyPrefix = "revoked:jti:";

    /** Guards this service's own {@code /internal/**}. Blank refuses every internal caller. */
    private String internalApiKey;

    /**
     * Keys this service presents when CALLING the other two internal APIs - distinct from
     * {@link #internalApiKey}, which guards its own.
     *
     * <p>Three separate secrets rather than one platform-wide key, so that compromising any single
     * service yields only what that service was entitled to reach. This is the service that makes
     * the separation worth having: it is the first to hold two of someone else's.
     */
    private String categoryServiceApiKey;

    private String userServiceApiKey;

    /** CORS allow-list. Defence in depth - this service is independently reachable. */
    private String allowedOrigins = "http://localhost:4200";

    /** CIDRs whose {@code X-Forwarded-For} is believed. Empty means trust nothing. */
    private List<String> trustedProxyCidrs = new ArrayList<>();
}
