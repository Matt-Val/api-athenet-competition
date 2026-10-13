package com.athenet.competition.exception;

/* 
    MS-Instituciones (u otro servicio) no respondió o respondió con un error
    inesperado. Solo traduce un 503 con un mensaje claro.
*/
public class UpstreamServiceException extends RuntimeException {
    public UpstreamServiceException(String message) {
        super(message);
    }

    public UpstreamServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
