// src/main/java/com/clickkart/product/service/AuditTrailService.java
package com.clickkart.product.service;

import com.clickkart.product.entity.AuditLogEntryEntity;
import com.clickkart.product.enums.AuditOutcome;
import com.clickkart.product.enums.ProductAuditAction;
import com.clickkart.product.web.RequestMetadata;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * This service's own tamper-evident activity log, plus a dispatch to the central Audit Log Service.
 *
 * <p><strong>Two trails, on purpose.</strong> The local one is hash-chained and lives in this
 * service's database; the central one aggregates across the platform. Keeping both costs a duplicate
 * write and buys the thing neither gives alone: the local chain survives Audit Log Service being
 * unreachable, and the central trail answers questions that span services. An earlier version of
 * this class argued the opposite - that per-service copies "can disagree" - and they can. What that
 * argument missed is that the alternative is not one trail, it is one trail plus a silent gap
 * whenever the network blinks.
 *
 * <p><strong>Where this differs from Auth Service, and it matters.</strong> Auth's {@code record}
 * joins the caller's transaction, so a login cannot commit without its audit row - the audit-or-abort
 * guarantee. That works because Auth audits from inside its transactional service methods. Several
 * services here call {@code record} <em>after</em> their write has already committed, deliberately,
 * so that remote calls stay out of transactions and cannot pin a database connection. In those
 * services the local entry is written in its own transaction and a business write can therefore
 * succeed while its audit fails. The failure is loud rather than silent, and closing the gap
 * properly means moving the audit call inside the writer - which is a change to the call sites, not
 * to this class.
 */
public interface AuditTrailService {

    /** What the first entry links to. No real entry ever has this as its own hash. */
    String GENESIS_HASH = "0".repeat(64);

    /** Records a successful action. */
    void record(
            String correlationId,
            String actor,
            ProductAuditAction action,
            RequestMetadata requestMetadata,
            String details);

    /** Records an action with an explicit outcome, for the paths where failure is worth keeping. */
    void record(
            String correlationId,
            String actor,
            ProductAuditAction action,
            AuditOutcome outcome,
            RequestMetadata requestMetadata,
            String details);

    /**
     * Recomputes every entry's hash from its own stored fields and checks both the hash and the link
     * to its predecessor. Any mismatch means a row was altered, deleted or reordered after it was
     * written, and the report says which one.
     */
    ChainIntegrityReport verifyChainIntegrity();

    Page<AuditLogEntryEntity> browse(Pageable pageable);
}
