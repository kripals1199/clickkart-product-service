// src/main/java/com/clickkart/product/enums/ProductAuditAction.java
package com.clickkart.product.enums;

/**
 * This service's own audit vocabulary, reported to the Audit Log Service as a plain string.
 * No shared enum with any other service (Rule 4). Names must stay within the sink's 60-character
 * column.
 */
public enum ProductAuditAction {
    PRODUCT_CREATED,
    PRODUCT_UPDATED,
    PRODUCT_SUBMITTED_FOR_REVIEW,
    PRODUCT_APPROVED,
    PRODUCT_REJECTED,
    PRODUCT_ARCHIVED,
    VARIANT_ADDED,
    VARIANT_UPDATED,
    VARIANT_REMOVED
}
