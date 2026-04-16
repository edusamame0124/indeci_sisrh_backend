package com.indeci.exception;

import com.indeci.security.ratelimit.LoginRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final LoginRateLimiter loginRateLimiter;

    // ============================
    // NEGOCIO (400)
    // ============================
    @ExceptionHandler(NegocioException.class)
    public ResponseEntity<?> handleNegocio(NegocioException ex,
                                           HttpServletRequest request) {

        String ip = request.getRemoteAddr();
        int intentos = loginRateLimiter.obtenerIntentos(ip);

        boolean requiereCaptcha = intentos >= 3;

        Map<String, Object> response = new HashMap<>();
        response.put("status", 400);
        response.put("mensaje", ex.getMessage());
        response.put("requiereCaptcha", requiereCaptcha);

        return ResponseEntity.badRequest().body(response);
    }

    // ============================
    // SEGURIDAD (403)
    // ============================
    @ExceptionHandler(SeguridadException.class)
    public ResponseEntity<?> handleSeguridad(SeguridadException ex,
                                             HttpServletRequest request) {

        Map<String, Object> response = new HashMap<>();
        response.put("status", 403);
        response.put("mensaje", ex.getMessage());
        response.put("requiereCaptcha", false);

        return ResponseEntity.status(403).body(response);
    }

    // ============================
    // CAPTCHA INVALIDO
    // ============================
    @ExceptionHandler(TurnstileInvalidoException.class)
    public ResponseEntity<?> handleTurnstile(TurnstileInvalidoException ex,
                                             HttpServletRequest request) {

        String ip = request.getRemoteAddr();
        int intentos = loginRateLimiter.obtenerIntentos(ip);

        boolean requiereCaptcha = intentos >= 3;

        Map<String, Object> response = new HashMap<>();
        response.put("status", 400);
        response.put("mensaje", "Captcha inválido");
        response.put("requiereCaptcha", requiereCaptcha);

        return ResponseEntity.badRequest().body(response);
    }

    // ============================
    // ERROR GENERAL
    // ============================
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneral(Exception ex) {

        Map<String, Object> response = new HashMap<>();
        response.put("status", 500);
        response.put("mensaje", "Error interno");
        response.put("requiereCaptcha", false);

        return ResponseEntity.status(500).body(response);
    }
}