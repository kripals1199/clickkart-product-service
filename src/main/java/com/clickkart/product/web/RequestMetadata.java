// src/main/java/com/clickkart/user/web/RequestMetadata.java
package com.clickkart.product.web;

/**
 * HTTP-request-derived context threaded from the controllers through the service layer into the
 * audit trail - bundled into one record instead of a growing list of {@code String} parameters on
 * every service method.
 */
public record RequestMetadata(String ipAddress, String userAgent) {}
