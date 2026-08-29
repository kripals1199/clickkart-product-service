// src/main/java/com/clickkart/product/dto/request/ProductRequest.java
package com.clickkart.product.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 * What a seller supplies when creating or updating a listing.
 *
 * <p>
 * Deliberately absent: {@code sellerPublicId} and {@code status}. The seller is
 * taken from the verified token - accepting it here would let one seller create
 * listings under another's name - and status is owned by the moderation
 * workflow, so a seller who could set it would put goods on sale without
 * review, which is the one thing review exists to prevent.
 *
 * <p>
 * At least one variant is required. A product with none is not purchasable, and
 * letting one exist would mean every downstream service handling an
 * empty-variant case that has no meaning.
 */
public record ProductRequest(
		@NotBlank(message = "must not be blank") 
		@Size(max = 200, message = "must be at most 200 characters") 
		String name,

		@Pattern(regexp = "^$|^[a-z0-9]+(-[a-z0-9]+)*$", message = "must be lowercase letters, digits and single hyphens") 
		@Size(max = 220, message = "must be at most 220 characters") 
		String slug,

		@Size(max = 4000, message = "must be at most 4000 characters") 
		String description,

		@Size(max = 120, message = "must be at most 120 characters") 
		String brand,

		@NotBlank(message = "must not be blank") 
		@Size(max = 40, message = "must be at most 40 characters") 
		String categoryPublicId,

		@NotEmpty(message = "at least one variant is required") 
		@Size(max = 50, message = "a listing may have at most 50 variants")
		@Valid List<VariantRequest> variants,

		/**
		 * The seller's answers to the master-data properties that apply to this category, keyed by
		 * the property's stable machine name. A list per key, because a multi-select property is
		 * several answers to one question.
		 *
		 * <p>Optional and unvalidated here on purpose. Which properties apply, and what each accepts,
		 * is Category Service's to decide - duplicating those rules in this service would create two
		 * answers to the same question and guarantee they drift. A property that no longer applies is
		 * simply not sent by the form.
		 */
		Map<String, List<String>> properties
		) {
}
