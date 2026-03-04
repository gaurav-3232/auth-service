package com.authservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.admin")
public record AdminBootstrapProperties(
        String bootstrapEmail,
        String bootstrapPassword
) {}
