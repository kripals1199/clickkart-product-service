// src/main/java/com/clickkart/product/exception/DuplicateSkuException.java
package com.clickkart.product.exception;

/**
 * Another variant already uses this SKU.
 *
 * <p>SKUs are unique across the whole catalog, not per seller: Inventory keys stock on the SKU
 * alone and a warehouse operator scans it off a label with no idea which seller it belongs to.
 * Two sellers sharing one would make both of those ambiguous.
 */
public class DuplicateSkuException extends RuntimeException {

    public DuplicateSkuException(String sku) {
        super("The SKU '" + sku + "' is already in use");
    }
}
