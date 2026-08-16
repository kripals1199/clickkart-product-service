// src/main/java/com/clickkart/product/repository/AuditChainHeadRepository.java
package com.clickkart.product.repository;

import com.clickkart.product.entity.AuditChainHeadEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Extends the bare {@code Repository} marker rather than {@code JpaRepository}: this singleton
 * bookkeeping row has exactly two legitimate operations - save, and lock-and-read - and nothing
 * should ever be able to delete it or list "all" of them.
 */
@Repository
public interface AuditChainHeadRepository
        extends org.springframework.data.repository.Repository<AuditChainHeadEntity, Long> {

    AuditChainHeadEntity save(AuditChainHeadEntity head);

    Optional<AuditChainHeadEntity> findById(Long id);

    /**
     * Locks the singleton row for the caller's transaction, serialising every concurrent append onto
     * this one row.
     *
     * <p>That serialisation is the whole mechanism: without it two writers could read the same head
     * hash, both link to it, and fork the chain into two branches that each verify individually
     * while the trail as a whole is no longer a single sequence. It is also the throughput ceiling
     * on audited writes in this service, which is a deliberate trade - an audit trail that can be
     * raced is not tamper-evident.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from AuditChainHeadEntity h where h.id = :id")
    Optional<AuditChainHeadEntity> lockForUpdate(@Param("id") Long id);
}
