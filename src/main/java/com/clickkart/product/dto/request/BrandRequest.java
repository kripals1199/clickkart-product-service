// src/main/java/com/clickkart/product/dto/request/BrandRequest.java
package com.clickkart.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A brand a seller is adding to the shared list.
 *
 * <p>The pattern is permissive about punctuation on purpose - real brands contain ampersands,
 * apostrophes, dots and digits - but refuses a name with no letter or digit in it at all, which is
 * the only way to end up with a row whose normalised key is empty and which therefore collides with
 * every other such row.
 */
public record BrandRequest(
        @NotBlank(message = "must not be blank")
        @Size(min = 2, max = 120, message = "must be between 2 and 120 characters")
        @Pattern(regexp = ".*[\\p{L}\\p{N}].*", message = "must contain at least one letter or digit")
        String name) {
}
