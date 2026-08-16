// src/main/java/com/clickkart/product/entity/AuditChainHeadEntity.java
package com.clickkart.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The tip of this service's audit hash chain. Exactly one row, always id {@value #SINGLETON_ID}.
 *
 * <p>Holding the last entry's hash in its own row is what makes the chain append-only in practice
 * rather than in principle: a writer locks this row, reads the hash it must link to, writes its
 * entry, and advances the head - so two concurrent writers cannot both link to the same predecessor
 * and fork the chain.
 *
 * <p><strong>The id is assigned, not generated</strong>, which is where this diverges from Auth
 * Service's copy. Auth's chain head inherits {@code BaseEntity}'s sequence generator, so the row it
 * seeds only lands on id 1 because it happens to be the first row that sequence ever issues - if
 * anything else claimed the sequence first, {@code lockForUpdate(1L)} would find nothing and every
 * audited write would fail. Fixing the id here removes that coupling entirely: the row is id 1
 * because it is declared to be, not because of when it was created.
 *
 * <p>It therefore does not extend {@code BaseEntity} either. The created/updated stamps that base
 * class adds describe who last changed a row, which is meaningless for a counter that only ever
 * moves forward and is never edited by a person.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "audit_chain_head")
public class AuditChainHeadEntity {

    /** There is exactly one row in this table, always with this id. */
    public static final long SINGLETON_ID = 1L;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Long id = SINGLETON_ID;

    @Column(name = "last_entry_hash", nullable = false, length = 64)
    private String lastEntryHash;

    @Column(name = "entry_count", nullable = false)
    private long entryCount;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public AuditChainHeadEntity(String genesisHash) {
        this.id = SINGLETON_ID;
        this.lastEntryHash = genesisHash;
        this.entryCount = 0;
        this.updatedAt = Instant.now();
    }

    public void advance(String newEntryHash) {
        this.lastEntryHash = newEntryHash;
        this.entryCount++;
        this.updatedAt = Instant.now();
    }
}
