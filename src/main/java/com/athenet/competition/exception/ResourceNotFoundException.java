package com.athenet.competition.exception;

// Error al no encontrar un recurso buscando por ID
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
