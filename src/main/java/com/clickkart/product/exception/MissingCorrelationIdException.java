// src/main/java/com/clickkart/user/exception/MissingCorrelationIdException.java
package com.clickkart.product.exception;

/** Rule 13: only Auth Service mints a correlation id; every other service requires one already present. */
public class MissingCorrelationIdException extends RuntimeException {

    public MissingCorrelationIdException(String message) {
        super(message);
    }
}
