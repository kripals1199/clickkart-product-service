// src/test/java/com/clickkart/user/jwt/JwtAuthenticationFilterTest.java
package com.clickkart.product.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.clickkart.product.exception.DownstreamServiceUnavailableException;
import com.clickkart.product.exception.MissingCorrelationIdException;
import com.clickkart.product.security.AuthenticatedPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.FilterChain;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * The security boundary this service depends on. The first test is the important one: it pins the
 * decision to ignore Gateway-supplied identity headers, which is what stops anything able to reach
 * this service directly from impersonating a customer.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtAuthenticationFilterTest {

    private static final String USER_ID = "usr_real_subject";

    @Mock private JwtService jwtService;
    @Mock private com.clickkart.product.security.RevocationService revocationService;
    @Mock private HandlerExceptionResolver handlerExceptionResolver;
    @Mock private FilterChain filterChain;
    @Mock private Claims claims;

    private JwtAuthenticationFilter filter() {
        return new JwtAuthenticationFilter(
                jwtService, revocationService, handlerExceptionResolver, List.of("/actuator/health"), List.of());
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void stubValidToken() {
        when(claims.getSubject()).thenReturn(USER_ID);
        when(claims.getId()).thenReturn("jti-1");
        when(claims.getExpiration()).thenReturn(new Date(System.currentTimeMillis() + 60_000));
        when(claims.get(JwtClaimNames.CORRELATION_ID, String.class)).thenReturn("corr-1");
        when(claims.get(JwtClaimNames.ROLES, String.class)).thenReturn("ROLE_USER");
        when(jwtService.parseAndValidate("good-token")).thenReturn(claims);
        when(revocationService.isRevoked("jti-1")).thenReturn(false);
    }

    @Test
    void identityComesFromTheTokenAndSpoofedGatewayHeadersAreIgnored() throws Exception {
        // A caller reaching this service directly claims to be someone else via the very headers
        // the Gateway legitimately sets. The token says otherwise, and the token must win.
        stubValidToken();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products/admin");
        request.addHeader("Authorization", "Bearer good-token");
        request.addHeader("X-User-Id", "usr_victim");
        request.addHeader("X-User-Roles", "ROLE_ADMIN");

        // The principal has to be read while the chain is executing - the filter clears the
        // SecurityContext in a finally block, so it is already gone by the time the call returns.
        AtomicReference<AuthenticatedPrincipal> seen = new AtomicReference<>();
        doAnswer(invocation -> {
            seen.set((AuthenticatedPrincipal)
                    SecurityContextHolder.getContext().getAuthentication().getPrincipal());
            return null;
        }).when(filterChain).doFilter(any(), any());

        filter().doFilterInternal(request, new MockHttpServletResponse(), filterChain);

        assertThat(seen.get()).isNotNull();
        assertThat(seen.get().userId()).isEqualTo(USER_ID);
        assertThat(seen.get().userId()).isNotEqualTo("usr_victim");
        assertThat(seen.get().roles()).containsExactly("ROLE_USER");
        assertThat(seen.get().roles()).doesNotContain("ROLE_ADMIN");
    }

    @Test
    void aRequestWithNoAuthorizationHeaderIsRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products/admin");

        filter().doFilterInternal(request, new MockHttpServletResponse(), filterChain);

        verify(handlerExceptionResolver)
                .resolveException(any(), any(), isNull(), any(BadCredentialsException.class));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void aRequestCarryingOnlyGatewayHeadersAndNoTokenIsRejected() throws Exception {
        // The impersonation attempt in its purest form: no token at all, just the headers.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products/admin");
        request.addHeader("X-User-Id", "usr_victim");
        request.addHeader("X-User-Roles", "ROLE_ADMIN");

        filter().doFilterInternal(request, new MockHttpServletResponse(), filterChain);

        verify(handlerExceptionResolver)
                .resolveException(any(), any(), isNull(), any(BadCredentialsException.class));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void anInvalidSignatureIsRejected() throws Exception {
        when(jwtService.parseAndValidate("bad-token")).thenThrow(new MalformedJwtException("nope"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products/admin");
        request.addHeader("Authorization", "Bearer bad-token");

        filter().doFilterInternal(request, new MockHttpServletResponse(), filterChain);

        verify(handlerExceptionResolver)
                .resolveException(any(), any(), isNull(), any(BadCredentialsException.class));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void aRevokedTokenIsRejectedSoLogoutTakesEffectImmediately() throws Exception {
        stubValidToken();
        when(revocationService.isRevoked("jti-1")).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products/admin");
        request.addHeader("Authorization", "Bearer good-token");

        filter().doFilterInternal(request, new MockHttpServletResponse(), filterChain);

        verify(handlerExceptionResolver)
                .resolveException(any(), any(), isNull(), any(BadCredentialsException.class));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void anUnreachableRevocationStoreFailsClosedRatherThanAssumingNotRevoked() throws Exception {
        // Treating a Redis outage as "not revoked" would silently restore access to every
        // logged-out token for the duration of the outage.
        stubValidToken();
        when(revocationService.isRevoked("jti-1")).thenThrow(new QueryTimeoutException("redis down"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products/admin");
        request.addHeader("Authorization", "Bearer good-token");

        filter().doFilterInternal(request, new MockHttpServletResponse(), filterChain);

        verify(handlerExceptionResolver)
                .resolveException(any(), any(), isNull(), any(DownstreamServiceUnavailableException.class));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void aTokenWithoutTheCorrelationIdClaimIsRejected() throws Exception {
        stubValidToken();
        when(claims.get(JwtClaimNames.CORRELATION_ID, String.class)).thenReturn("  ");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products/admin");
        request.addHeader("Authorization", "Bearer good-token");

        filter().doFilterInternal(request, new MockHttpServletResponse(), filterChain);

        verify(handlerExceptionResolver)
                .resolveException(any(), any(), isNull(), any(MissingCorrelationIdException.class));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void publicPathsSkipAuthenticationEntirely() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        request.setServletPath("/actuator/health");

        filter().doFilterInternal(request, new MockHttpServletResponse(), filterChain);

        verify(filterChain).doFilter(any(), any());
        verify(handlerExceptionResolver, never()).resolveException(any(), any(), any(), any());
    }

    // ------------------------------------------------------------- optional auth
    // The public catalog. This is where this service's filter diverges from User Service's.

    private static final String CATALOG_PATH = "/api/v1/products/search";

    private JwtAuthenticationFilter filterWithOptionalCatalog() {
        return new JwtAuthenticationFilter(
                jwtService, revocationService, handlerExceptionResolver,
                List.of("/actuator/health"), List.of(CATALOG_PATH));
    }

    private MockHttpServletRequest catalogRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", CATALOG_PATH);
        request.setServletPath(CATALOG_PATH);
        return request;
    }

    @Test
    void anAnonymousRequestToAnOptionalAuthPathContinuesWithoutAPrincipal() throws Exception {
        // The regression this covers: the filter runs BEFORE Spring Security's authorization
        // rules, so rejecting a missing header here means an anonymous catalog request never
        // reaches permitAll at all - which is exactly what happened, and 401'd the shop front.
        filterWithOptionalCatalog().doFilterInternal(catalogRequest(), new MockHttpServletResponse(), filterChain);

        verify(filterChain).doFilter(any(), any());
        verify(handlerExceptionResolver, never()).resolveException(any(), any(), any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void aValidTokenOnAnOptionalAuthPathStillEstablishesThePrincipal() throws Exception {
        stubValidToken();
        MockHttpServletRequest request = catalogRequest();
        request.addHeader("Authorization", "Bearer good-token");

        AtomicReference<AuthenticatedPrincipal> seen = new AtomicReference<>();
        doAnswer(invocation -> {
            seen.set((AuthenticatedPrincipal)
                    SecurityContextHolder.getContext().getAuthentication().getPrincipal());
            return null;
        }).when(filterChain).doFilter(any(), any());

        filterWithOptionalCatalog().doFilterInternal(request, new MockHttpServletResponse(), filterChain);

        assertThat(seen.get()).isNotNull();
        assertThat(seen.get().userId()).isEqualTo(USER_ID);
    }

    @Test
    void aRevokedTokenOnAnOptionalAuthPathIsRejectedRatherThanTreatedAsAnonymous() throws Exception {
        // This is why the catalog gets optional auth rather than plain exemption: exemption would
        // skip validation entirely, so a logged-out token would sail through as "anonymous".
        stubValidToken();
        when(revocationService.isRevoked("jti-1")).thenReturn(true);
        MockHttpServletRequest request = catalogRequest();
        request.addHeader("Authorization", "Bearer good-token");

        filterWithOptionalCatalog().doFilterInternal(request, new MockHttpServletResponse(), filterChain);

        verify(handlerExceptionResolver)
                .resolveException(any(), any(), isNull(), any(BadCredentialsException.class));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void aMissingTokenOnAPathThatIsNotOptionalIsStillRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/products/admin");
        request.setServletPath("/api/v1/products/admin");

        filterWithOptionalCatalog().doFilterInternal(request, new MockHttpServletResponse(), filterChain);

        verify(handlerExceptionResolver)
                .resolveException(any(), any(), isNull(), any(BadCredentialsException.class));
        verify(filterChain, never()).doFilter(any(), any());
    }
}
