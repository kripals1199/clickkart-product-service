// src/main/java/com/clickkart/product/enums/ReviewStatus.java
package com.clickkart.product.enums;

/**
 * Whether a review is visible, and nothing more.
 *
 * <p>There is deliberately no PENDING state. Holding every review for approval means either a queue
 * nobody drains - so honest reviews never appear - or rubber-stamping, which is approval in name
 * only. Reviews publish immediately and an operator can hide one that breaks the rules, which is
 * the trade every marketplace at this size actually makes.
 */
public enum ReviewStatus {

    /** Visible to everyone, and counted in the product's rating. */
    PUBLISHED,

    /**
     * Hidden by an operator. Still visible to its author, who would otherwise write it again not
     * knowing it had been removed, and still stored, because a deleted row cannot be appealed.
     */
    HIDDEN
}
