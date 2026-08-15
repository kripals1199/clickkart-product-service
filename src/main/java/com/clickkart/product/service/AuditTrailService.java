// src/main/java/com/clickkart/category/service/AuditTrailService.java
package com.clickkart.product.service;

import com.clickkart.product.enums.ProductAuditAction;
import com.clickkart.product.web.RequestMetadata;

/** Reports one event to the central Audit Log Service. Throws if the sink is unreachable. */
public interface AuditTrailService {

    void record(
            String correlationId, String actor, ProductAuditAction action, RequestMetadata requestMetadata, String details);
}
