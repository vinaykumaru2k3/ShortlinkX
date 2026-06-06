package com.link.shortlinkx.tracing;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
public class MdcCorrelationFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        if (request instanceof HttpServletRequest httpRequest) {
            String correlationId = httpRequest.getHeader(TraceConstants.CORRELATION_HEADER);
            if (correlationId == null || correlationId.trim().isEmpty()) {
                correlationId = UUID.randomUUID().toString();
            }
            MDC.put(TraceConstants.CORRELATION_KEY, correlationId);
        }
        
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(TraceConstants.CORRELATION_KEY);
        }
    }
}
