package com.athenet.competition.exception;

// Error para violaciones de reglas de negocio (duplicados, transiciones de estado inválidas, etc.)
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
