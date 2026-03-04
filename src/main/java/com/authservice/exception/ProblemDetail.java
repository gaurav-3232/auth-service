package com.authservice.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProblemDetail(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        Instant timestamp,
        Map<String, Object> errors
) {
    public ProblemDetail(String type, String title, int status, String detail, String instance) {
        this(type, title, status, detail, instance, Instant.now(), null);
    }

    public ProblemDetail(String type, String title, int status, String detail, String instance, Map<String, Object> errors) {
        this(type, title, status, detail, instance, Instant.now(), errors);
    }
}
