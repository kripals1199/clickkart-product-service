// src/main/java/com/clickkart/product/feign/UserProfileApiResponse.java
package com.clickkart.product.feign;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A reviewer's public identity, reduced to the one field this service shows.
 *
 * <p>Name, date of birth, contact details and preferences are deliberately not bound. A review needs
 * a byline; pulling a customer's profile into a second database would put personal data outside
 * User Service's erasure reach for no benefit.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserProfileApiResponse(boolean success, Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(String userPublicId, String displayName) {}

    /** Null rather than a placeholder: the response DTO decides what an absent name reads as. */
    public String displayNameOrNull() {
        return data == null || data.displayName() == null || data.displayName().isBlank()
                ? null
                : data.displayName();
    }
}
