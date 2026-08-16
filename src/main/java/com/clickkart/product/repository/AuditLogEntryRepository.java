// src/main/java/com/clickkart/product/repository/AuditLogEntryRepository.java
package com.clickkart.product.repository;

import com.clickkart.product.entity.AuditLogEntryEntity;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/**
 * Extends the bare {@code Repository} marker, not {@code JpaRepository}, so this interface exposes
 * no {@code delete}, {@code deleteById} or {@code deleteAll} at all. An audit trail is append-only
 * by design, and the cheapest way to guarantee that is to give the application no method to call.
 */
@Repository
public interface AuditLogEntryRepository
        extends org.springframework.data.repository.Repository<AuditLogEntryEntity, Long> {

    AuditLogEntryEntity save(AuditLogEntryEntity entry);

    long count();

    Page<AuditLogEntryEntity> findAllByOrderByIdAsc(Pageable pageable);

    /**
     * Full-table read in chain order, for the integrity check.
     *
     * <p>Loads the whole table into memory, which is fine for an on-demand check at current volume
     * and will not be forever. The natural next step is checkpointed verification - verify only
     * entries added since the last verified id - which needs somewhere to store that checkpoint.
     */
    List<AuditLogEntryEntity> findAllByOrderByIdAsc();
}
