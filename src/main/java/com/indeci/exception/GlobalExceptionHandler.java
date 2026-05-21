package com.indeci.exception;

import com.indeci.security.ratelimit.LoginRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

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
    // RATE LIMIT (429)
    // ============================
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<?> handleRateLimit(RateLimitExceededException ex,
                                             HttpServletRequest request) {

        String ip = request.getRemoteAddr();
        int intentos = loginRateLimiter.obtenerIntentos(ip);
        boolean requiereCaptcha = intentos >= 3;

        Map<String, Object> response = new HashMap<>();
        response.put("status", 429);
        response.put("mensaje", ex.getMessage());
        response.put("requiereCaptcha", requiereCaptcha);

        return ResponseEntity.status(429).body(response);
    }

    // ============================
    // CONCEPTO NO ASIGNABLE MANUALMENTE (422) — Spec 013 / C1
    // ============================
    @ExceptionHandler(ConceptoNoAsignableManualmenteException.class)
    public ResponseEntity<?> handleConceptoNoAsignable(
            ConceptoNoAsignableManualmenteException ex) {

        Map<String, Object> response = new HashMap<>();
        response.put("status", 422);
        response.put("mensaje", ex.getMessage());
        response.put("requiereCaptcha", false);

        return ResponseEntity.status(422).body(response);
    }

    // ============================
    // CONCEPTO YA ASIGNADO / DUPLICADO (409) — Spec 013 / C1
    // ============================
    @ExceptionHandler(ConceptoYaAsignadoException.class)
    public ResponseEntity<?> handleConceptoYaAsignado(
            ConceptoYaAsignadoException ex) {

        Map<String, Object> response = new HashMap<>();
        response.put("status", 409);
        response.put("mensaje", ex.getMessage());
        response.put("requiereCaptcha", false);

        return ResponseEntity.status(409).body(response);
    }

    // ============================
    // VALIDACIÓN @VALID (400)
    // ============================
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidacion(MethodArgumentNotValidException ex,
                                              HttpServletRequest request) {

        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "")
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining(" "));
        if (msg.isBlank()) {
            msg = "Datos de entrada inválidos";
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", 400);
        response.put("mensaje", msg);
        response.put("requiereCaptcha", false);

        return ResponseEntity.badRequest().body(response);
    }

    // ============================
    // ACCESO DENEGADO @PreAuthorize (403)
    // ============================
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> handleAccessDenied(AccessDeniedException ex,
                                                HttpServletRequest request) {

        Map<String, Object> response = new HashMap<>();
        response.put("status", 403);
        response.put("mensaje", "No tiene permisos para esta operación");
        response.put("requiereCaptcha", false);

        return ResponseEntity.status(403).body(response);
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