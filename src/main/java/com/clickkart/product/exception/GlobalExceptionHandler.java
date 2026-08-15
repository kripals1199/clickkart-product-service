// src/main/java/com/clickkart/product/exception/GlobalExceptionHandler.java
package com.clickkart.product.exception;

import com.clickkart.product.constant.LoggerNames;
import com.clickkart.product.constant.MdcKeys;
import com.clickkart.product.dto.ApiResponse;
import com.clickkart.product.dto.ErrorDetail;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Central mapping to the standard {@link ApiResponse} envelope (Rule 12) - own copy of the pattern
 * established in Auth Service (Rule 4).
 *
 * <p>Stack traces go through the logger or nowhere. The one place a trace is useful ({@link
 * #handleUnexpected}) logs it at ERROR with the correlation id attached; expected outcomes like a
 * 404 for a listing that is not on sale produce a single line and no trace.
 */
@Slf4j(topic = LoggerNames.SECURITY)
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String DEFAULT_FIELD_ERROR_MESSAGE = "invalid value";

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(
            BadCredentialsException ex, HttpServletRequest request) {
        return respond(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED, ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("ACCESS_DENIED path={} correlationId={}", request.getRequestURI(), MDC.get(MdcKeys.CORRELATION_ID));
        return respond(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED, "You do not have permission to perform this action", request);
    }

    @ExceptionHandler(MissingCorrelationIdException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingCorrelationId(
            MissingCorrelationIdException ex, HttpServletRequest request) {
        return respond(HttpStatus.UNAUTHORIZED, ErrorCode.MISSING_CORRELATION_ID, ex.getMessage(), request);
    }

    /**
     * Covers "no such product", "belongs to another seller" and - on public reads - "not ACTIVE"
     * alike. Collapsing them is deliberate: distinguishing would let a seller enumerate a
     * competitor's unpublished catalog.
     */
    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleProductNotFound(
            ProductNotFoundException ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, ErrorCode.PRODUCT_NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(VariantNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleVariantNotFound(
            VariantNotFoundException ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, ErrorCode.VARIANT_NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicateSlugException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateSlug(
            DuplicateSlugException ex, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, ErrorCode.DUPLICATE_SLUG, ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicateSkuException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateSku(
            DuplicateSkuException ex, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, ErrorCode.DUPLICATE_SKU, ex.getMessage(), request);
    }

    /** Names both the current state and the attempted action - the caller's next move depends on which. */
    @ExceptionHandler(InvalidProductStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidState(
            InvalidProductStateException ex, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, ErrorCode.INVALID_PRODUCT_STATE, ex.getMessage(), request);
    }

    /** 422 rather than 400: the request is well-formed, the prices are just not a legal combination. */
    @ExceptionHandler(InvalidPriceException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidPrice(
            InvalidPriceException ex, HttpServletRequest request) {
        return respond(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.INVALID_PRICE, ex.getMessage(), request);
    }

    /**
     * 403, not 400: the seller is authenticated and the request is fine - they are simply not
     * cleared to sell yet, and the fix is verification rather than a different payload.
     */
    @ExceptionHandler(SellerNotEligibleException.class)
    public ResponseEntity<ApiResponse<Void>> handleSellerNotEligible(
            SellerNotEligibleException ex, HttpServletRequest request) {
        log.warn("SELLER_NOT_ELIGIBLE path={} correlationId={}",
                request.getRequestURI(), MDC.get(MdcKeys.CORRELATION_ID));
        return respond(HttpStatus.FORBIDDEN, ErrorCode.SELLER_NOT_ELIGIBLE, ex.getMessage(), request);
    }

    /** Carries Category Service's own wording - the three refusal reasons need different fixes. */
    @ExceptionHandler(CategoryNotAssignableException.class)
    public ResponseEntity<ApiResponse<Void>> handleCategoryNotAssignable(
            CategoryNotAssignableException ex, HttpServletRequest request) {
        return respond(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.CATEGORY_NOT_ASSIGNABLE, ex.getMessage(), request);
    }

    /** Two writes to the same listing at once - retryable, so 409 rather than 500. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(
            OptimisticLockingFailureException ex, HttpServletRequest request) {
        log.warn("CONCURRENT_MODIFICATION path={} correlationId={} cause={}",
                request.getRequestURI(), MDC.get(MdcKeys.CORRELATION_ID), ex.toString());
        return respond(HttpStatus.CONFLICT, ErrorCode.CONCURRENT_MODIFICATION,
                "This listing was changed by another request - please retry", request);
    }

    @ExceptionHandler(DownstreamServiceUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleDownstreamUnavailable(
            DownstreamServiceUnavailableException ex, HttpServletRequest request) {
        log.error("DOWNSTREAM_UNAVAILABLE service={} path={} correlationId={} cause={}",
                ex.getServiceName(), request.getRequestURI(), MDC.get(MdcKeys.CORRELATION_ID), ex.toString());
        return respond(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.SERVICE_UNAVAILABLE, ex.getMessage(), request);
    }

    /** Conditional rules the annotations cannot express, e.g. a name that yields no usable slug. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fieldError -> fieldErrors.put(
                fieldError.getField(),
                fieldError.getDefaultMessage() == null ? DEFAULT_FIELD_ERROR_MESSAGE : fieldError.getDefaultMessage()));
        ErrorDetail errorDetail = ErrorDetail.withFieldErrors(ErrorCode.VALIDATION_FAILED, fieldErrors);
        return respond(HttpStatus.BAD_REQUEST, errorDetail, "One or more fields failed validation", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        fieldErrors.put(ex.getName(), DEFAULT_FIELD_ERROR_MESSAGE);
        ErrorDetail errorDetail = ErrorDetail.withFieldErrors(ErrorCode.VALIDATION_FAILED, fieldErrors);
        return respond(HttpStatus.BAD_REQUEST, errorDetail, "One or more fields failed validation", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMalformedRequestBody(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Request body is missing or malformed", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "An unexpected error occurred", request);
    }

    private ResponseEntity<ApiResponse<Void>> respond(
            HttpStatus status, String code, String message, HttpServletRequest request) {
        return respond(status, ErrorDetail.of(code), message, request);
    }

    private ResponseEntity<ApiResponse<Void>> respond(
            HttpStatus status, ErrorDetail errorDetail, String message, HttpServletRequest request) {
        String correlationId = MDC.get(MdcKeys.CORRELATION_ID);
        ApiResponse<Void> body =
                ApiResponse.error(status.value(), errorDetail, message, request.getRequestURI(), correlationId);
        return ResponseEntity.status(status).body(body);
    }

    /** Stable, machine-readable codes a UI can switch on - never the free-text message. */
    private static final class ErrorCode {
        private ErrorCode() {}

        static final String UNAUTHENTICATED = "UNAUTHENTICATED";
        static final String ACCESS_DENIED = "ACCESS_DENIED";
        static final String MISSING_CORRELATION_ID = "MISSING_CORRELATION_ID";
        static final String PRODUCT_NOT_FOUND = "PRODUCT_NOT_FOUND";
        static final String VARIANT_NOT_FOUND = "VARIANT_NOT_FOUND";
        static final String DUPLICATE_SLUG = "DUPLICATE_SLUG";
        static final String DUPLICATE_SKU = "DUPLICATE_SKU";
        static final String INVALID_PRODUCT_STATE = "INVALID_PRODUCT_STATE";
        static final String INVALID_PRICE = "INVALID_PRICE";
        static final String SELLER_NOT_ELIGIBLE = "SELLER_NOT_ELIGIBLE";
        static final String CATEGORY_NOT_ASSIGNABLE = "CATEGORY_NOT_ASSIGNABLE";
        static final String CONCURRENT_MODIFICATION = "CONCURRENT_MODIFICATION";
        static final String SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";
        static final String VALIDATION_FAILED = "VALIDATION_FAILED";
        static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    }
}
