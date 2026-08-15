// src/main/java/com/clickkart/product/feign/SellerProfileApiResponse.java
package com.clickkart.product.feign;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * User Service's seller profile, unwrapped from the standard envelope and reduced to the fields
 * this service actually needs.
 *
 * <p>GSTIN, support contacts and pickup address are deliberately not bound. This service has no use
 * for them, and pulling another service's regulated data into a second database widens the blast
 * radius of a compromise for no benefit - the answer needed here is one boolean.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SellerProfileApiResponse(boolean success, Data data) {

    private static final String VERIFIED = "VERIFIED";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(String userPublicId, String businessName, String verificationStatus) {}

    public boolean isVerified() {
        return data != null && VERIFIED.equals(data.verificationStatus());
    }

    public String businessNameOrUnknown() {
        return data == null || data.businessName() == null ? "unknown" : data.businessName();
    }

    public String verificationStatusOrUnknown() {
        return data == null || data.verificationStatus() == null ? "NONE" : data.verificationStatus();
    }
}
