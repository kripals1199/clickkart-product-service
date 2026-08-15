// src/main/java/com/clickkart/user/feign/AuditLogServiceClientFallbackFactory.java
package com.clickkart.product.feign;

import com.clickkart.product.exception.DownstreamServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * The Audit Log Service is a required dependency, matching how Auth Service treats it: on
 * open-circuit or error this logs locally at WARN with full detail (so the event isn't lost
 * entirely even though the request is about to fail) and then throws {@link
 * DownstreamServiceUnavailableException}, which Feign propagates to the caller as if the
 * underlying call had thrown it.
 *
 * <p>The service layer does not catch it, so the surrounding transaction rolls back and the write
 * never happens - a profile change is never persisted without a corresponding audit entry. The
 * trade-off is accepted deliberately: an unaudited write is a permanent, undetectable gap in a
 * tamper-evident chain, whereas a failed request is visible and retryable. It also means an audit
 * entry can exist for a change that later failed to commit; that direction is at least detectable,
 * because the entry names the correlation id of a request the caller saw fail.
 */
@Slf4j
@Component
public class AuditLogServiceClientFallbackFactory implements FallbackFactory<AuditLogServiceClient> {

    private static final String SERVICE_NAME = "Audit Log Service";

    @Override
    public AuditLogServiceClient create(Throwable cause) {
        return (correlationId, request) -> {
            log.warn(
                    "AUDIT_DISPATCH_FAILED correlationId={} actor={} action={} ipAddress={} timestamp={} details={} - audit-log-service unreachable, cause={}",
                    correlationId,
                    request.actor(),
                    request.action(),
                    request.ipAddress(),
                    request.timestamp(),
                    request.details(),
                    cause.toString());
            throw new DownstreamServiceUnavailableException(SERVICE_NAME, cause);
        };
    }
}
