// src/main/java/com/clickkart/product/controller/AuditController.java
package com.clickkart.product.controller;

import com.clickkart.product.constant.ApiPaths;
import com.clickkart.product.constant.MdcKeys;
import com.clickkart.product.dto.ApiResponse;
import com.clickkart.product.dto.PageResponse;
import com.clickkart.product.dto.response.AuditLogEntryResponse;
import com.clickkart.product.service.AuditTrailService;
import com.clickkart.product.service.ChainIntegrityReport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reading this service's own activity log. ADMIN only, and read-only.
 *
 * <p>ADMIN because the trail names every actor who touched a profile, which makes it a record of who
 * did what to whom - more sensitive in aggregate than any single row it describes.
 *
 * <p>Read-only because the repository behind it exposes no delete or update at all. There is no
 * endpoint here that could alter an entry, and there should never be one: a trail an operator can
 * edit answers none of the questions it exists to answer.
 */
@Tag(name = "Audit", description = "This service's own tamper-evident activity log (ROLE_ADMIN)")
@RestController
@RequiredArgsConstructor
public class AuditController {

    private final AuditTrailService auditTrailService;

    @Operation(summary = "Browse this service's activity log, oldest first")
    @GetMapping(ApiPaths.ADMIN_AUDIT)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<AuditLogEntryResponse>>> browse(
            Pageable pageable, HttpServletRequest request) {
        PageResponse<AuditLogEntryResponse> page =
                PageResponse.from(auditTrailService.browse(pageable).map(AuditLogEntryResponse::from));
        return envelope(page, request);
    }

    /**
     * Recomputes every hash and every link, and reports the first entry that does not add up.
     *
     * <p>Deliberately on demand rather than scheduled. A background check that quietly passes tells
     * nobody anything; the moment this matters is when somebody has a reason to ask, and then they
     * want an answer about the chain as it stands right now.
     */
    @Operation(summary = "Verify the hash chain has not been tampered with")
    @GetMapping(ApiPaths.ADMIN_AUDIT_VERIFY)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ChainIntegrityReport>> verify(HttpServletRequest request) {
        return envelope(auditTrailService.verifyChainIntegrity(), request);
    }

    private <T> ResponseEntity<ApiResponse<T>> envelope(T data, HttpServletRequest request) {
        String correlationId = MDC.get(MdcKeys.CORRELATION_ID);
        return ResponseEntity.ok(
                ApiResponse.success(HttpStatus.OK.value(), data, request.getRequestURI(), correlationId));
    }
}
