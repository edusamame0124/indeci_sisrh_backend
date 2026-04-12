package exception;

public class TurnstileInvalidoException extends RuntimeException {
    public TurnstileInvalidoException() {
        super("Validación Turnstile fallida");
    }
}