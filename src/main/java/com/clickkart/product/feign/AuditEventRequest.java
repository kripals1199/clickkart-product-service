// src/main/java/com/clickkart/user/feign/AuditEventRequest.java
package com.clickkart.product.feign;

import com.clickkart.product.enums.ProductAuditAction;
import java.time.Instant;

/**
 * The wire shape the Audit Log Service accepts, matched field-for-field against {@code
 * com.clickkart.auditlog.dto.request.AuditEventRequest}: correlationId, actor, action, ipAddress,
 * timestamp, details. Own copy per Rule 4 (no shared library).
 *
 * <p>{@code action} serializes as a plain string, which is exactly what the sink stores - it
 * deliberately does not bind to any one service's enum, so {@link ProductAuditAction} can be this
 * service's own vocabulary without the sink needing to know about it.
 */
public record AuditEventRequest(
        String correlationId, String actor, ProductAuditAction action, String ipAddress, Instant timestamp, String details) {

    public static AuditEventRequest of(
            String correlationId, String actor, ProductAuditAction action, String ipAddress, String details) {
        return new AuditEventRequest(correlationId, actor, action, ipAddress, Instant.now(), details);
    }
}
