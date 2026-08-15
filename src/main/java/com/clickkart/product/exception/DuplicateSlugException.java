// src/main/java/com/clickkart/product/exception/DuplicateSlugException.java
package com.clickkart.product.exception;

/** Another product already uses this slug - it forms a flat, customer-visible URL space. */
public class DuplicateSlugException extends RuntimeException {

    public DuplicateSlugException(String slug) {
        super("The slug '" + slug + "' is already used by another product");
    }
}
