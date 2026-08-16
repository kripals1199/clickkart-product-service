// src/main/java/com/clickkart/product/config/AuditChainSeeder.java
package com.clickkart.product.config;

import com.clickkart.product.entity.AuditChainHeadEntity;
import com.clickkart.product.repository.AuditChainHeadRepository;
import com.clickkart.product.service.AuditTrailService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the singleton chain-head row at startup if it is not already there, so that
 * {@code AuditTrailService.record} can assume it exists rather than having to create-if-missing
 * under its own lock on every audited write.
 *
 * <p>Idempotent, and runs early. There is no migration tool in this project, so seeding at startup
 * is how invariant rows get created - the same pattern Auth Service uses for its roles.
 */
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AuditChainSeeder implements ApplicationRunner {

    private final AuditChainHeadRepository auditChainHeadRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        if (auditChainHeadRepository.findById(AuditChainHeadEntity.SINGLETON_ID).isEmpty()) {
            auditChainHeadRepository.save(new AuditChainHeadEntity(AuditTrailService.GENESIS_HASH));
        }
    }
}
