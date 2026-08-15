// src/main/java/com/clickkart/product/exception/VariantNotFoundException.java
package com.clickkart.product.exception;

/** No such variant on this product, or it belongs to a different one. */
public class VariantNotFoundException extends RuntimeException {

    public VariantNotFoundException(String identifier) {
        super("Variant " + identifier + " was not found");
    }
}
