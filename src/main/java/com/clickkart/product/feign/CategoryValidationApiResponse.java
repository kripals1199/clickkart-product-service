// src/main/java/com/clickkart/product/feign/CategoryValidationApiResponse.java
package com.clickkart.product.feign;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Category Service's reply, unwrapped from the platform's standard envelope.
 *
 * <p>{@code ignoreUnknown} on both levels is load-bearing rather than decorative: the envelope
 * carries timestamp/status/path/correlationId this caller has no use for, and the inner payload may
 * gain fields. Without it, Category Service adding one field would break every deserialization
 * here - precisely the shared-schema coupling the no-shared-library rule exists to avoid.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CategoryValidationApiResponse(boolean success, Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(
            String publicId, boolean exists, boolean active, boolean leaf, boolean assignable, String reason) {}

    public boolean isAssignable() {
        return data != null && data.assignable();
    }

    /** Falls back to a generic message only when the sender omitted one, which it does on success. */
    public String reasonOrDefault() {
        if (data == null) {
            return "Category Service returned no verdict";
        }
        return data.reason() == null ? "Category cannot be used for a listing" : data.reason();
    }
}
