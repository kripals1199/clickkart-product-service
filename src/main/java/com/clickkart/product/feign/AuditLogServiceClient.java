// src/main/java/com/clickkart/user/feign/AuditLogServiceClient.java
package com.clickkart.product.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Eureka-discovered client for the Audit Log Service. Wrapped with a Resilience4j circuit breaker
 * (see {@code resilience4j.circuitbreaker.instances.clickkart-audit-log-service.*} in the config
 * repository) and {@link AuditLogServiceClientFallbackFactory}, per Rule 9.
 *
 * <p>Path and header names mirror Auth Service's own client exactly - both call the same route on
 * the same service, they just cannot share a type.
 */
@FeignClient(name = AuditLogServiceClient.SERVICE_NAME, fallbackFactory = AuditLogServiceClientFallbackFactory.class)
public interface AuditLogServiceClient {

    String SERVICE_NAME = "clickkart-audit-log-service";
    String EVENTS_PATH = "/api/v1/audit-log/events";
    String CORRELATION_ID_HEADER = "X-Correlation-Id";

    @PostMapping(path = EVENTS_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
    void logEvent(@RequestHeader(CORRELATION_ID_HEADER) String correlationId, @RequestBody AuditEventRequest request);
}
