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
     * <p>Separate secrets rather than one platform-wide key, so that compromising any single
     * service yields only what that service was entitled to reach. This is the service that makes
     * the separation worth having: it is the first to hold several of someone else's.
     */
    private String categoryServiceApiKey;

    private String userServiceApiKey;

    /**
     * Order Service's key, asked one question: has this customer received this product? Its own
     * secret rather than reusing another, for the reason given above - three became four the
     * moment reviews needed a verified-purchase badge.
     */
    private String orderServiceApiKey;

    /**
     * How far back a price drop is measured. Long enough that a genuine reduction is still visible
     * a fortnight later, short enough that last quarter's price cannot be dressed up as today's
     * saving.
     */
    private int priceDropWindowDays = 30;

    /** Below this, a drop is noise on a listing tile rather than a reason to look. */
    private int minPriceDropPercent = 5;

    /** CORS allow-list. Defence in depth - this service is independently reachable. */
    private String allowedOrigins = "http://localhost:4200";

    /** CIDRs whose {@code X-Forwarded-For} is believed. Empty means trust nothing. */
    private List<String> trustedProxyCidrs = new ArrayList<>();

    /**
     * Where uploaded product media is written.
     *
     * <p>A local directory until the platform has an object store. Deliberately outside any
     * served webroot: files here are handed back through a controller that sets the content type
     * we determined, never by a static handler that would trust the extension.
     */
    private String mediaDirectory = "./data/product-media";

    /** Prefix the stored filename is appended to, to form the URL recorded against a product. */
    private String mediaBaseUrl = "/api/v1/products/media";

    /**
     * Largest upload accepted, in bytes.
     *
     * <p>8MB. Large enough for a high-resolution product photograph, small enough that a seller
     * cannot fill the disk by looping an upload - the limit exists for that, not for the honest
     * case.
     */
    private long maxMediaBytes = 8L * 1024 * 1024;
}
