// src/main/java/com/clickkart/product/exception/CategoryNotAssignableException.java
package com.clickkart.product.exception;

/**
 * Category Service refused the category: missing, inactive, or an interior node.
 *
 * <p>Carries that service's own reason rather than a generic message, because the three cases need
 * different things from the seller - pick a different category, wait, or pick a more specific one.
 */
public class CategoryNotAssignableException extends RuntimeException {

    public CategoryNotAssignableException(String reason) {
        super(reason);
    }
}
