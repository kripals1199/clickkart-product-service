// src/main/java/com/clickkart/product/exception/ReviewNotFoundException.java
package com.clickkart.product.exception;

/**
 * Raised when a review id does not exist <em>or does not belong to the caller</em> on the paths
 * where ownership is required.
 *
 * <p>Both collapse into one 404, for the reason {@code AddressNotFoundException} spells out in User
 * Service: answering 403 for the second case confirms the id is real and belongs to someone.
 */
public class ReviewNotFoundException extends RuntimeException {

    public ReviewNotFoundException(String identifier) {
        super("Review " + identifier + " was not found");
    }
}
