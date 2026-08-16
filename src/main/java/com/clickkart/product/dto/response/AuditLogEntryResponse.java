// src/main/java/com/clickkart/product/dto/response/AuditLogEntryResponse.java
package com.clickkart.product.dto.response;

import com.clickkart.product.entity.AuditLogEntryEntity;
import com.clickkart.product.enums.AuditOutcome;
import com.clickkart.product.enums.ProductAuditAction;
import java.time.Instant;

/**
 * One audit entry as an operator reads it.
 *
 * <p>Both hashes are exposed. They are not secrets - they are derived from the entry's own contents -
 * and showing them is what lets somebody spot-check a link by eye, or quote a specific entry in an
 * incident write-up without ambiguity.
 */
public record AuditLogEntryResponse(
        Long id,
        Instant occurredAt,
        String correlationId,
        String actor,
        ProductAuditAction action,
        AuditOutcome outcome,
        String ipAddress,
        String details,
        String previousEntryHash,
        String entryHash) {

    public static AuditLogEntryResponse from(AuditLogEntryEntity entity) {
        return new AuditLogEntryResponse(
                entity.getId(),
                entity.getOccurredAt(),
                entity.getCorrelationId(),
                entity.getActor(),
                entity.getAction(),
                entity.getOutcome(),
                entity.getIpAddress(),
                entity.getDetails(),
                entity.getPreviousEntryHash(),
                entity.getEntryHash());
    }
}
