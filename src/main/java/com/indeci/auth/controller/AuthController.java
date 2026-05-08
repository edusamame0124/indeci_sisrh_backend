package com.indeci.auth.controller;

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
import com.indeci.util.ClientInfoUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

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
            HttpServletRequest httpRequest
    ) {
    	 String ip = clientInfoUtil.obtenerIpReal(httpRequest);
    	    String userAgent = clientInfoUtil.obtenerUserAgent(httpRequest);

    	    return authService.confirmarOtp(request, authHeader, ip, userAgent);
    }
    
    @PostMapping("/refresh")
    public LoginResponse refresh(
            @RequestBody RefreshRequest request,
            HttpServletRequest httpRequest
    ) {

        String ip = clientInfoUtil.obtenerIpReal(httpRequest);
        String userAgent = clientInfoUtil.obtenerUserAgent(httpRequest);

        return authService.refreshToken(request, ip, userAgent);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest body) {
        authService.logout(body);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/test")
    public String test() {
        return "OK";
    }
}