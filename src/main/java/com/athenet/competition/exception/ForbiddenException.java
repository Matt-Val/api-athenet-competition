package com.athenet.competition.exception;

// Error cuando el usuario tiene el rol, pero no es dueño del recurso (p. ej. otro delegado)
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
