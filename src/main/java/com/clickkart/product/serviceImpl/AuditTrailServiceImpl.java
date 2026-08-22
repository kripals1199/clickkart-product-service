// src/main/java/com/clickkart/product/serviceImpl/AuditTrailServiceImpl.java
package com.clickkart.product.serviceImpl;

import com.clickkart.product.constant.LoggerNames;
import com.clickkart.product.entity.AuditChainHeadEntity;
import com.clickkart.product.entity.AuditLogEntryEntity;
import com.clickkart.product.enums.AuditOutcome;
import com.clickkart.product.enums.ProductAuditAction;
import com.clickkart.product.feign.AuditEventRequest;
import com.clickkart.product.feign.AuditLogServiceClient;
import com.clickkart.product.repository.AuditChainHeadRepository;
import com.clickkart.product.repository.AuditLogEntryRepository;
import com.clickkart.product.service.AuditTrailService;
import com.clickkart.product.service.ChainIntegrityReport;
import com.clickkart.product.web.RequestMetadata;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the local hash-chained entry first, then dispatches to the central Audit Log Service.
 *
 * <p><strong>Local first, and the order is the design.</strong> The local write is the one that must
 * not be lost, so it happens while the chain-head lock is held and inside a transaction. The central
 * dispatch is best-effort afterwards: if it throws, the local entry still stands and the exception is
 * logged rather than propagated, because failing a customer's request over a reporting call would
 * turn an aggregation problem into an outage.
 *
 * <p>That is a deliberate reversal of what this class did before, when the central call was the only
 * write and its failure took the request with it.
 */
@Slf4j(topic = LoggerNames.AUDIT)
@Service
@RequiredArgsConstructor
public class AuditTrailServiceImpl implements AuditTrailService {

    private final AuditLogEntryRepository auditLogEntryRepository;
    private final AuditChainHeadRepository auditChainHeadRepository;
    private final AuditLogServiceClient auditLogServiceClient;

    @Override
    // Annotated even though this only delegates: the delegation goes through `this`, so the
    // proxy never sees it and the target's own @Transactional would be skipped. Callers that
    // already have a transaction join this one; callers that have none - placeOrder, which is
    // deliberately not transactional - get the one lockForUpdate needs.
    @Transactional(rollbackFor = Exception.class)
    public void record(
            String correlationId,
            String actor,
            ProductAuditAction action,
            RequestMetadata requestMetadata,
            String details) {
        record(correlationId, actor, action, AuditOutcome.SUCCESS, requestMetadata, details);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void record(
            String correlationId,
            String actor,
            ProductAuditAction action,
            AuditOutcome outcome,
            RequestMetadata requestMetadata,
            String details) {

        AuditChainHeadEntity head = auditChainHeadRepository
                .lockForUpdate(AuditChainHeadEntity.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "Audit chain head row missing - AuditChainSeeder should have created it at startup"));

        AuditLogEntryEntity entry = AuditLogEntryEntity.create(
                Instant.now(),
                correlationId,
                actor,
                action,
                outcome,
                requestMetadata.ipAddress(),
                requestMetadata.userAgent(),
                details,
                head.getLastEntryHash());
        auditLogEntryRepository.save(entry);

        head.advance(entry.getEntryHash());
        auditChainHeadRepository.save(head);

        dispatchCentrally(correlationId, actor, action, requestMetadata, details);
    }

    /**
     * Best-effort. A failure here means the central trail is missing an event the local chain has,
     * which is a reconciliation problem for someone reading both - not a reason to fail the customer
     * whose request produced it.
     */
    private void dispatchCentrally(
            String correlationId,
            String actor,
            ProductAuditAction action,
            RequestMetadata requestMetadata,
            String details) {
        try {
            auditLogServiceClient.logEvent(
                    correlationId,
                    AuditEventRequest.of(correlationId, actor, action, requestMetadata.ipAddress(), details));
        } catch (RuntimeException e) {
            log.error("AUDIT_CENTRAL_DISPATCH_FAILED correlationId={} actor={} action={} cause={} - "
                            + "the local chain has this event; the central trail does not",
                    correlationId, actor, action, e.toString());
        }
    }

    @Override
    @Transactional(readOnly = true, rollbackFor = Exception.class)
    public ChainIntegrityReport verifyChainIntegrity() {
        List<AuditLogEntryEntity> entries = auditLogEntryRepository.findAllByOrderByIdAsc();

        String expectedPreviousHash = GENESIS_HASH;
        for (AuditLogEntryEntity entry : entries) {
            if (!expectedPreviousHash.equals(entry.getPreviousEntryHash())) {
                return ChainIntegrityReport.broken(
                        entries.size(),
                        entry.getId(),
                        "previousEntryHash does not match the prior entry's hash - chain link broken");
            }
            if (!entry.recomputeHash().equals(entry.getEntryHash())) {
                return ChainIntegrityReport.broken(
                        entries.size(),
                        entry.getId(),
                        "recomputed hash does not match the stored entryHash - entry may have been tampered with");
            }
            expectedPreviousHash = entry.getEntryHash();
        }
        return ChainIntegrityReport.intact(entries.size());
    }

    @Override
    @Transactional(readOnly = true, rollbackFor = Exception.class)
    public Page<AuditLogEntryEntity> browse(Pageable pageable) {
        return auditLogEntryRepository.findAllByOrderByIdAsc(pageable);
    }
}
