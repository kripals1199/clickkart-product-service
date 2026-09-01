// src/main/java/com/clickkart/product/dto/response/RatingSummaryResponse.java
package com.clickkart.product.dto.response;

import java.math.BigDecimal;
import java.util.Map;

/**
 * The rating block above a product's reviews.
 *
 * <p>{@code average} is null when nothing has been reviewed, rather than zero - "not rated yet" and
 * "rated zero" are different claims about a seller's product, and only one of them is ever true.
 *
 * @param breakdown how many reviews gave each star, 1 to 5, with absent ratings present as 0 so a
 *     client can render five bars without filling gaps itself
 */
public record RatingSummaryResponse(BigDecimal average, int total, Map<Short, Long> breakdown) {}
