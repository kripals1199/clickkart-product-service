// src/main/java/com/clickkart/category/serviceImpl/AuditTrailServiceImpl.java
package com.clickkart.product.serviceImpl;

import com.clickkart.product.constant.LoggerNames;
import com.clickkart.product.enums.ProductAuditAction;
import com.clickkart.product.feign.AuditEventRequest;
import com.clickkart.product.feign.AuditLogServiceClient;
import com.clickkart.product.service.AuditTrailService;
import com.clickkart.product.web.RequestMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Thin dispatcher to the central hash-chained trail. Required, not best-effort: a failure aborts the
 * surrounding write rather than letting a catalog change land unrecorded.
 *
 * <p>{@code details} names what changed. A listing is public commercial data once approved, not
 * personal data, so the reason User Service keeps its audit details abstract does not apply - and
 * an operator investigating "who approved this counterfeit" needs the actual answer.
 *
 * <p>Prices are recorded on moderation decisions for the same reason: a dispute about what was
 * approved at what price is exactly what this trail exists to settle.
 */
@Slf4j(topic = LoggerNames.AUDIT)
@Service
@RequiredArgsConstructor
public class AuditTrailServiceImpl implements AuditTrailService {

    private final AuditLogServiceClient auditLogServiceClient;

    @Override
    public void record(
            String correlationId, String actor, ProductAuditAction action, RequestMetadata requestMetadata, String details) {
        AuditEventRequest request =
                AuditEventRequest.of(correlationId, actor, action, requestMetadata.ipAddress(), details);
        auditLogServiceClient.logEvent(correlationId, request);
        log.info("AUDIT_DISPATCHED correlationId={} actor={} action={} details={}", correlationId, actor, action, details);
    }
}
