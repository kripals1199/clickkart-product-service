// src/main/java/com/clickkart/product/enums/OfferType.java
package com.clickkart.product.enums;

/**
 * The kind of promotion a listing advertises.
 *
 * <p>Section 13. These are merchandising badges, not the coupon engine: what is recorded is that
 * this product advertises an offer and the label customers read, never its budget or redemption
 * rules, which belong to whatever service issues it.
 */
public enum OfferType {
    /** Card or bank instant discount. */
    BANK,
    /** A code the customer enters at checkout. */
    COUPON,
    CASHBACK,
    /** Time-boxed. The only type whose end date drives a visible countdown. */
    DEAL
}
