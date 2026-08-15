// src/main/java/com/clickkart/category/config/WebConfig.java
package com.clickkart.product.config;

import com.clickkart.product.constant.ApiPaths;
import com.clickkart.product.filter.AccessLogFilter;
import com.clickkart.product.filter.CorrelationIdFilter;
import com.clickkart.product.filter.MdcCleanupFilter;
import com.clickkart.product.security.CorrelationIdGenerator;
import java.util.List;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * This service mints a correlation id when one is absent, following Captcha Service rather than
 * Notification/Audit Log.
 *
 * <p>The reason is the public catalog: an anonymous browser reaches these endpoints through the
 * Gateway, which passes public paths through untouched and therefore forwards no correlation id -
 * there is no token for it to have taken one from. A pure receiver would reject every catalog page
 * view. Rule 13 still holds for authenticated traffic: {@code JwtAuthenticationFilter} overwrites
 * the MDC value with the {@code correlationId} claim from the verified token, so a signed-in
 * request is traced by Auth Service's id and not by one invented here.
 */
@Configuration
public class WebConfig {

    private static final List<String> CORRELATION_ID_EXEMPT_PATHS = List.of(
            ApiPaths.ACTUATOR_HEALTH,
            ApiPaths.ACTUATOR_HEALTH_WILDCARD,
            ApiPaths.ACTUATOR_PROMETHEUS,
            ApiPaths.SWAGGER_UI,
            ApiPaths.SWAGGER_UI_WILDCARD,
            ApiPaths.API_DOCS_WILDCARD);

    @Bean
    public FilterRegistrationBean<MdcCleanupFilter> mdcCleanupFilter() {
        FilterRegistrationBean<MdcCleanupFilter> registration = new FilterRegistrationBean<>(new MdcCleanupFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter(
            CorrelationIdGenerator correlationIdGenerator) {
        FilterRegistrationBean<CorrelationIdFilter> registration = new FilterRegistrationBean<>(
                new CorrelationIdFilter(correlationIdGenerator, CORRELATION_ID_EXEMPT_PATHS));
        // Before the access log, so REQUEST_START already carries the id.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Bean
    public FilterRegistrationBean<AccessLogFilter> accessLogFilter() {
        FilterRegistrationBean<AccessLogFilter> registration = new FilterRegistrationBean<>(new AccessLogFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
