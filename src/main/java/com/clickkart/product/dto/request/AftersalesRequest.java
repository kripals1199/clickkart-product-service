// src/main/java/com/clickkart/product/dto/request/AftersalesRequest.java
package com.clickkart.product.dto.request;

import com.clickkart.product.enums.WarrantyType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Section 20. What happens after the sale.
 *
 * <p>{@code returnWindowDays} of zero is a real answer meaning no returns, and is deliberately not
 * the same as null, which means the seller has not reached this section. The publish checklist has
 * to tell those apart - one is a decision, the other is unfinished work.
 */
public record AftersalesRequest(
        @Min(value = 0, message = "cannot be negative")
        @Max(value = 90, message = "must be at most 90 days")
        Integer returnWindowDays,

        WarrantyType warrantyType,

        @Min(value = 0, message = "cannot be negative")
        @Max(value = 240, message = "must be at most 20 years")
        Integer warrantyMonths) {
}
