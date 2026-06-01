package com.indeci.auth.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.indeci.audit.annotation.Auditable;
import com.indeci.auth.dto.ChangePasswordRequest;
import com.indeci.auth.dto.LoginRequest;
import com.indeci.auth.dto.LoginResponse;
import com.indeci.auth.dto.LogoutRequest;
import com.indeci.auth.dto.OtpRequest;
import com.indeci.auth.dto.RefreshRequest;
import com.indeci.auth.entity.AuthRefreshToken;
import com.indeci.auth.repository.AuthRefreshTokenRepository;
import com.indeci.auth.dto.OtpEnrollResponseDto;
import com.indeci.exception.NegocioException;
import com.indeci.exception.RateLimitExceededException;
import com.indeci.exception.SeguridadException;
import com.indeci.security.captcha.TurnstileService;
import com.indeci.security.jwt.JwtProvider;
import com.indeci.security.otp.OtpService;
import com.indeci.security.ratelimit.LoginRateLimiter;
import com.indeci.sistema.service.UsuarioSistemaService;
import com.indeci.user.entity.*;
import com.indeci.user.repository.*;
import com.indeci.user.repository.UsuarioPermisoRepository;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UsuarioRolRepository usuarioRolRepository;
    private final RolRepository rolRepository;
    private final RolPermisoRepository rolPermisoRepository;
    private final PermisoRepository permisoRepository;
    private final UsuarioPermisoDenyRepository usuarioPermisoDenyRepository;
    private final UsuarioPermisoRepository usuarioPermisoRepository;
    private final JwtProvider jwtProvider;
    private final PasswordEncoder passwordEncoder;
    private final TurnstileService turnstileService;
    private final LoginRateLimiter loginRateLimiter;
    private final OtpService otpService;
    private final AuthRefreshTokenRepository authRefreshTokenRepository;
    private final UsuarioSistemaService usuarioSistemaService;

    // 🔥 TEMPORAL EN MEMORIA (como SIGCO)
    private final Map<String, String> secretTemporal = new ConcurrentHashMap<>();

    // ============================
    // LOGIN
    // ============================
    @Auditable(accion = "LOGIN")
    public LoginResponse login(LoginRequest request, String ip, String userAgent) {

        int intentos = loginRateLimiter.obtenerIntentos(ip);

        if (intentos >= 3) {
            turnstileService.validarToken(request.getTurnstileToken());
        }

        if (!loginRateLimiter.tryConsume(ip)) {
            throw new RateLimitExceededException("Demasiados intentos, intenta luego");
        }

        String username = request.getUsername() != null
                ? request.getUsername().trim().toLowerCase(Locale.ROOT)
                : "";
        if (username.isEmpty()) {
            throw new NegocioException("Credenciales inválidas");
        }

        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new NegocioException("Credenciales inválidas"));

        String rawPassword = request.getPassword() != null ? request.getPassword() : "";
        if (!verificarClaveUsuario(user, rawPassword)) {
            throw new NegocioException("Credenciales inválidas");
        }

        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new SeguridadException("Usuario inactivo");
        }

        List<String> roles = obtenerRoles(user);
        List<String> permisos = obtenerPermisos(user);

        loginRateLimiter.reset(ip);

        LoginResponse response = new LoginResponse();
        response.setNewPass(user.getNewClave());

        // ============================
        // CAMBIO DE CLAVE
        // ============================
        if ("S".equalsIgnoreCase(user.getNewClave())) {

            response.setToken(jwtProvider.generarTokenCambioClave(user));
            response.setNewPass("S");
            response.setRoles(roles);
            response.setPermisos(permisos);
            response.setRequiereOtp(false);
            response.setRequiereEnroll(false);
            

            return response;
        }

        // ============================
        // OTP FLOW
        // ============================
     // ============================
     // OTP FLOW (CORREGIDO)
     // ============================

     // 🔹 SI OTP ESTÁ HABILITADO → VALIDAR
        if ("S".equalsIgnoreCase(user.getOtpHabilitado()) &&
        	    user.getOtpSecret() != null &&
        	    !user.getOtpSecret().isBlank()) {

         response.setToken(jwtProvider.generarTokenTemporal(user));
         response.setRequiereOtp(true);
         response.setRequiereEnroll(false);

         return response;
     }

     // 🔹 SI NO ESTÁ HABILITADO → ENROLL
     response.setToken(jwtProvider.generarTokenTemporal(user));
     response.setRequiereEnroll(true);
     response.setRequiereOtp(false);

     return response;
    }

    // ============================
    // ENROLL OTP (QR)
    // ============================
    public OtpEnrollResponseDto enrollOtp(String authHeader) {

        String token = authHeader.replace("Bearer ", "");
        String username = jwtProvider.getSubject(token);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NegocioException("Credenciales inválidas"));
        
        if ("S".equalsIgnoreCase(user.getOtpHabilitado()) &&
        	    user.getOtpSecret() != null &&
        	    !user.getOtpSecret().isBlank()) {

        	    throw new NegocioException("OTP ya está configurado");
        	}

        var key = otpService.generarSecret();
        String secret = key.getKey();

        String otpUrl = otpService.construirOtpAuthUrl(
                "INDECI",
                user.getUsername(),
                secret
        );

        // 🔥 GUARDAR EN MEMORIA
        secretTemporal.put(username, secret);

        String qr = otpService.generarUrlQr(otpUrl);

        return new OtpEnrollResponseDto(qr);
    }

 // ============================
 // CONFIRMAR OTP
 // ============================
    @Auditable(accion = "OTP_CONFIRM")
    public LoginResponse confirmarOtp(OtpRequest request, String authHeader, String ip, String userAgent) {

     String token = authHeader.replace("Bearer ", "");
     String username = jwtProvider.getSubject(token);

     User user = userRepository.findByUsername(username)
             .orElseThrow(() -> new NegocioException("Credenciales inválidas"));

     // 🔥 1. OBTENER SECRET (MEMORIA O BD)
     String secret = secretTemporal.get(username);

     // 🔥 SI NO HAY TEMPORAL → USAR BD
     if (secret == null || secret.isBlank()) {

         if (user.getOtpSecret() == null || user.getOtpSecret().isBlank()) {
             throw new NegocioException("OTP no generado");
         }

         secret = user.getOtpSecret();
     }

     // 🔥 2. VALIDAR CÓDIGO
     if (!otpService.validarCodigo(secret, request.getCodigo())) {
         throw new NegocioException("Código OTP inválido");
     }

     // 🔥 3. SI ES ENROLL → GUARDAR DEFINITIVO
     if (secretTemporal.containsKey(username)) {

         user.setOtpSecret(secret);
         user.setOtpHabilitado("S");
         userRepository.save(user);

         // 🔥 LIMPIAR MEMORIA
         secretTemporal.remove(username);
     }

     // 🔥 4. GENERAR TOKEN FINAL — Fase 3 SSO: claims "sistemas" y "areas".
     List<String> roles = obtenerRoles(user);
     List<String> permisos = obtenerPermisos(user);
     Map<String, List<String>> sistemas = usuarioSistemaService.obtenerSistemasDe(user, roles);
     Map<String, String> areas = usuarioSistemaService.obtenerAreasDe(user);

     String accessToken = jwtProvider.generarTokenDefinitivo(user, roles, permisos, sistemas, areas);
     String refreshToken = jwtProvider.generarRefreshToken(user);

     // 🔥 GUARDAR EN BD
     AuthRefreshToken entity = new AuthRefreshToken();
     entity.setUsuario(user.getUsername());
     entity.setToken(refreshToken);
     entity.setActivo("S");
     entity.setFechaCreacion(LocalDateTime.now());
     entity.setFechaExpiracion(LocalDateTime.now().plusHours(24));
     entity.setIp(ip);
     entity.setUserAgent(userAgent);

     authRefreshTokenRepository.save(entity);

     // 🔥 5. RESPUESTA
     LoginResponse response = new LoginResponse();
     response.setToken(accessToken);
     response.setRefreshToken(refreshToken);
     response.setRoles(roles);
     response.setPermisos(permisos);
     response.setEmpleadoId(user.getEmpleadoId());
     response.setNewPass("N");
     response.setRequiereOtp(false);
     response.setRequiereEnroll(false);

     return response;
 }

    // ============================
    // VALIDAR OTP (LOGIN NORMAL)
    // ============================
    public LoginResponse validarOtp(OtpRequest request, String authHeader, String ip, String userAgent) {

        String token = authHeader.replace("Bearer ", "");
        String username = jwtProvider.getSubject(token);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NegocioException("Credenciales inválidas"));

        if (!otpService.validarCodigo(user.getOtpSecret(), request.getCodigo())) {
            throw new NegocioException("Código OTP inválido");
        }

        List<String> roles = obtenerRoles(user);
        List<String> permisos = obtenerPermisos(user);
        Map<String, List<String>> sistemas = usuarioSistemaService.obtenerSistemasDe(user, roles);
        Map<String, String> areas = usuarioSistemaService.obtenerAreasDe(user);

        String accessToken = jwtProvider.generarTokenDefinitivo(user, roles, permisos, sistemas, areas);
        String refreshToken = jwtProvider.generarRefreshToken(user);

        // 🔥 GUARDAR EN BD
        AuthRefreshToken entity = new AuthRefreshToken();
        entity.setUsuario(user.getUsername());
        entity.setToken(refreshToken);
        entity.setActivo("S");
        entity.setFechaCreacion(LocalDateTime.now());
        entity.setFechaExpiracion(LocalDateTime.now().plusHours(24));
        entity.setIp(ip);
        entity.setUserAgent(userAgent);

        authRefreshTokenRepository.save(entity);

        LoginResponse response = new LoginResponse();
        response.setToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setRoles(roles);
        response.setPermisos(permisos);
        response.setEmpleadoId(user.getEmpleadoId());
        response.setNewPass("N");
        response.setRequiereOtp(false);
        response.setRequiereEnroll(false);


        return response;
    }

    // ============================
    // CAMBIAR CLAVE
    // ============================
    @Auditable(accion = "CAMBIAR_CLAVE")
    public LoginResponse cambiarClave(ChangePasswordRequest request, String authHeader) {

        String token = authHeader.replace("Bearer ", "");
        String username = jwtProvider.getSubject(token);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NegocioException("Credenciales inválidas"));

        if (!"S".equalsIgnoreCase(user.getNewClave())) {
            throw new NegocioException("Usuario ya cambió su contraseña");
        }

        aplicarClaveCodificada(user, request.getNuevaClave());
        user.setNewClave("N");

        userRepository.save(user);

     

        String nuevoToken = jwtProvider.generarTokenTemporal(user);

        LoginResponse response = new LoginResponse();
        response.setToken(nuevoToken);
        response.setRoles(null);
        response.setPermisos(null);
        response.setRequiereOtp(false);
        response.setRequiereEnroll(false);
        response.setNewPass("N");

        return response;
    }

    // ============================
    // HELPERS
    // ============================
    /**
     * Valida contra PASSWORD y, si aplica, PASSWORD_HASH (cuentas legacy o migradas).
     */
    private boolean verificarClaveUsuario(User user, String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            return false;
        }
        if (coincideHash(user.getPassword(), rawPassword)) {
            return true;
        }
        return coincideHash(user.getPasswordHash(), rawPassword);
    }

    private boolean coincideHash(String encoded, String rawPassword) {
        if (encoded == null || encoded.isBlank()) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, encoded);
    }

    private void aplicarClaveCodificada(User user, String rawPassword) {
        String encoded = passwordEncoder.encode(rawPassword);
        user.setPassword(encoded);
        user.setPasswordHash(encoded);
    }

    private List<String> obtenerRoles(User user) {
        List<UsuarioRol> usuarioRoles = usuarioRolRepository.findByUserId(user.getId());
        Set<String> roles = new LinkedHashSet<>();

        for (UsuarioRol ur : usuarioRoles) {
            if (!esRolSisrh(ur)) {
                continue;
            }
            Rol rol = rolRepository.findById(ur.getRolId()).orElse(null);
            if (rol != null) roles.add(rol.getCodigo());
        }

        return new ArrayList<>(roles);
    }

    private List<String> obtenerPermisos(User user) {
        List<UsuarioRol> usuarioRoles = usuarioRolRepository.findByUserId(user.getId());
        Set<Long> permisosDenegados = usuarioPermisoDenyRepository.findByUserId(user.getId())
                .stream()
                .map(UsuarioPermisoDeny::getPermisoId)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> permisos = new LinkedHashSet<>();

        // Permisos heredados del rol (menos los denegados)
        for (UsuarioRol ur : usuarioRoles) {
            if (!esRolSisrh(ur)) {
                continue;
            }
            for (RolPermiso rp : rolPermisoRepository.findByRolId(ur.getRolId())) {
                if (permisosDenegados.contains(rp.getPermisoId())) {
                    continue;
                }
                Permiso p = permisoRepository.findById(rp.getPermisoId()).orElse(null);
                if (p != null) permisos.add(p.getCodigo());
            }
        }

        // Permisos otorgados directamente (SS_USUARIO_PERMISO), también respetan las denegaciones
        for (UsuarioPermiso up : usuarioPermisoRepository.findByUserId(user.getId())) {
            if (permisosDenegados.contains(up.getPermisoId())) {
                continue;
            }
            Permiso p = permisoRepository.findById(up.getPermisoId()).orElse(null);
            if (p != null) permisos.add(p.getCodigo());
        }

        return new ArrayList<>(permisos);
    }

    private boolean esRolSisrh(UsuarioRol ur) {
        return ur.getSistema() == null || "SISRH".equalsIgnoreCase(ur.getSistema());
    }
    
    @Auditable(accion = "REFRESH_TOKEN")
    @Transactional
    public LoginResponse refreshToken(RefreshRequest request, String ip, String userAgent) {

        String refreshToken = request.getRefreshToken();

        // 🔥 1. VALIDAR JWT (firma + expiración + claims)
        Claims claims = jwtProvider.obtenerClaims(refreshToken);

        if (!"refresh".equals(String.valueOf(claims.get("type")))) {
            throw new SeguridadException("Token inválido");
        }

        String username = claims.getSubject();

        // 🔥 2. VALIDAR EN BD (existencia + activo)
        AuthRefreshToken tokenEntity = authRefreshTokenRepository
                .findByTokenAndActivo(refreshToken, "S")
                .orElseThrow(() -> new SeguridadException("Refresh inválido"));

        // 🔥 3. VALIDAR EXPIRACIÓN EN BD
        if (tokenEntity.getFechaExpiracion().isBefore(LocalDateTime.now())) {
            throw new SeguridadException("Refresh expirado");
        }

        // 🔥 4. OBTENER USUARIO
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NegocioException("Credenciales inválidas"));

        // 🔥 5. GENERAR NUEVO ACCESS — Fase 3 SSO: re-resuelve sistemas y areas.
        List<String> roles = obtenerRoles(user);
        List<String> permisos = obtenerPermisos(user);
        Map<String, List<String>> sistemas = usuarioSistemaService.obtenerSistemasDe(user, roles);
        Map<String, String> areas = usuarioSistemaService.obtenerAreasDe(user);

        String nuevoAccess = jwtProvider.generarTokenDefinitivo(user, roles, permisos, sistemas, areas);

        // 🔥 6. ROTACIÓN DEL TOKEN (invalidar anterior)
        tokenEntity.setActivo("N");
        tokenEntity.setFechaRevocacion(LocalDateTime.now());
        tokenEntity.setMotivoRevocacion("ROTACION");
        authRefreshTokenRepository.save(tokenEntity);

        // 🔥 7. GENERAR NUEVO REFRESH
        String nuevoRefresh = jwtProvider.generarRefreshToken(user);

        AuthRefreshToken nuevo = new AuthRefreshToken();
        nuevo.setUsuario(username);
        nuevo.setToken(nuevoRefresh);
        nuevo.setActivo("S");
        nuevo.setFechaCreacion(LocalDateTime.now());
        nuevo.setFechaExpiracion(LocalDateTime.now().plusHours(24));
        nuevo.setIp(ip);
        nuevo.setUserAgent(userAgent);

        authRefreshTokenRepository.save(nuevo);

        // 🔥 8. RESPUESTA FINAL
        LoginResponse response = new LoginResponse();
        response.setToken(nuevoAccess);
        response.setRefreshToken(nuevoRefresh);
        response.setRoles(roles);
        response.setPermisos(permisos);
        response.setEmpleadoId(user.getEmpleadoId());

        return response;
    }

    @Auditable(accion = "LOGOUT")
    @Transactional
    public void logout(LogoutRequest request) {
        authRefreshTokenRepository.findByToken(request.getRefreshToken()).ifPresent(entity -> {
            if (!"S".equalsIgnoreCase(entity.getActivo())) {
                return;
            }
            entity.setActivo("N");
            entity.setFechaRevocacion(LocalDateTime.now());
            entity.setMotivoRevocacion("LOGOUT");
            authRefreshTokenRepository.save(entity);
        });
    }

    // ============================
    // VALIDAR TOKEN (Fase 3 SSO)
    // ============================
    /**
     * Endpoint que consumen SISCONV (8081) y GDR (8082) para verificar el JWT
     * emitido por el SISRH. Devuelve los claims útiles ya parseados.
     *
     * Errores:
     *   - Header ausente / mal formado → SeguridadException → 401.
     *   - Firma inválida / token expirado → JwtException → 401 (via handler global).
     *   - Token con otpValidado=false o newPassOk=false → SeguridadException → 401.
     *     Los sistemas externos no deben aceptar tokens "a medias".
     */
    @SuppressWarnings("unchecked")
    public com.indeci.auth.dto.ValidateTokenResponse validarToken(String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new SeguridadException("Token ausente");
        }
        String token = authHeader.substring(7);
        Claims claims = jwtProvider.obtenerClaims(token);

        Boolean otpValidado = claims.get("otpValidado", Boolean.class);
        Boolean newPassOk = claims.get("newPassOk", Boolean.class);
        if (!Boolean.TRUE.equals(otpValidado) || !Boolean.TRUE.equals(newPassOk)) {
            throw new SeguridadException("Token incompleto: requiere OTP o cambio de clave");
        }

        List<String> roles = (List<String>) claims.getOrDefault("roles", List.of());
        List<String> permisos = (List<String>) claims.getOrDefault("permisos", List.of());
        Map<String, List<String>> sistemas =
                (Map<String, List<String>>) claims.getOrDefault("sistemas", Map.of());
        Long empleadoId = claims.get("empleadoId", Long.class);
        Long exp = claims.getExpiration() != null
                ? claims.getExpiration().getTime() / 1000
                : null;

        return new com.indeci.auth.dto.ValidateTokenResponse(
                claims.getSubject(),
                roles,
                permisos,
                sistemas,
                empleadoId,
                exp
        );
    }
}
