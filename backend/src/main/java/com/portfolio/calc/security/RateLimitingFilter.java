package com.portfolio.calc.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitingFilter implements Filter {

    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    
    // Limits: Allow up to 15 requests per minute per IP address
    private static final int MAX_TOKENS = 15;
    private static final long REFILL_PERIOD_MS = 60000; // 1 minute

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        if (request instanceof HttpServletRequest httpRequest && response instanceof HttpServletResponse httpResponse) {
            // Bypass health check endpoints and static docs if needed, but apply rate limits on solves/scans
            String path = httpRequest.getRequestURI();
            if (path.startsWith("/api/v1/solve") || path.startsWith("/api/v1/scan")) {
                String ip = getClientIp(httpRequest);
                TokenBucket bucket = buckets.computeIfAbsent(ip, k -> new TokenBucket(MAX_TOKENS, REFILL_PERIOD_MS));

                if (!bucket.tryConsume()) {
                    httpResponse.setStatus(429); // Too Many Requests
                    httpResponse.setContentType("application/json");
                    httpResponse.getWriter().write("{\"error\": \"Too many requests. Rate limit of 15 requests per minute exceeded.\"}");
                    return;
                }
            }
        }
        
        chain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }

    private static class TokenBucket {
        private final int maxTokens;
        private final long refillPeriodMs;
        private double tokens;
        private long lastRefillTimestamp;

        public TokenBucket(int maxTokens, long refillPeriodMs) {
            this.maxTokens = maxTokens;
            this.refillPeriodMs = refillPeriodMs;
            this.tokens = maxTokens;
            this.lastRefillTimestamp = System.currentTimeMillis();
        }

        public synchronized boolean tryConsume() {
            refill();
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long elapsedTime = now - lastRefillTimestamp;
            double tokensToAdd = ((double) elapsedTime / refillPeriodMs) * maxTokens;
            if (tokensToAdd > 0) {
                tokens = Math.min(maxTokens, tokens + tokensToAdd);
                lastRefillTimestamp = now;
            }
        }
    }
}
