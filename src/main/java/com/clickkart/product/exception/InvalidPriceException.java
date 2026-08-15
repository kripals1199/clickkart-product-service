// src/main/java/com/clickkart/product/exception/InvalidPriceException.java
package com.clickkart.product.exception;

/**
 * Prices violate an invariant the annotations cannot express on their own.
 *
 * <p>Chiefly: selling price above MRP. That is not a typo to shrug at - MRP is the price a discount
 * is advertised *from*, so selling above it renders a negative discount in the storefront and, in
 * India, misstates a legally meaningful figure.
 */
public class InvalidPriceException extends RuntimeException {

    public InvalidPriceException(String message) {
        super(message);
    }
}
