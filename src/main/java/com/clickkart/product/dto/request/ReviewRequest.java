// src/main/java/com/clickkart/product/dto/request/ReviewRequest.java
package com.clickkart.product.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * What a customer submits.
 *
 * <p>The rating is required and the words are not. Someone who wants to say "four stars" and move
 * on is giving the most useful signal a rating carries, and demanding a paragraph for it would
 * mostly produce paragraphs nobody means.
 */
public record ReviewRequest(
        @NotNull(message = "A rating is required") @Min(1) @Max(5) Short rating,
        @Size(max = 120, message = "Keep the headline under 120 characters") String title,
        @Size(max = 4000, message = "Keep the review under 4000 characters") String body) {}
