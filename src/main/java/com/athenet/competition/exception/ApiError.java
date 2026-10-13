package com.athenet.competition.exception;

import java.time.LocalDateTime;

// Estructura del JSON cuando algo falla
public record ApiError(
        LocalDateTime timestamp,
        int status,
        String error,
        String message
) {
}
