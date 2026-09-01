// src/main/java/com/clickkart/product/enums/DeliveryOption.java
package com.clickkart.product.enums;

/**
 * How fast a listing can be delivered.
 *
 * <p>Section 18. A set rather than a choice - a product can offer both, and something bulky may
 * offer only standard. Free shipping is deliberately not one of these: it answers what delivery
 * costs, not how fast it is, and folding the two together would make "free" and "express" mutually
 * exclusive when they are not.
 */
public enum DeliveryOption {
    /** Three to five days. The default, and what every listing offered before this existed. */
    STANDARD,
    EXPRESS
}
