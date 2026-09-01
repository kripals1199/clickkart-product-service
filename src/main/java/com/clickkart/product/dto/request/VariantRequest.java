// src/main/java/com/clickkart/product/dto/request/VariantRequest.java
package com.clickkart.product.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Map;

/**
 * One purchasable variant.
 *
 * <p>Money is {@link BigDecimal}, never {@code double}, all the way from the wire: a JSON number
 * bound to a double has already lost precision before any validation runs, so switching to
 * BigDecimal at the entity alone would be too late.
 *
 * <p>{@code @Digits} bounds it to the column's {@code numeric(12,2)}. Without it an oversized
 * amount reaches the database and fails as a 500 rather than a field error naming the price.
 *
 * <p>The relationship between MRP and selling price is checked in the service layer, not here -
 * cross-field rules are not something a per-field annotation can express, and splitting the two
 * halves of "is this priced sensibly" across layers would be worse than putting both in one place.
 */
public record VariantRequest(
        /*
         * Uppercase alphanumerics, hyphens and underscores. Restrictive because this is printed on a
         * label, scanned, and re-typed by a warehouse operator - anything ambiguous in that loop
         * costs a mis-picked order.
         */
        @NotBlank(message = "must not be blank")
                @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]{2,63}$", message = "must be 3-64 letters, digits, hyphens or underscores")
                String sku,

        @NotBlank(message = "must not be blank") @Size(max = 150, message = "must be at most 150 characters")
                String variantName,

        @NotNull(message = "must be specified")
                @DecimalMin(value = "0.01", message = "must be greater than zero")
                @DecimalMax(value = "9999999999.99", message = "is too large")
                @Digits(integer = 10, fraction = 2, message = "must have at most 2 decimal places")
                BigDecimal mrp,

        @NotNull(message = "must be specified")
                @DecimalMin(value = "0.01", message = "must be greater than zero")
                @DecimalMax(value = "9999999999.99", message = "is too large")
                @Digits(integer = 10, fraction = 2, message = "must have at most 2 decimal places")
                BigDecimal sellingPrice,

        /** Free-form options, e.g. {@code {colour: Blue, size: M}}. Meaningful keys differ per category. */
        @Size(max = 20, message = "a variant may have at most 20 attributes") Map<String, String> attributes,

        /**
         * Section 11. What this SKU cost the seller. Optional, and never returned to a customer.
         *
         * <p>Not validated against the selling price: selling below cost is a real decision - a
         * loss leader, or clearing old stock - and refusing it would block a legitimate listing.
         */
        @DecimalMin(value = "0.00", message = "cannot be negative")
        @DecimalMax(value = "9999999999.99", message = "is too large")
        @Digits(integer = 10, fraction = 2, message = "must have at most 2 decimal places")
        BigDecimal costPrice) {}
