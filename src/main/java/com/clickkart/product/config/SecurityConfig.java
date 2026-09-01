// src/main/java/com/clickkart/category/config/SecurityConfig.java
package com.clickkart.product.config;

import com.clickkart.product.constant.ApiPaths;
import com.clickkart.product.jwt.JwtAuthenticationFilter;
import com.clickkart.product.jwt.JwtService;
import com.clickkart.product.security.AuthenticatedPrincipal;
import com.clickkart.product.security.InternalApiKeyFilter;
import com.clickkart.product.security.RestAccessDeniedHandler;
import com.clickkart.product.security.RestAuthenticationEntryPoint;
import com.clickkart.product.security.RevocationService;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * The first service in this platform with a genuinely public read surface, which makes the
 * authorization shape different from every one before it.
 *
 * <p><strong>Catalog reads are permitted by method, not just by path.</strong> {@code GET
 * /api/v1/categories/**} is anonymous; every other verb on the same prefix requires
 * authentication. Permitting the prefix outright would have made {@code POST
 * /api/v1/categories/admin} anonymous too, and the only thing standing between that and an
 * unauthenticated catalog rewrite would have been the {@code @PreAuthorize} on the controller -
 * one annotation, on one class, with nothing behind it. Spring Security can express the method
 * distinction even though the Gateway's path-only public list cannot, so it is expressed here.
 *
 * <p>{@link JwtAuthenticationFilter} still runs on public paths' behalf in one respect: it is
 * skipped for them, so an anonymous request is never rejected for lacking a token. Writes carry a
 * token, are validated here, and are then role-checked by {@code @PreAuthorize}.
 */
@Configuration
@EnableConfigurationProperties(ProductProperties.class)
@EnableMethodSecurity
public class SecurityConfig {

    private static final String SYSTEM_ACTOR = "system";

    /** Infrastructure paths - health, metrics, docs. */
    private static final List<String> INFRA_PATHS = List.of(
            ApiPaths.ACTUATOR_HEALTH,
            ApiPaths.ACTUATOR_HEALTH_WILDCARD,
            ApiPaths.ACTUATOR_PROMETHEUS,
            ApiPaths.SWAGGER_UI,
            ApiPaths.SWAGGER_UI_WILDCARD,
            ApiPaths.API_DOCS_WILDCARD);

    /**
     * The public catalog, enumerated rather than written as {@code /api/v1/categories/**}.
     *
     * <p>A blanket wildcard would also cover {@code /api/v1/categories/admin/**}, and these same
     * patterns are handed to the JWT filter as optional-auth paths - so the wildcard would make an
     * unauthenticated admin request continue anonymously instead of being turned away at the filter.
     * It would still be refused by the authorization rules below, but as a confusing 403 on a
     * request that never had a principal rather than a clean 401.
     *
     * <p>The {@code PRD-*} pattern is safe for the same reason the routes are: a publicId is always
     * {@code PRD-<uuid>}, so it cannot match the literal {@code seller} or {@code admin} segments.
     */
    private static final List<String> CATALOG_READ_PATHS = List.of(
            ApiPaths.SEARCH,
            ApiPaths.BASE + "/slug/*",
            ApiPaths.BASE + "/PRD-*",
            // A product image has to render for a signed-out shopper. The filename is a UUID, so
            // an unpublished draft is not enumerable even though the route is open.
            ApiPaths.MEDIA_FILE,
            // The customer-facing brand filter reads this too, so it cannot need a token. Only
            // GET is permitted here; POST falls through to authenticated, and the controller
            // requires ROLE_SELLER on top.
            ApiPaths.BRANDS,
            // Reviews read anonymously: a shopper deciding whether to buy is who they are for,
            // and a sign-in wall would leave the stars on the listing pages unexplainable.
            //
            // Single-segment wildcards, so these reach /PRD-x/reviews and /PRD-x/reviews/summary
            // and deliberately do NOT reach /PRD-x/reviews/mine - that one needs a session and
            // falls through to authenticated() below.
            ApiPaths.BASE + "/PRD-*/reviews",
            ApiPaths.BASE + "/PRD-*/reviews/summary");

    private static final String ADMIN_WILDCARD = ApiPaths.ADMIN_BASE + "/**";

    /** Seller-scoped writes. Authenticated here; ROLE_SELLER enforced on the controller. */
    private static final String SELLER_WILDCARD = ApiPaths.SELLER_BASE + "/**";

    private static final List<String> INTERNAL_PATHS = List.of(ApiPaths.INTERNAL_WILDCARD);

    /**
     * Paths {@link JwtAuthenticationFilter} must not attempt to authenticate. Deliberately a
     * different list from what is permitted: "the JWT filter skips this" and "anyone may call this"
     * are separate questions, and conflating them is how an internal endpoint quietly becomes an
     * anonymous one.
     */
    private static final List<String> JWT_EXEMPT_PATHS =
            Stream.concat(INFRA_PATHS.stream(), INTERNAL_PATHS.stream()).toList();

    private static final long HSTS_MAX_AGE_SECONDS = Duration.ofDays(365).toSeconds();

    private static final String CONTENT_SECURITY_POLICY = "default-src 'self'; frame-ancestors 'none'";

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(
            JwtService jwtService,
            RevocationService revocationService,
            HandlerExceptionResolver handlerExceptionResolver) {
        // Catalog reads get optional auth rather than exemption: a signed-in customer browsing may
        // present a token, and it must still be validated - a revoked one must not pass as
        // "anonymous". Exemption would skip that check entirely. See the filter's own Javadoc.
        return new JwtAuthenticationFilter(
                jwtService, revocationService, handlerExceptionResolver, JWT_EXEMPT_PATHS, CATALOG_READ_PATHS);
    }

    @Bean
    public InternalApiKeyFilter internalApiKeyFilter(
            ProductProperties productProperties, HandlerExceptionResolver handlerExceptionResolver) {
        return new InternalApiKeyFilter(
                productProperties.getInternalApiKey(), handlerExceptionResolver, INTERNAL_PATHS);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(ProductProperties productProperties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(
                Arrays.stream(productProperties.getAllowedOrigins().split(",")).map(String::trim).toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            InternalApiKeyFilter internalApiKeyFilter,
            CorsConfigurationSource corsConfigurationSource,
            RestAuthenticationEntryPoint restAuthenticationEntryPoint,
            RestAccessDeniedHandler restAccessDeniedHandler)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(INFRA_PATHS.toArray(new String[0]))
                        .permitAll()
                        .requestMatchers(INTERNAL_PATHS.toArray(new String[0]))
                        .hasRole("INTERNAL")
                        // Before the catalog rule: first match wins, and the admin tree is a GET.
                        // Without this ordering, GET /admin/tree would be permitted anonymously
                        // and then fail inside @PreAuthorize with no principal to check.
                        .requestMatchers(ADMIN_WILDCARD)
                        .hasRole("ADMIN")
                        // Also before the catalog rule: the seller dashboard is a GET, and would
                        // otherwise be permitted anonymously and then fail inside @PreAuthorize
                        // with no principal to check.
                        .requestMatchers(SELLER_WILDCARD)
                        .hasRole("SELLER")
                        // GET only. Every other verb falls through to authenticated() below.
                        .requestMatchers(HttpMethod.GET, CATALOG_READ_PATHS.toArray(new String[0]))
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(internalApiKeyFilter, JwtAuthenticationFilter.class)
                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler))
                .headers(headers -> headers
                        .frameOptions(frameOptions -> frameOptions.deny())
                        .contentTypeOptions(contentTypeOptions -> {})
                        .cacheControl(cacheControl -> {})
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(HSTS_MAX_AGE_SECONDS)));
        return http.build();
    }

    /**
     * createdBy/updatedBy source (Rule 3). Falls back to {@value #SYSTEM_ACTOR} for the anonymous
     * catalog reads, which never write anything - the fallback exists so JPA auditing has a
     * non-null value, not because an anonymous caller can create a row.
     */
    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .map(authentication -> authentication.getPrincipal())
                .filter(AuthenticatedPrincipal.class::isInstance)
                .map(AuthenticatedPrincipal.class::cast)
                .map(AuthenticatedPrincipal::userId)
                .or(() -> Optional.of(SYSTEM_ACTOR));
    }
}
