package com.authservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        int loginRequestsPerMinute,
        int refreshRequestsPerMinute
) {}
