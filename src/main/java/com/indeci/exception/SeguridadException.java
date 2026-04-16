package com.indeci.exception;

public class SeguridadException extends RuntimeException {
    public SeguridadException(String mensaje) {
        super(mensaje);
    }
}