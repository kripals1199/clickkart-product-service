// src/main/java/com/clickkart/product/dto/request/ProductRequest.java
package com.clickkart.product.dto.request;

import com.clickkart.product.enums.ProductType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;
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
		Map<String, List<String>> properties,

		/** Section 7. Written for a listing card, not truncated from {@link #description()}. */
		@Size(max = 300, message = "must be at most 300 characters")
		String shortDescription,

		/** Section 6. Null is taken as PHYSICAL, which is what the overwhelming majority are. */
		ProductType productType,

		/** Section 11. A percentage, so 18 means 18% - not 0.18. */
		@DecimalMin(value = "0.00", message = "cannot be negative")
		@DecimalMax(value = "100.00", message = "cannot exceed 100%")
		@Digits(integer = 3, fraction = 2, message = "must have at most 2 decimal places")
		BigDecimal taxRatePercent,

		Boolean priceIncludesTax,

		/**
		 * Sections 18, 20 and 21.
		 *
		 * <p>Each is nullable and each is applied wholesale, exactly like {@link #properties()}: a
		 * save carries the complete state of the form, so a section the seller cleared arrives as
		 * null and is cleared rather than silently kept at its old value. Autosave sends the whole
		 * draft for the same reason - a partial patch cannot express "this field is now empty".
		 */
		@Valid ShippingRequest shipping,

		@Valid AftersalesRequest aftersales,

		@Valid SeoRequest seo
		) {
}
