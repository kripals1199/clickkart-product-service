// src/main/java/com/clickkart/product/enums/ProductType.java
package com.clickkart.product.enums;

/**
 * Whether the thing being sold is shipped or delivered over the wire.
 *
 * <p>Section 6 of the Add Product brief. This is not cosmetic: it decides whether the form asks for
 * weight, dimensions and a delivery estimate at all. Collecting those for a downloadable licence
 * produces columns full of nulls and a shipping section the seller has to work out is irrelevant.
 */
public enum ProductType {
    /** Ships to the customer. Weight, dimensions and a return window all apply. */
    PHYSICAL,
    /** Delivered digitally. No package, no carrier, and returns work differently. */
    DIGITAL
}
