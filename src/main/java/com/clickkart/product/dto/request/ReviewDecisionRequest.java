// src/main/java/com/clickkart/product/dto/request/ReviewDecisionRequest.java
package com.clickkart.product.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * An operator's moderation decision.
 *
 * <p>{@code reason} is required when rejecting - enforced in the service layer, since the
 * requirement is conditional on {@code approved}. A rejection with no reason leaves the seller
 * guessing at what to change and support unable to explain it, which turns one rejection into a
 * support ticket and a resubmission of the same listing.
 */
public record ReviewDecisionRequest(
        @NotNull(message = "must be specified") Boolean approved,
        @Size(max = 500, message = "must be at most 500 characters") String reason) {}
