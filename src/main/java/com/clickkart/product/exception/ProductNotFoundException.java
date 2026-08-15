// src/main/java/com/clickkart/product/exception/ProductNotFoundException.java
package com.clickkart.product.exception;

/**
 * No such product, or it is not visible to this caller.
 *
 * <p>Those two collapse into one 404 deliberately. A seller asking for another seller's draft, and
 * a customer asking for a listing still under review, both get the same answer - anything else
 * would confirm that a listing exists and let a competitor enumerate a rival's unpublished catalog.
 */
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(String identifier) {
        super("Product " + identifier + " was not found");
    }
}
