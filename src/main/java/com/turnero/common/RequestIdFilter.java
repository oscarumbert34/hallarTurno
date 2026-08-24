package com.turnero.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Request-Id";
    public static final String ATTRIBUTE_NAME = "requestId";
    public static final String MDC_KEY = "requestId";

    private static final int MAX_REQUEST_ID_LENGTH = 64;
    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);

    @Override
    protected void doFilterInternal(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final FilterChain filterChain
    ) throws ServletException, IOException {
        final long startedAt = System.currentTimeMillis();
        final String requestId = resolveRequestId(request);
        request.setAttribute(ATTRIBUTE_NAME, requestId);
        response.setHeader(HEADER_NAME, requestId);
        MDC.put(MDC_KEY, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            logRequest(request, response, System.currentTimeMillis() - startedAt);
            MDC.remove(MDC_KEY);
        }
    }

    public static String currentRequestId(final HttpServletRequest request) {
        final Object requestId = request.getAttribute(ATTRIBUTE_NAME);
        if (requestId instanceof String value && !value.isBlank()) {
            return value;
        }
        return null;
    }

    private String resolveRequestId(final HttpServletRequest request) {
        final String incomingRequestId = request.getHeader(HEADER_NAME);
        if (incomingRequestId == null || incomingRequestId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        final String sanitized = incomingRequestId.trim().replaceAll("[^A-Za-z0-9._-]", "");
        if (sanitized.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return sanitized.length() > MAX_REQUEST_ID_LENGTH
                ? sanitized.substring(0, MAX_REQUEST_ID_LENGTH)
                : sanitized;
    }

    private void logRequest(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final long durationMillis
    ) {
        final int status = response.getStatus();
        final String message = "request method={} path={} status={} durationMs={}";
        if (status >= 500) {
            log.error(message, request.getMethod(), request.getRequestURI(), status, durationMillis);
        } else if (status >= 400) {
            log.warn(message, request.getMethod(), request.getRequestURI(), status, durationMillis);
        } else {
            log.info(message, request.getMethod(), request.getRequestURI(), status, durationMillis);
        }
    }
}
