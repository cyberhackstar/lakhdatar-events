package com.neelastack.lakhdatar.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

/**
 * Emits one low-volume request completion event. Static assets are served by the web/edge tier,
 * while backend requests are logged only at DEBUG unless they are slow or unsuccessful.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class RequestLifecycleLoggingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestLifecycleLoggingFilter.class);
    private static final long SLOW_REQUEST_MS = 1000L;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long started = System.nanoTime();
        String method = request.getMethod();
        String path = request.getRequestURI();
        MDC.put("httpMethod", method);
        MDC.put("requestPath", path);
        try {
            chain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - started) / 1_000_000L;
            int status = response.getStatus();
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                MDC.put("userRole", safe(auth.getAuthorities().stream().findFirst().map(a -> a.getAuthority()).orElse(null)));
                Object principal = auth.getPrincipal();
                if (principal instanceof com.neelastack.lakhdatar.security.UserPrincipal p) {
                    MDC.put("userId", String.valueOf(p.userId()));
                }
            }
            Object[] fields = {
                    "event.category", "http",
                    "http.method", method,
                    "http.route", path,
                    "http.status_code", status,
                    "http.response.duration_ms", durationMs
            };
            String action = "http.request.completed";
            if (status >= 500) {
                EnterpriseLog.error(log, action, null, fields);
            } else if (status >= 400 || durationMs >= SLOW_REQUEST_MS) {
                EnterpriseLog.warn(log, action, concat(fields, "event.outcome", status >= 400 ? "failure" : "success"));
            } else {
                EnterpriseLog.debug(log, action, concat(fields, "event.outcome", "success"));
            }
        }
        MDC.remove("httpMethod");
        MDC.remove("requestPath");
        MDC.remove("userRole");
        MDC.remove("userId");
    }

    private static Object[] concat(Object[] source, Object... extra) {
        Object[] out = java.util.Arrays.copyOf(source, source.length + extra.length);
        System.arraycopy(extra, 0, out, source.length, extra.length);
        return out;
    }

    private static String safe(String value) { return value == null ? "unknown" : value.toUpperCase(Locale.ROOT); }
}
