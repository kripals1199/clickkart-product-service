// src/main/java/com/clickkart/product/exception/InvalidProductStateException.java
package com.clickkart.product.exception;

import com.clickkart.product.enums.ProductStatus;

/**
 * The requested transition is not legal from the listing's current state - approving something
 * nobody submitted, or a seller editing a listing frozen under review.
 *
 * <p>Names both states rather than saying "invalid", because the caller's next action depends
 * entirely on which one it is in.
 */
public class InvalidProductStateException extends RuntimeException {

    public InvalidProductStateException(ProductStatus current, String attempted) {
        super("Cannot " + attempted + " a product that is " + current);
    }
}
