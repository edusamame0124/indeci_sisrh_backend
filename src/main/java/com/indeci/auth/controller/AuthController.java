package com.indeci.auth.controller;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.indeci.auth.dto.ChangePasswordRequest;
import com.indeci.auth.dto.LoginRequest;
import com.indeci.auth.dto.LoginResponse;
import com.indeci.auth.dto.LogoutRequest;
import com.indeci.auth.dto.OtpEnrollResponseDto;
import com.indeci.auth.dto.OtpRequest;
import com.indeci.auth.dto.RefreshRequest;
import com.indeci.auth.service.AuthService;
import com.indeci.exception.SeguridadException;
import com.indeci.util.ClientInfoUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    /**
     * Spec 013 / C4 — el refresh token viaja en una cookie HttpOnly, no en el
     * cuerpo JSON. Así el JavaScript del cliente nunca puede leerlo (defensa
     * ante XSS). Ámbito acotado a `/api/auth`: solo refresh y logout la usan.
     */
    private static final String REFRESH_COOKIE = "sisrh_refresh_token";
    private static final Duration REFRESH_TTL = Duration.ofHours(24);
    private static final String COOKIE_PATH = "/api/auth";

    private final AuthService authService;
    private final ClientInfoUtil clientInfoUtil;

    @PostMapping("/login")
    public LoginResponse login(
            @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {

        String ip = clientInfoUtil.obtenerIpReal(httpRequest);
        String userAgent = clientInfoUtil.obtenerUserAgent(httpRequest);

        return authService.login(request, ip, userAgent);
    }


    @PostMapping("/cambiar-clave")
    public LoginResponse cambiarClave(
            @RequestBody ChangePasswordRequest request,
            @RequestHeader("Authorization") String authHeader
    ) {
        return authService.cambiarClave(request, authHeader);
    }


    @PostMapping("/otp/enroll")
    public OtpEnrollResponseDto enroll(@RequestHeader("Authorization") String authHeader) {
        return authService.enrollOtp(authHeader);
    }

    @PostMapping("/otp/confirm")
    public LoginResponse confirm(
            @RequestBody OtpRequest request,
            @RequestHeader("Authorization") String authHeader,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        String ip = clientInfoUtil.obtenerIpReal(httpRequest);
        String userAgent = clientInfoUtil.obtenerUserAgent(httpRequest);

        LoginResponse response = authService.confirmarOtp(request, authHeader, ip, userAgent);
        emitirCookieRefresh(response, httpRequest, httpResponse);
        return response;
    }

    @PostMapping("/refresh")
    public LoginResponse refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshCookie,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        if (refreshCookie == null || refreshCookie.isBlank()) {
            throw new SeguridadException("No hay sesión para renovar");
        }

        String ip = clientInfoUtil.obtenerIpReal(httpRequest);
        String userAgent = clientInfoUtil.obtenerUserAgent(httpRequest);

        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken(refreshCookie);

        LoginResponse response = authService.refreshToken(request, ip, userAgent);
        emitirCookieRefresh(response, httpRequest, httpResponse);
        return response;
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshCookie,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        if (refreshCookie != null && !refreshCookie.isBlank()) {
            LogoutRequest body = new LogoutRequest();
            body.setRefreshToken(refreshCookie);
            authService.logout(body);
        }
        limpiarCookieRefresh(httpRequest, httpResponse);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/test")
    public String test() {
        return "OK";
    }

    // ============================ COOKIE HttpOnly (C4) ============================

    /**
     * Mueve el refresh token de la respuesta a una cookie HttpOnly y lo borra
     * del cuerpo JSON. {@code Secure} se ata a si la petición llegó por HTTPS,
     * así funciona en dev (http) y queda seguro en producción (https).
     */
    private void emitirCookieRefresh(
            LoginResponse response, HttpServletRequest req, HttpServletResponse res) {

        String refreshToken = response.getRefreshToken();
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, refreshToken)
                .httpOnly(true)
                .secure(req.isSecure())
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(REFRESH_TTL)
                .build();
        res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        // El refresh token nunca debe llegar al JavaScript del cliente.
        response.setRefreshToken(null);
    }

    /** Borra la cookie de refresh (Max-Age 0) al cerrar sesión. */
    private void limpiarCookieRefresh(HttpServletRequest req, HttpServletResponse res) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(req.isSecure())
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(0)
                .build();
        res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
