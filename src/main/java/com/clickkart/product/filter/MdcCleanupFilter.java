// src/main/java/com/clickkart/user/filter/MdcCleanupFilter.java
package com.clickkart.product.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/** Runs outermost - guarantees MDC never leaks a correlationId onto a later, unrelated request handled by the same pooled Tomcat thread. */
public class MdcCleanupFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
