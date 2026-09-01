// src/main/java/com/clickkart/product/dto/request/ShippingRequest.java
package com.clickkart.product.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Section 18. What the package is, so a carrier can be priced and a delivery date estimated.
 *
 * <p>Grams and millimetres as integers rather than kilograms and metres as decimals: carrier rate
 * cards band on whole units, and a float rounds differently in two services — the price quoted at
 * checkout would then not match the one the seller was shown here.
 *
 * <p>Every field is optional. A seller saving a draft after the Basic Information section has not
 * reached this one yet, and refusing the save would lose the work they had done.
 */
public record ShippingRequest(
        @Positive(message = "must be greater than zero")
        @Max(value = 500_000, message = "must be at most 500kg")
        Integer weightGrams,

        @Positive(message = "must be greater than zero")
        @Max(value = 10_000, message = "must be at most 10m")
        Integer lengthMm,

        @Positive(message = "must be greater than zero")
        @Max(value = 10_000, message = "must be at most 10m")
        Integer widthMm,

        @Positive(message = "must be greater than zero")
        @Max(value = 10_000, message = "must be at most 10m")
        Integer heightMm,

        @Size(max = 40, message = "must be at most 40 characters")
        String packageType,

        @Size(max = 40, message = "must be at most 40 characters")
        String shippingClass,

        boolean freeShipping,

        /**
         * Section 18. Empty is taken as STANDARD rather than "no delivery at all", which is not
         * a state a physical product can be in.
         */
        java.util.List<com.clickkart.product.enums.DeliveryOption> deliveryOptions) {
}
