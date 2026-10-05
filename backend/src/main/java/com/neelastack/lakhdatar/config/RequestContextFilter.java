package com.neelastack.lakhdatar.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component("lakhdatarRequestContextFilter")
public class RequestContextFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String ip = firstNonBlank(request.getHeader("CF-Connecting-IP"), request.getHeader("X-Real-IP"), request.getRemoteAddr());
        MDC.put("clientIp", limit(ip,64));
        MDC.put("requestScheme", request.getScheme());
        MDC.put("requestHost", limit(request.getHeader("Host"),160));
        try { chain.doFilter(request,response); } finally { MDC.remove("clientIp"); MDC.remove("requestScheme"); MDC.remove("requestHost"); }
    }
    private String firstNonBlank(String... values){ for(String v:values) if(v!=null&&!v.isBlank()) return v.trim(); return "unknown"; }
    private String limit(String v,int n){ return v==null?null:(v.length()<=n?v:v.substring(0,n)); }
}
