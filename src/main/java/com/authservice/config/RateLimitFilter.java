package com.authservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP rate limiting for auth endpoints using Bucket4j.
 * Login and refresh are the most sensitive endpoints for brute-force attacks.
 */
@Component
@Order(2)
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitFilter extends OncePerRequestFilter {

    private final ConcurrentHashMap<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> refreshBuckets = new ConcurrentHashMap<>();
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimitProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if (!"POST".equalsIgnoreCase(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(request);
        Bucket bucket = null;

        if (path.equals("/api/v1/auth/login") || path.equals("/api/v1/auth/register")) {
            bucket = loginBuckets.computeIfAbsent(clientIp, k -> createBucket(properties.loginRequestsPerMinute()));
        } else if (path.equals("/api/v1/auth/refresh")) {
            bucket = refreshBuckets.computeIfAbsent(clientIp, k -> createBucket(properties.refreshRequestsPerMinute()));
        }

        if (bucket != null && !bucket.tryConsume(1)) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", "60");
            objectMapper.writeValue(response.getOutputStream(), Map.of(
                    "type", "about:blank",
                    "title", "Too Many Requests",
                    "status", 429,
                    "detail", "Rate limit exceeded. Please try again later.",
                    "instance", path,
                    "timestamp", Instant.now().toString()
            ));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private Bucket createBucket(int tokensPerMinute) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(tokensPerMinute)
                        .refillGreedy(tokensPerMinute, Duration.ofMinutes(1))
                        .build())
                .build();
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
