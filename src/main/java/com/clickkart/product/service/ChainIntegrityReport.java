// src/main/java/com/clickkart/product/service/ChainIntegrityReport.java
package com.clickkart.product.service;

/** Result of {@code AuditTrailService.verifyChainIntegrity()} - what a compliance check reads. */
public record ChainIntegrityReport(boolean intact, long entriesChecked, Long brokenAtEntryId, String reason) {

    public static ChainIntegrityReport intact(long entriesChecked) {
        return new ChainIntegrityReport(true, entriesChecked, null, null);
    }

    public static ChainIntegrityReport broken(long entriesChecked, long brokenAtEntryId, String reason) {
        return new ChainIntegrityReport(false, entriesChecked, brokenAtEntryId, reason);
    }
}
