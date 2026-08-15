// src/main/java/com/clickkart/product/exception/SellerNotEligibleException.java
package com.clickkart.product.exception;

/**
 * The seller has no verified business profile, so they may not put anything on sale.
 *
 * <p>Checked at submit rather than at creation: drafting a listing before verification completes is
 * reasonable and lets a seller prepare while an operator reviews their GSTIN. Publishing before it
 * completes is not.
 */
public class SellerNotEligibleException extends RuntimeException {

    public SellerNotEligibleException(String reason) {
        super(reason);
    }
}
