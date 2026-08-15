// src/main/java/com/clickkart/user/exception/DownstreamServiceUnavailableException.java
package com.clickkart.product.exception;

/** A required dependency (Redis, Audit Log Service) could not be reached - surfaces as 503, never a generic 500. */
public class DownstreamServiceUnavailableException extends RuntimeException {

    private final String serviceName;

    public DownstreamServiceUnavailableException(String serviceName, Throwable cause) {
        super(serviceName + " is unavailable", cause);
        this.serviceName = serviceName;
    }

    public String getServiceName() {
        return serviceName;
    }
}
