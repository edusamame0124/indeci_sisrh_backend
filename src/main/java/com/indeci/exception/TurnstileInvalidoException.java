package com.indeci.exception;

public class TurnstileInvalidoException extends RuntimeException {
    public TurnstileInvalidoException() {
        super("Captcha inválido");
    }
}