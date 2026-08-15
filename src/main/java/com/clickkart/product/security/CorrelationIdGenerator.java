// src/main/java/com/clickkart/captcha/security/CorrelationIdGenerator.java
package com.clickkart.product.security;

import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * This service, unlike Notification/Audit Log Service, has a genuinely public entry point
 * ({@code POST /challenge}, reached directly by a browser through the Gateway - see {@code
 * JwtAuthenticationGlobalFilter}'s Javadoc: public paths pass through the Gateway untouched,
 * with no {@code X-Correlation-Id} added). There is therefore no upstream minter to require one
 * from the way Notification/Audit Log Service can require it from their sole caller, Auth
 * Service's Feign clients. {@code CorrelationIdFilter} uses this to mint one when absent, rather
 * than rejecting the request outright.
 */
@Component
public class CorrelationIdGenerator {

    public String generate() {
        return UUID.randomUUID().toString();
    }
}
