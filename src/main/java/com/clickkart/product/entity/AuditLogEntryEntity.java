// src/main/java/com/clickkart/product/entity/AuditLogEntryEntity.java
package com.clickkart.product.entity;

import com.clickkart.product.enums.AuditOutcome;
import com.clickkart.product.enums.ProductAuditAction;
import com.clickkart.product.util.Sha256;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One audited event, linked by hash to the one before it.
 *
 * <p><strong>Append-only.</strong> Every column is {@code updatable = false} and the repository
 * exposes no delete, so there is no supported way to change or remove an entry once written. That is
 * the point: an audit trail an operator can edit answers no question anyone would ask it.
 *
 * <p>{@code entryHash} covers this entry's own fields <em>and</em> its predecessor's hash, so
 * altering any row invalidates every hash after it. Deleting or reordering rows breaks the links.
 * {@code AuditTrailService.verifyChainIntegrity()} recomputes the lot and says where it broke.
 *
 * <p><strong>Never put personal data in {@code details}.</strong> This table is retained far longer
 * than the rows it describes and survives an erasure request that scrubs the profile itself -
 * recording the shape of a change (which fields, how many addresses) rather than its content is what
 * keeps a customer's home address out of a second, longer-lived store.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "audit_log_entries",
        indexes = {
            @Index(name = "idx_audit_log_entries_actor", columnList = "actor"),
            @Index(name = "idx_audit_log_entries_correlation_id", columnList = "correlation_id"),
            @Index(name = "idx_audit_log_entries_occurred_at", columnList = "occurred_at")
        })
public class AuditLogEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "audit_log_entry_seq_gen")
    @SequenceGenerator(name = "audit_log_entry_seq_gen", sequenceName = "audit_log_entry_seq", allocationSize = 1)
    private Long id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "correlation_id", nullable = false, updatable = false, length = 64)
    private String correlationId;

    /** A {@code userPublicId}, or {@code "system"} for unattended flows - never an email or phone number. */
    @Column(name = "actor", nullable = false, updatable = false, length = 64)
    private String actor;

    /*
     * JdbcTypeCode(VARCHAR) alongside @Enumerated(STRING) is deliberate, not redundant. Plain
     * @Enumerated(STRING) makes Hibernate 6+ generate a CHECK constraint listing every enum value
     * that existed at table-creation time. With ddl-auto=update and no migration tool, that
     * constraint is never widened - so the next value added to the action enum would be rejected by
     * a stale constraint in every environment where the table already exists. Forcing plain VARCHAR
     * sidesteps Hibernate's enum-aware DDL entirely, so no such constraint is ever created.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "action", nullable = false, updatable = false, length = 40)
    private ProductAuditAction action;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "outcome", nullable = false, updatable = false, length = 10)
    private AuditOutcome outcome;

    @Column(name = "ip_address", nullable = false, updatable = false, length = 45)
    private String ipAddress;

    @Column(name = "user_agent", updatable = false, length = 512)
    private String userAgent;

    @Column(name = "details", updatable = false, length = 1000)
    private String details;

    @Column(name = "previous_entry_hash", nullable = false, updatable = false, length = 64)
    private String previousEntryHash;

    @Column(name = "entry_hash", nullable = false, updatable = false, length = 64)
    private String entryHash;

    private AuditLogEntryEntity(
            Instant occurredAt,
            String correlationId,
            String actor,
            ProductAuditAction action,
            AuditOutcome outcome,
            String ipAddress,
            String userAgent,
            String details,
            String previousEntryHash,
            String entryHash) {
        this.occurredAt = occurredAt;
        this.correlationId = correlationId;
        this.actor = actor;
        this.action = action;
        this.outcome = outcome;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.details = details;
        this.previousEntryHash = previousEntryHash;
        this.entryHash = entryHash;
    }

    /**
     * Builds an entry linked to {@code previousEntryHash}. The caller supplies that hash while
     * holding the chain-head lock; this factory only computes a digest and reads no shared state.
     *
     * <p><strong>The timestamp is truncated to microseconds before it is hashed or stored</strong>,
     * and that is load-bearing rather than tidying. {@code Instant.now()} carries nanoseconds on a
     * modern JVM; the Postgres column is {@code timestamp(6)}, so the nanoseconds are silently
     * dropped on write. Hash the untruncated value and every entry fails its own integrity check the
     * moment it is read back - the chain reports itself as tampered with from the first row, which is
     * both alarming and useless, because a check that always fails tells you nothing when something
     * really is wrong. Truncating here makes the value that is hashed the same value that is stored.
     */
    public static AuditLogEntryEntity create(
            Instant occurredAt,
            String correlationId,
            String actor,
            ProductAuditAction action,
            AuditOutcome outcome,
            String ipAddress,
            String userAgent,
            String details,
            String previousEntryHash) {
        Instant storable = occurredAt.truncatedTo(ChronoUnit.MICROS);
        String entryHash = Sha256.hex(canonicalPayload(
                storable, correlationId, actor, action, outcome, ipAddress, userAgent, details, previousEntryHash));
        return new AuditLogEntryEntity(
                storable, correlationId, actor, action, outcome, ipAddress, userAgent, details,
                previousEntryHash, entryHash);
    }

    /** Recomputes what this row's hash should be from its own stored fields, to detect tampering. */
    public String recomputeHash() {
        return Sha256.hex(canonicalPayload(
                occurredAt, correlationId, actor, action, outcome, ipAddress, userAgent, details, previousEntryHash));
    }

    /**
     * Field order and separators must never change without a migration plan for the whole chain -
     * any change invalidates every hash ever computed, and the integrity check would then report the
     * entire table as tampered with.
     */
    private static String canonicalPayload(
            Instant occurredAt,
            String correlationId,
            String actor,
            ProductAuditAction action,
            AuditOutcome outcome,
            String ipAddress,
            String userAgent,
            String details,
            String previousEntryHash) {
        return String.join(
                "|",
                previousEntryHash,
                occurredAt.toString(),
                correlationId,
                actor,
                action.name(),
                outcome.name(),
                ipAddress,
                userAgent == null ? "" : userAgent,
                details == null ? "" : details);
    }
}
